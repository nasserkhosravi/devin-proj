package com.khosravi.devin.write

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A log captured on the caller's thread, waiting to be sent to the presenter. */
internal class PendingLog(
    val tag: String?,
    val value: String,
    val typeId: String?,
    val meta: String?,
    val content: ByteArray?,
    val metaIndex: Pair<String, String>?,
    val date: Date,
)

/**
 * Sends logs to the presenter in batches, off the caller's thread.
 *
 * [write] only puts the log in a bounded [Channel]. One consumer coroutine takes the first log of a burst, waits
 * [FLUSH_DELAY_MS] for the burst to gather, then sends everything waiting in chunks of [MAX_BATCH_ITEMS]. A single
 * consumer keeps call order, and while idle it is suspended, holding no thread. When [MAX_PENDING_LOGS] are
 * waiting, new logs are dropped and a [droppedLogsNotice] is sent with the next batch.
 */
internal class AsyncLogWriter(
    private val sendBatch: (List<PendingLog>) -> Unit,
    private val droppedLogsNotice: (droppedCount: Int) -> PendingLog,
) {

    private val channel = Channel<PendingLog>(MAX_PENDING_LOGS)
    private val droppedCount = AtomicInteger()

    /** Logs accepted by [write] and logs handled by the consumer, so [awaitPending] knows when it has caught up. */
    private val acceptedCount = AtomicLong()
    private val handledCount = MutableStateFlow(0L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch { consume() }
    }

    fun write(log: PendingLog) {
        if (channel.trySend(log).isSuccess) {
            acceptedCount.incrementAndGet()
        } else {
            droppedCount.incrementAndGet()
        }
    }

    /**
     * Blocks up to [timeoutMs] until logs written before this call are sent. Used before the process dies.
     */
    fun awaitPending(timeoutMs: Long) {
        val target = acceptedCount.get()
        runBlocking {
            withTimeoutOrNull(timeoutMs) { handledCount.first { it >= target } }
        }
    }

    private suspend fun consume() {
        for (first in channel) {
            delay(FLUSH_DELAY_MS)
            var batch = ArrayList<PendingLog>(MAX_BATCH_ITEMS)
            batch.add(first)
            while (true) {
                batch.add(channel.tryReceive().getOrNull() ?: break)
                if (batch.size == MAX_BATCH_ITEMS) {
                    send(batch, logCount = batch.size)
                    batch = ArrayList(MAX_BATCH_ITEMS)
                }
            }
            val logCount = batch.size
            val dropped = droppedCount.getAndSet(0)
            if (dropped > 0) {
                batch.add(droppedLogsNotice(dropped))
            }
            if (batch.isNotEmpty()) {
                send(batch, logCount)
            }
        }
    }

    /** [logCount] excludes the dropped-logs notice, which [write] never accepted. */
    private fun send(batch: List<PendingLog>, logCount: Int) {
        try {
            sendBatch(batch)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${batch.size} logs", e)
        }
        handledCount.update { it + logCount }
    }

    companion object {
        private const val TAG = "DevinAsyncLogWriter"
        private const val MAX_PENDING_LOGS = 2000
        private const val MAX_BATCH_ITEMS = 50
        private const val FLUSH_DELAY_MS = 150L
    }
}
