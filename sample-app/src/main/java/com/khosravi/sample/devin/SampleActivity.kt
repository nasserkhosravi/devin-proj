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
import org.json.JSONObject
import kotlin.random.Random

class SampleActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        DevinTool.init(
            this, presenterConfig = JSONObject()
                .put("logPassword", "12346")
        )

        val devinTool: DevinTool? = DevinTool.get()
        val logger = devinTool?.logger
        if (logger == null) {
            Snackbar.make(binding.root, "Devin is not available", Snackbar.LENGTH_INDEFINITE).show()
            return
        }
        setupSpinnerAdapter(binding)

        logger.logSessionStart(this)

        binding.btnSend.setOnClickListener {
            sendLog(binding, logger)
        }

        binding.btnSendLogs.setOnClickListener {
            binding.edLogCount.text.toString().toIntOrNull()?.let {
                sendLogs(binding, logger, it)
            } ?: Toast.makeText(this, "Error in getting number", Toast.LENGTH_SHORT).show()
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

        binding.btnGenerateLogs.setOnClickListener {
            val tags = List(10) { i ->
                "a${i}"
            }
            tags.forEach {
                val text = generateRandomStringKB(2)
                logger.debug(it,text)
            }
            Toast.makeText(this,"done", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendLog(binding: ActivitySampleBinding, logger: DevinLogger) {
        getLogInputParams(binding) { tag, message, logLevel, throwable ->
            sendLog(logger, tag, logLevel, message, throwable)
        }
    }


    private fun sendLogs(binding: ActivitySampleBinding, logger: DevinLogger, count: Int) {
        getLogInputParams(binding) { tag, message, logLevel, throwable ->
            for (i in 0 until count) {
                val fMessage = message.plus(" ${i + 1}")
                sendLog(logger, tag, logLevel, fMessage, throwable)
            }
        }

    }

    private fun getLogInputParams(
        binding: ActivitySampleBinding,
        action: (
            tag: String,
            message: String,
            logLevel: Int, throwable: Throwable?
        ) -> Unit
    ) {
        val tag = binding.edTag.text.toString()
        val message = binding.edMessage.text.toString()
        val selectedItemPosition = binding.spLogLevel.selectedItemPosition
        val throwable = if (binding.cbWithException.isChecked) Throwable("Something went wrong") else null
        action(tag, message, selectedItemPosition, throwable)
    }

    private fun sendLog(
        logger: DevinLogger,
        tag: String,
        itemPosition: Int,
        message: String,
        throwable: Throwable?
    ) {
        val logLevel = when (itemPosition) {
            0 -> Log.VERBOSE
            1 -> Log.DEBUG
            2 -> Log.INFO
            3 -> Log.WARN
            4 -> Log.ERROR
            else -> 1
        }
        logger.send(logLevel, tag, message, null, throwable)
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