package com.khosravi.sample.devin

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.khosravi.devin.write.DevinTool
import com.khosravi.sample.devin.databinding.ActivitySampleBinding

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
            logger.log(binding.edCustomText.text.toString())
        }

        binding.btnSendAnalytic.setOnClickListener {
            logger.log("My-Analytic", binding.edCustomText.text.toString())
        }

        logger.logCallerFunc(enableParentName = true)

    }
}