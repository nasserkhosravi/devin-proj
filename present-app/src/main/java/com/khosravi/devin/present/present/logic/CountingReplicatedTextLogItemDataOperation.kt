package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItemData
import java.lang.IllegalStateException

class CountingReplicatedTextLogItemDataOperation(private val logs: List<LogItemData>) {

    fun get(): List<LogItemData> {
        if (logs.isEmpty()) return emptyList()

        val finalResult = ArrayList<LogItemData>()
        var latestItemInProcess: TextLogItemData? = null
        val replicatedItems = ArrayList<TextLogItemData>()
        logs.forEach {
            if (it is TextLogItemData) {
                if (it.text != latestItemInProcess?.text) {
                    moveReplicateItemsIfNeed(finalResult, replicatedItems)
                    latestItemInProcess = null
                }

                if (latestItemInProcess == null || latestItemInProcess?.text == it.text) {
                    latestItemInProcess = it
                    replicatedItems.add(it)
                } else {
                    throw IllegalStateException("Unsupported state")
                }
            } else {
                //first or new header iteration occur here
                finalResult.add(it)
                moveReplicateItemsIfNeed(finalResult, replicatedItems)
                latestItemInProcess = null
            }
        }
        return finalResult
    }

    private fun moveReplicateItemsIfNeed(finalResult: ArrayList<LogItemData>, replicatedItems: ArrayList<TextLogItemData>) {
        if (replicatedItems.isEmpty()) {
            return
        }
        if (replicatedItems.size == 1) {
            finalResult.add(replicatedItems.first())
        } else {
            //because [replicatedItems] comes sorted, there is no need to sort.
            finalResult.add(ReplicatedTextLogItemData(ArrayList(replicatedItems)))
        }
        replicatedItems.clear()
    }
}