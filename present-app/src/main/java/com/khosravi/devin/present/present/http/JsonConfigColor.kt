package com.khosravi.devin.present.present.http

import android.content.Context
import androidx.core.content.ContextCompat
import com.khosravi.devin.present.R

class JsonConfigColor(
    val keyColor: Int,
    val valueColor: Int,
    val digitsAndNullValueColor: Int,
    val signElementsColor: Int,
    val booleanColor: Int,
) {
    companion object {

        fun create(context: Context): JsonConfigColor {
            return JsonConfigColor(
                ContextCompat.getColor(context, R.color.colorHttpJsonKey),
                ContextCompat.getColor(context, R.color.colorHttpJsonValue),
                ContextCompat.getColor(context, R.color.colorHttpJsonDigit_null_value),
                ContextCompat.getColor(context, R.color.colorHttpJsonElements),
                ContextCompat.getColor(context, R.color.colorHttpJsonBoolean),
            )

        }
    }

}