package com.khosravi.devin.present.testing

import android.content.Context
import com.google.gson.JsonObject
import com.khosravi.devin.read.DevinUriHelper
import com.khosravi.devin.write.DevinContentProvider
import org.json.JSONObject
import org.robolectric.Robolectric
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Registers the real [DevinContentProvider] and writes into it the way client apps do. */
object ProviderFixture {

    private const val AUTHORITY = "com.khosravi.devin.provider"
    private val worker = Executors.newSingleThreadExecutor()

    /**
     * Runs [block] off the test (main) thread. Room rejects main-thread access, and in the app
     * provider calls arrive on binder threads anyway.
     */
    fun <T> onWorker(block: () -> T): T = worker.submit<T> { block() }.get(30, TimeUnit.SECONDS)

    fun register() {
        Robolectric.setupContentProvider(DevinContentProvider::class.java, AUTHORITY)
    }

    fun putClient(context: Context, clientId: String, presenterConfig: JSONObject? = null) = onWorker {
        context.contentResolver.insert(
            DevinUriHelper.getClientListUri(),
            DevinContentProvider.contentValuePutClient(clientId, presenterConfig),
        )
    }

    fun putLog(
        context: Context,
        clientId: String,
        tag: String,
        value: String,
        date: Long,
        typeId: String? = null,
        meta: JsonObject? = null,
        metaIndex: Pair<String, String>? = null,
    ): Long = onWorker {
        val uri = context.contentResolver.insert(
            DevinUriHelper.getLogListUri(),
            DevinContentProvider.contentValueLog(
                appId = clientId,
                tag = tag,
                value = value,
                typeId = typeId,
                meta = meta?.toString(),
                content = null,
                metaIndexPair = metaIndex,
                date = Date(date),
            ),
        )!!
        uri.lastPathSegment!!.toLong()
    }
}
