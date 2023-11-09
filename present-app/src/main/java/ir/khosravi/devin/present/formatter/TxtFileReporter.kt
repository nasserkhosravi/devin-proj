package ir.khosravi.devin.present.formatter

import ir.khosravi.devin.write.room.LogTable
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
                .append(it.type)
                .append(": ")
                .append(it.value)
                .appendLine()
                .appendLine()
        }
        return TextualReport("DevinShareFile: ${Date()}.txt", stringBuilder.toString())
    }
}