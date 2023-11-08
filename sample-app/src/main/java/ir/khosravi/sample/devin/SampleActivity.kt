package ir.khosravi.sample.devin

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import ir.khosravi.devin.write.devinLogger
import ir.khosravi.sample.devin.databinding.ActivitySampleBinding

class SampleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        binding.edCustomText.setText("Custom text")

        binding.btnSendDebug.setOnClickListener {
            devinLogger(this).debug(binding.edCustomText.text.toString())
        }

        binding.btnSendAnalytic.setOnClickListener {
            devinLogger(this).custom("Analytic", binding.edCustomText.text.toString())
        }

    }
}