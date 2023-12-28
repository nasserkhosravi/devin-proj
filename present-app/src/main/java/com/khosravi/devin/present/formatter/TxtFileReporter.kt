package com.khosravi.devin.present.formatter

import com.khosravi.devin.present.data.LogTable
import java.util.Date

internal object TxtFileReporter {

    fun create(
        versionName: String,
        logs: List<LogTable>
    ): TextualReport {
        val stringBuilder = StringBuilder()
            .append("version name:").append(versionName)
            .appendLine()

        logs.forEach {
            stringBuilder.append("* ")
                .append(it.tag)
                .append(": ")
                .append(it.value)
                .appendLine()
                .appendLine()
        }
        return TextualReport("DevinShareFile: ${Date()}.txt", stringBuilder.toString())
    }
}