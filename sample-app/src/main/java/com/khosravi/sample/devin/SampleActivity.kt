package com.khosravi.sample.devin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.snackbar.Snackbar
import com.khosravi.devin.api.DevinLogger
import com.khosravi.devin.write.DevinTool
import com.khosravi.sample.devin.databinding.ActivitySampleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope


class SampleActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        val devinTool: DevinTool? = DevinTool.getOrCreate(this)
        val logger = devinTool?.logger
        if (logger == null) {
            Snackbar.make(binding.root, "Devin is not available", Snackbar.LENGTH_INDEFINITE).show()
            return
        }
        setupSpinnerAdapter(binding)

        binding.btnSend.setOnClickListener {
            sendLog(binding, logger)
        }

        binding.btnCauseCrash.setOnClickListener {
            throw IllegalStateException("My message from exception that appears in UncaughtExceptionHandler")
        }

        //you can have your UncaughtExceptionHandler here without conflict to devin general exception handler.
//        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
//            paramThread
//        }
        //this line enable logging uncaught exceptions
        logger.generalUncaughtExceptionLogging(true)

        logger.logCallerFunc()

        logger.doIfEnable {
            //You can do your heavy operation here like json validation or any custom execution must happen in debug
        }

        binding.btnFetchImage.setOnClickListener {
            val urlString = binding.edUrlImage.text.toString()
            if (urlString.isEmpty()) {
                return@setOnClickListener
            }
            try {
                //test if [urlString] is a valid url
                urlString.toUri()

                binding.img.load(urlString) {
                    listener(CoilCompositeListener().apply {
                        addDevinImageLogger(urlString, devinTool)
                        addListener(onStart = {
                            Toast.makeText(this@SampleActivity, "start", Toast.LENGTH_SHORT).show()
                        }, onError = { _, r ->
                            r.throwable.printStackTrace()
                            Toast.makeText(this@SampleActivity, "failed", Toast.LENGTH_SHORT).show()
                            binding.img.setImageDrawable(null)
                        }, onSuccess = { a, r ->
                            binding.img.setImageDrawable(r.drawable)
                            Toast.makeText(this@SampleActivity, "succeed", Toast.LENGTH_SHORT).show()
                        })
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Exception, maybe not a valid URL", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGoToHttpSample.setOnClickListener {
            startActivity(Intent(this, OkHttpSampleActivity::class.java))
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

    private fun CoilCompositeListener.addDevinImageLogger(url: String, devinTool: DevinTool) {
        addListener(onStart = {
            devinTool.imageLogger?.downloading(url = url)
        }, onError = { _: ImageRequest, errorResult: ErrorResult ->
            devinTool.imageLogger?.failed(url = url, throwable = errorResult.throwable)
            errorResult.throwable.printStackTrace()
        }, onSuccess = { _: ImageRequest, _: SuccessResult ->
            devinTool.imageLogger?.succeed(url = url)
        })
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