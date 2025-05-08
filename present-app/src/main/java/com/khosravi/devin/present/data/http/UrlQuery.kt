package com.khosravi.devin.present.data.http

import java.net.MalformedURLException
import java.net.URL

class UrlQuery private constructor(private val helperUrl: URL) {

    val domain: String
        get() = helperUrl.host

    val protocol: String
        get() = helperUrl.protocol

    val path: String
        get() = helperUrl.path

    val isSsl: Boolean
        get() = helperUrl.protocol.equals("https", ignoreCase = true)

    val urlExternalForm: String get() = helperUrl.toString()

    companion object {

        @Throws(MalformedURLException::class)
        fun create(url: String) = UrlQuery(URL(url))

    }


}