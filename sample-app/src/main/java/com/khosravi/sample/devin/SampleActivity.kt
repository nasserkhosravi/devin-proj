package com.khosravi.sample.devin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import io.nasser.devin.api.DevinLogger
import com.khosravi.devin.write.DevinTool
import com.khosravi.sample.devin.databinding.ActivitySampleBinding
import java.lang.StringBuilder

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        val devinTool = DevinTool.create(this)
        val logger = devinTool.logger
        if (logger == null) {
            Snackbar.make(binding.root, "Devin is not enable", Snackbar.LENGTH_INDEFINITE).show()
            return
        }
        setupSpinnerAdapter(binding)

        binding.btnSend.setOnClickListener {
            sendLog(binding, logger)
        }

        logger.logCallerFunc()

        logger.doIfEnable {
            //You can do your heavy operation here like json validation or any custom execution must happen in debug
        }
    }

    private fun sendLog(binding: ActivitySampleBinding, logger: DevinLogger) {
        val tag = binding.edTag.text.toString()
        val message = binding.edMessage.text.toString()
        val selectedItemPosition = binding.spLogLevel.selectedItemPosition
        val throwable = if (binding.cbWithException.isChecked) Throwable("Something went wrong") else null

        //+3 to sync selectedItemPosition to [android.util.Log] levels
        when (selectedItemPosition + 3) {
            Log.DEBUG -> {
                logger.debug(tag, message, null, throwable)
            }

            Log.INFO -> {
                logger.info(tag, message, null, throwable)
            }

            Log.WARN -> {
                logger.warning(tag, message, null, throwable)
            }

            Log.ERROR -> {
                logger.error(tag, message, null, throwable)
            }
        }
    }

    private fun setupSpinnerAdapter(binding: ActivitySampleBinding) {
        ArrayAdapter.createFromResource(
            this,
            R.array.log_level_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears.
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner.
            binding.spLogLevel.adapter = adapter
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