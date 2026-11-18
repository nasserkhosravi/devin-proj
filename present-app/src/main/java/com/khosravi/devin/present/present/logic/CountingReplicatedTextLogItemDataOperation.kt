package com.khosravi.devin.present.present.logic

import com.khosravi.devin.present.log.LogItemData
import com.khosravi.devin.present.log.ReplicatedTextLogItemData
import com.khosravi.devin.present.log.TextLogItemData

class CountingReplicatedTextLogItemDataOperation(private val logs: List<LogItemData>) {

    fun get(): List<LogItemData> {
        if (logs.isEmpty()) return emptyList()

        val finalResult = ArrayList<LogItemData>()
        var latestItemInProcess: TextLogItemData? = null
        val replicatedItems = ArrayList<TextLogItemData>()
        logs.forEach {
            if (it is TextLogItemData) {
                val current = latestItemInProcess
                if (current != null && (it.text != current.text || it.tag != current.tag)) {
                    moveReplicateItemsIfNeed(finalResult, replicatedItems)
                }
                latestItemInProcess = it
                replicatedItems.add(it)
            } else {
                //first or new header iteration occur here
                moveReplicateItemsIfNeed(finalResult, replicatedItems)
                latestItemInProcess = null
                finalResult.add(it)
            }
        }
        moveReplicateItemsIfNeed(finalResult, replicatedItems)
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