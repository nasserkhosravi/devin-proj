package com.khosravi.sample.devin

import android.content.res.AssetManager
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.khosravi.devin.write.DevinTool
import com.khosravi.devin.write.okhttp.DevinOkHttpLogger
import com.khosravi.devin.write.okhttp.okhttpLogger
import com.khosravi.lib.har.HarConverter.toHarFile
import com.khosravi.sample.devin.databinding.ActivityHttpSampleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.use
import org.json.JSONObject

class OkHttpSampleActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private var httpClient: OkHttpClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHttpSampleBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        DevinTool.init(this)
        val devinTool: DevinTool? = DevinTool.get()
        val logger = devinTool?.logger
        if (logger == null) {
            Snackbar.make(binding.root, "Devin is not available", Snackbar.LENGTH_INDEFINITE).show()
            return
        }

        httpClient = devinTool.okhttpLogger?.buildOkHttpClient()
        if (httpClient == null) {
            Snackbar.make(binding.root, "Devin-write-okhttp is not available", Snackbar.LENGTH_INDEFINITE).show()
            return
        }

        val fileString = assets.readString("okhttp-sample/okhttp_sample1.json")

        binding.btnMakeHttp.setOnClickListener {
            launch {
                makeHttpFlow(fileString)
                    .onStart {
                        binding.tvResult.text = "Loading"
                    }
                    .flowOn(Dispatchers.Main)
                    .collect {
                        Log.d("xosro", "response: ${it.toString()}")
                        binding.tvResult.text = styleErrorMessage(it)
                    }
            }
        }
    }


    private fun styleErrorMessage(optionalMessage: String?): Spannable {
        val message = optionalMessage ?: return SpannableString("")
        val spannableString = SpannableString(message)
        if (message.startsWith("Error", ignoreCase = true)) {
            // Set the color for the word "Error"
            val errorEndIndex = (message.indexOf(' ') != -1)
                .let { if (it) message.indexOf(' ') else message.length }

            spannableString.setSpan(
                ForegroundColorSpan(Color.RED),
                0,
                errorEndIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString
    }

    private fun makeHttpFlow(fileString: String): Flow<String?> {
        return flow {
            val reqBuilderResult = convertOkHttpRequestBuilderFromHarString(fileString)
            val throwable = reqBuilderResult.exceptionOrNull()
            if (throwable == null) {
                //success
                val request = reqBuilderResult.getOrThrow().build()
                val result = httpClient?.launchAndGetResponse(request)
                emit(result)
            } else {
                throw throwable
            }
        }.map { it?.string() }
            .flowOn(Dispatchers.IO)
            .catch {
                emit(it.message ?: "Error")
            }

    }

    private fun DevinOkHttpLogger.buildOkHttpClient(): OkHttpClient? {
        val networkLogger = this
       return networkLogger.getOrCreateInterceptor()?.let { devinInterceptorLogger ->
            OkHttpClient.Builder()
                .addInterceptor(devinInterceptorLogger)
                .addInterceptor(createOtherOkHttpInterceptor())
                .build()
        } ?: return null
    }

    private fun convertOkHttpRequestBuilderFromHarString(harFileString: String): Result<Request.Builder> {
        val jsonObj: JSONObject = try {
            JSONObject(harFileString)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(Exception("Error in parsing asset sample"))
        }

        val harFile = try {
            jsonObj.toHarFile()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(Exception("Error in converting json to HAR"))
        }

        val harEntry = harFile.log.entries.firstOrNull() ?: return Result.failure(Exception("Error, No har entry found"))
        val harRequest = harEntry.request
        val reqBuilder = Request.Builder()

        harRequest.headers.forEach {
            reqBuilder.addHeader(it.name, it.value)
        }

        val postData = harRequest.postData
        var body: RequestBody? = null
        postData?.let {
            val mediaType = postData.mimeType.toMediaType()
            body = RequestBody.create(mediaType, postData.text)
            //TODO: what should we do with postData.params?
        }
        return Result.success(
            reqBuilder.method(harRequest.method, body)
                .url(harRequest.url)
        )
    }

    private fun OkHttpClient.launchAndGetResponse(request: Request): ResponseBody? {
        return newCall(request).execute().body
    }
}


private fun createOtherOkHttpInterceptor(): Interceptor {
    return Interceptor { chain ->
        Log.d("xosro", "MyOtherOkHttpInterceptor thread: ${Thread.currentThread().name}")
        val response = chain.proceed(request = chain.request())
        val responseBodyString: String = response.peekBody(Long.MAX_VALUE).string()
        Log.d("OtherOkHttpInterceptor", responseBodyString)
        return@Interceptor response
    }
}

private fun AssetManager.readString(fullFileName: String): String {
    return this.open(fullFileName).bufferedReader().use { it.readText() }
}