package com.khosravi.devin.present

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.khosravi.devin.present.tool.NotEmptyString
import org.json.JSONException
import org.json.JSONObject
import java.io.File
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

fun shareFileIntent(fileUri: Uri, type: String): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        this.type = type
        clipData = ClipData.newRawUri("", fileUri)
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

fun String.creataNotEmpty() = NotEmptyString(this)

fun <T : Fragment> T.applyBundle(vararg pairs: Pair<String, Any?>): T {
    arguments = bundleOf(*pairs)
    return this
}

internal fun <T : Serializable> Bundle.getParcelableSupport(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getSerializable(key, clazz)!!
    } else this.getSerializable(key) as? T
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