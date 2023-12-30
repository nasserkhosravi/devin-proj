package com.khosravi.devin.present

import android.content.Context
import android.graphics.Color
import com.khosravi.devin.present.filter.ChipColor
import com.khosravi.devin.present.tool.TempReference

object ResourceHelper {

    private val colorCache = TempReference<ArrayList<ChipColor>>()

    fun getAFilterColor(context: Context, lastFilterIndex: Int): ChipColor {
        require(lastFilterIndex > -1)
        val colors = colorCache.get {
            val whiteTextColor = context.getColor(R.color.white_light_1)
            val darkTextColor = context.getColor(R.color.black_pearl_alpha_87)
            ArrayList<ChipColor>().apply {
                add(ChipColor(Color.parseColor("#0064fb"), whiteTextColor))
                add(ChipColor(Color.parseColor("#6457f9"), whiteTextColor))
                add(ChipColor(Color.parseColor("#19db7e"), whiteTextColor))
                add(ChipColor(Color.parseColor("#9f46f4"), whiteTextColor))
                add(ChipColor(Color.parseColor("#ff78ff"), whiteTextColor))
                add(ChipColor(Color.parseColor("#ff4ba6"), whiteTextColor))
                add(ChipColor(Color.parseColor("#95a8bc"), whiteTextColor))
                add(ChipColor(Color.parseColor("#ff7511"), whiteTextColor))
                add(ChipColor(Color.parseColor("#fb5779"), whiteTextColor))

                add(ChipColor(Color.parseColor("#ffa800"), darkTextColor))
                add(ChipColor(Color.parseColor("#ffd100"), darkTextColor))
                add(ChipColor(Color.parseColor("#ace60f"), darkTextColor))
                add(ChipColor(Color.parseColor("#00d4c8"), darkTextColor))
                add(ChipColor(Color.parseColor("#48dafd"), darkTextColor))
            }
        }

        val size = colors.size
        val fLastFilterIndex = if (lastFilterIndex > (size - 1)) size - lastFilterIndex
        else lastFilterIndex

        return colors[fLastFilterIndex]
    }

}