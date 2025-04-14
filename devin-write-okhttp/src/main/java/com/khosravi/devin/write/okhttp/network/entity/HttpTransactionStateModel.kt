package com.khosravi.devin.write.okhttp.network.entity

import android.net.Uri
import java.net.URL

internal sealed class HttpTransactionStateModel(
    val url: URL
) {

    class Requested(
        url: URL,
        val request: HttpRequestModel
    ) : HttpTransactionStateModel(url)

    class Failed(
        url: URL,
        val request: HttpRequestModel,
        val dbUri: Uri,
        val exception: Exception
    ) : HttpTransactionStateModel(url)

    class Completed(
        url: URL,
        val request: HttpRequestModel,
        val response: HttpResponseModel,
        val dbUri: Uri
    ) : HttpTransactionStateModel(url)
}