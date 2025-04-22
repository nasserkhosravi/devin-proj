package com.khosravi.devin.present

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import com.khosravi.devin.present.tool.NotEmptyString
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Serializable
import java.util.Date


fun Context.fileForCache(name: String = "DevinShareFile: ${Date()}.txt"): File {
    val imagePath = File(cacheDir, "/temp_shared")
    if (!imagePath.exists()) {
        imagePath.mkdir()
    }
    return File(imagePath, name)
}

fun Context.toUriByFileProvider(file: File): Uri {
    return FileProvider.getUriForFile(this, "com.khosravi.devin.present.fileprovider", file)
}

fun sendOrShareFileIntent(fileUri: Uri, type: String): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        this.type = type
        clipData = ClipData.newRawUri("", fileUri)
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun importFileIntent(type: String): Intent {
    return Intent(Intent.ACTION_GET_CONTENT).apply {
        this.type = type
    }
}

fun writeOrSaveFileIntent(fileName: String, type: String): Intent {
    return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        this.type = type
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_TITLE, fileName)
    }
}

@Suppress("DEPRECATION")
fun Context.getPackageInfo(): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }
}

fun Int.withPadding(): String {
    if (this < 10) {
        return "0$this"
    }
    return this.toString()
}

fun String.itsNotEmpty() = NotEmptyString(this)

fun <T : Fragment> T.applyBundle(vararg pairs: Pair<String, Any?>): T {
    arguments = bundleOf(*pairs)
    return this
}

internal fun <T : Serializable> Bundle.getSerializableSupport(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getSerializable(key, clazz)!!
    } else this.getSerializable(key) as? T
}

fun <T : Parcelable> Intent.getParcelableExtraSupport(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getParcelableExtra(key, clazz)!!
    } else this.getParcelableExtra(key) as? T
}

fun String.toSafeJSONObject(onException: ((JSONException) -> Unit)? = null): JSONObject? {
    if (!isNullOrEmpty()) {
        try {
            return JSONObject(this)
        } catch (e: JSONException) {
            onException?.invoke(e)
        }
    }
    return null
}

fun InputStream.readTextAndClose(): String {
    return this.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

fun OutputStream.writeTextAndClose(text: String) {
    val bw = BufferedWriter(OutputStreamWriter(this))
    bw.write(text)
    bw.flush()
    bw.close()
}

fun Context.setClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
}

fun Intent.getLongExtraOrFail(name: String): Long {
    return if (hasExtra(name)) {
        getLongExtra(name, -1)
    } else {
        throw IllegalStateException()
    }
}

fun JsonObject.optInt(key: String): Int? {
    return get(key)?.asInt
}

fun JsonObject.getInt(key: String): Int {
    return get(key).asInt
}

fun JsonObject.getString(key: String): String {
    return get(key).asString
}

fun JsonObject.optString(key: String): String? {
    return get(key)?.asString
}

