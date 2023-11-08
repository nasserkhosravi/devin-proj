package ir.khosravi.devin.present.formatter

import android.content.Context
import ir.khosravi.devin.present.getPackageInfo
import ir.khosravi.devin.write.room.LogTable


object TxtFileFormatter {

    fun execute(
        context: Context,
        logs: List<LogTable>
    ): String {
        val pInfo = context.getPackageInfo()
        val stringBuilder = StringBuilder()
            .append("version name:").append(pInfo.versionName)
            .appendLine()

        logs.forEach {
            stringBuilder.append("* ")
                .append(it.type)
                .append(": ")
                .append(it.value)
                .appendLine()
                .appendLine()
        }
        return stringBuilder.toString()
    }
}