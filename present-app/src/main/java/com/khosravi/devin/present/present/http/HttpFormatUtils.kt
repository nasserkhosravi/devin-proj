package com.khosravi.devin.present.present.http

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.text.HtmlCompat
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.khosravi.devin.present.R
import com.khosravi.lib.har.HarHeader
import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.StringWriter
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.ln
import kotlin.math.pow

internal object HttpFormatUtils {
    private const val SI_MULTIPLE = 1000
    private const val BASE_TWO_MULTIPLE = 1024

    fun formatHeaders(
        context: Context,
        httpHeaders: List<HarHeader>?,
    ): Spanned {
        if (httpHeaders.isNullOrEmpty()) {
            return SpannableStringBuilder.valueOf(context.getString(R.string.msg_header_empty))
        }
        return httpHeaders.joinToString(separator = "") { header ->
            "<b> ${header.name}: </b>${header.value} <br />"
        }.let {
            HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    fun formatByteCount(
        bytes: Long,
        si: Boolean,
    ): String {
        val unit = if (si) SI_MULTIPLE else BASE_TWO_MULTIPLE

        if (bytes < unit) {
            return "$bytes B"
        }

        val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
        val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"

        return String.format(Locale.US, "%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
    }

    fun formatXml(xml: String): String {
        return try {
            val documentFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
            // This flag is required for security reasons
            documentFactory.isExpandEntityReferences = false

            val documentBuilder: DocumentBuilder = documentFactory.newDocumentBuilder()
            val inputSource = InputSource(ByteArrayInputStream(xml.toByteArray(Charset.defaultCharset())))
            val document: Document = documentBuilder.parse(inputSource)

            val domSource = DOMSource(document)
            val writer = StringWriter()
            val result = StreamResult(writer)

            TransformerFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }.newTransformer().apply {
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                setOutputProperty(OutputKeys.INDENT, "yes")
                transform(domSource, result)
            }
            writer.toString()
        } catch (ignore: SAXParseException) {
            xml
        } catch (ignore: IOException) {
            xml
        } catch (ignore: TransformerException) {
            xml
        }
    }

    fun formatUrlEncodedForm(form: String): String {
        return try {
            if (form.isBlank()) {
                return form
            }
            form.split("&").joinToString(separator = "\n") { entry ->
                val keyValue = entry.split("=")
                val key = keyValue[0]
                val value = if (keyValue.size > 1) URLDecoder.decode(keyValue[1], "UTF-8") else ""
                "$key: $value"
            }
        } catch (ignore: IllegalArgumentException) {
            form
        } catch (ignore: UnsupportedEncodingException) {
            form
        }
    }

    fun formatJson(json: String): String {
        return try {
            val je = JsonParser.parseString(json)
            GsonConverter.instance.toJson(je)
        } catch (e: JsonParseException) {
            json
        }
    }

    private fun formatBody(
        body: String,
        contentType: String?,
    ): String {
        return when {
            contentType.isNullOrBlank() -> body
            contentType.contains("json", ignoreCase = true) -> formatJson(body)
            contentType.contains("xml", ignoreCase = true) -> formatXml(body)
            contentType.contains("x-www-form-urlencoded", ignoreCase = true) ->
                formatUrlEncodedForm(body)

            else -> body
        }
    }

    /**
     * This method creates [android.text.SpannableString] from body
     * and add [ForegroundColorSpan] to text with different colors for better contrast between
     * keys and values and etc in the body.
     *
     * This method just works with json content-type yet, and calls [formatBody]
     * for other content-type until parser function will be developed for other content-types.
     */
    fun spanBody(
        configColor: JsonConfigColor,
        body: CharSequence,
        contentType: String?,
        context: Context?,
    ): CharSequence {
        return when {
            // TODO Implement Other Content Types
            contentType.isNullOrBlank() -> body
            contentType.contains("json", ignoreCase = true) && context != null -> {
                JsonSpanTextUtil(configColor).spanJson(body)
            }

            else -> formatBody(body.toString(), contentType)
        }
    }
}