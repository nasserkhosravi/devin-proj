package com.khosravi.sample.devin

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.khosravi.devin.write.DevinTool
import com.khosravi.sample.devin.databinding.ActivitySampleBinding
import java.lang.StringBuilder

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.edCustomText.setText("Custom")

        val devinTool = DevinTool.create(this)
        val logger = devinTool.logger
        if (logger == null) {
            Snackbar.make(binding.root, "Devin is not enable", Snackbar.LENGTH_INDEFINITE).show()
            return
        }
        binding.btnSendDebug.setOnClickListener {
            logger.debug(null, binding.edCustomText.text.toString())
        }

        binding.btnSendAnalytic.setOnClickListener {
            val message = listOf("Superapp", "Home", "TapOnCard").formatForAnalytic()
            logger.info("Analytic", message)
        }

        logger.logCallerFunc()

        logger.doIfEnable {
            //You can do your heavy operation here like json validation or any custom execution must happen in debug
        }
    }

    private fun List<String>.formatForAnalytic(): String {
        val builder = StringBuilder()
        val lastIndex = lastIndex
        forEachIndexed { index, s ->
            if (index != lastIndex) {
                builder.append(s.plus("->"))
            } else {
                builder.append(s)
            }
        }
        return builder.toString()
    }
}