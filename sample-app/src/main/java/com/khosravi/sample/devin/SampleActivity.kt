package com.khosravi.sample.devin

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.khosravi.devin.write.devinLogger
import com.khosravi.sample.devin.databinding.ActivitySampleBinding

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.edCustomText.setText("Custom")

        val devinLogger = devinLogger(this)
        binding.btnSendDebug.setOnClickListener {
            devinLogger.log(binding.edCustomText.text.toString())
        }

        binding.btnSendAnalytic.setOnClickListener {
            devinLogger.log("My-Analytic", binding.edCustomText.text.toString())
        }

        devinLogger.logCallerFunc(enableParentName = true)

    }
}