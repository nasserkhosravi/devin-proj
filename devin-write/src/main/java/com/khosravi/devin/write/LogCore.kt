package com.khosravi.devin.write

import android.content.ContentProviderClient
import android.content.Context
import android.net.Uri
import android.os.DeadObjectException
import android.os.RemoteException
import android.os.TransactionTooLargeException
import android.util.Log
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.api.DevinLogCore
import java.util.Date

internal class LogCore(
    private val appContext: Context,
    private val isEnable: Boolean,
) : DevinLogCore {

    private val appId = appContext.packageName

    private val asyncLogWriter = AsyncLogWriter(::bulkInsertLogs, ::droppedLogsNotice)

    private val providerClientLock = Any()

    /**
     * Cached client of the presenter's provider, so each write skips the `getContentProvider` IPC to system_server.
     * It is unstable on purpose: if the presenter process dies, this process is not killed with it.
     */
    @Volatile
    private var providerClient: ContentProviderClient? = null

    /**
     * Blocks the caller until the presenter stores the log. Only call it off the main thread, or when the
     * returned [Uri] is needed. Otherwise use [insertLogAsync].
     */
    override fun insertLog(
        tag: String?,
        value: String,
        typeId: String?,
        meta: String?,
        content: ByteArray?,
        metaIndex: Pair<String, String>?
    ): Uri? {
        if (isEnable.not()) return null
        val uri = DevinUriHelper.getLogListUri()
        val values = PendingLog(tag, value, typeId, meta, content, metaIndex, Date()).toContentValues()
        return withProviderClient(uri, onFailure = null) { it.insert(uri, values) }
    }

    /**
     * Same as [insertLog], but only queues the log and returns, so it is safe on the main thread. Queued logs are
     * sent in batches by [AsyncLogWriter].
     */
    fun insertLogAsync(
        tag: String?,
        value: String,
        typeId: String?,
        meta: String? = null,
        content: ByteArray? = null,
        metaIndex: Pair<String, String>? = null,
    ) {
        if (isEnable.not()) return
        // The date is taken now, on the caller's thread: the batch is sent later.
        asyncLogWriter.write(PendingLog(tag, value, typeId, meta, content, metaIndex, Date()))
    }

    /**
     * Waits up to [timeoutMs] for logs queued by [insertLogAsync] to be sent. Used before the process dies.
     */
    fun awaitPendingWrites(timeoutMs: Long) {
        asyncLogWriter.awaitPending(timeoutMs)
    }

    private fun bulkInsertLogs(logs: List<PendingLog>) {
        val uri = DevinUriHelper.getLogListUri()
        val values = Array(logs.size) { logs[it].toContentValues() }
        withProviderClient(uri, onFailure = 0) { client ->
            try {
                client.bulkInsert(uri, values)
            } catch (e: TransactionTooLargeException) {
                // Only when the batch has large payloads: send them one by one instead of losing the batch.
                values.count { client.insert(uri, it) != null }
            }
        }
    }

    private fun droppedLogsNotice(droppedCount: Int): PendingLog {
        val meta = LoggerImpl.createMetaForComponentLogs(VALUE_DROPPED_LOGS_META_TYPE, Log.WARN, null, null)
        return PendingLog(
            TAG_DROPPED_LOGS, "Devin dropped $droppedCount logs because its queue was full", null,
            meta.toString(), null, null, Date()
        )
    }

    private fun PendingLog.toContentValues() = DevinContentProvider.contentValueLog(
        appId, if (tag.isNullOrEmpty()) LoggerImpl.LOG_TAG_UNTAG else tag, value, typeId, meta, content, metaIndex, date
    )

    override fun updateLog(itemId: Uri, tag: String?, value: String, typeId: String, meta: String?, content: ByteArray?): Int {
        if (isEnable.not()) return DevinLogCore.FLAG_OPERATION_FAILED

        val fTag = if (tag.isNullOrEmpty()) LoggerImpl.LOG_TAG_UNTAG else tag
        val values = DevinContentProvider.contentValueLog(appId, fTag, value, typeId, meta, content, null)
        return withProviderClient(itemId, onFailure = 0) { it.update(itemId, values, null, null) }
    }

    override fun isEnable(): Boolean = this.isEnable

    /**
     * Runs [action] on the cached provider client. If the presenter process died, the client is recreated and
     * [action] is retried once. Like [android.content.ContentResolver], a remote failure returns [onFailure].
     */
    private fun <T> withProviderClient(uri: Uri, onFailure: T, action: (ContentProviderClient) -> T): T {
        val client = obtainProviderClient(uri)
        return try {
            action(client)
        } catch (e: DeadObjectException) {
            discardProviderClient(client)
            try {
                action(obtainProviderClient(uri))
            } catch (e: RemoteException) {
                Log.e(TAG, "Presenter provider is not reachable", e)
                onFailure
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Presenter provider call failed", e)
            onFailure
        }
    }

    private fun obtainProviderClient(uri: Uri): ContentProviderClient {
        providerClient?.let { return it }
        synchronized(providerClientLock) {
            providerClient?.let { return it }
            // Same failure as ContentResolver.insert/update when no presenter provider is installed.
            val client = appContext.contentResolver.acquireUnstableContentProviderClient(uri)
                ?: throw IllegalArgumentException("Unknown URL $uri")
            providerClient = client
            return client
        }
    }

    private fun discardProviderClient(client: ContentProviderClient) {
        synchronized(providerClientLock) {
            if (providerClient === client) {
                providerClient = null
                client.close()
            }
        }
    }

    companion object {
        private const val TAG = "DevinLogCore"
        private const val TAG_DROPPED_LOGS = "devin_dropped_logs"
        private const val VALUE_DROPPED_LOGS_META_TYPE = "dropped_logs"
    }
}
