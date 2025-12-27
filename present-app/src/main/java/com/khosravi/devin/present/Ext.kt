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
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.khosravi.devin.present.tool.NotEmptyString
import okio.IOException
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Serializable
import java.text.DecimalFormat
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


fun Context.tmpFileForCache(name: String): File {
    val imagePath = File(cacheDir, "/temp_shared")
    if (!imagePath.exists()) {
        imagePath.mkdir()
    }
    return File(imagePath, name)
}

fun Context.toUriByFileProvider(file: File): Uri {
    return FileProvider.getUriForFile(this, "com.khosravi.devin.present.fileprovider", file)
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
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
    return get(key).notNullOrReturnNull()?.asInt
}

fun JsonObject.getInt(key: String): Int {
    return get(key).asInt
}

fun JsonObject.getString(key: String): String {
    return get(key).asString
}

fun JsonObject.optString(key: String): String? {
    return get(key).notNullOrReturnNull()?.asString
}

private fun JsonElement?.notNullOrReturnNull(): JsonElement? {
    if (this != null && this !is JsonNull) {
        return try {
            this
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    return null
}

/**
 * Zips multiple files into one zip file.
 *
 * @param filesToZip List of files to include in the zip.
 * @param zipFile The output zip file.
 * @throws IOException If an I/O error occurs.
 */
@Throws(IOException::class)
fun zipFiles(filesToZip: List<File>, zipFile: File) {
    val buffer = ByteArray(1024)

    FileOutputStream(zipFile).use { fos ->
        ZipOutputStream(fos).use { zos ->
            for (file in filesToZip) {
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    var length: Int
                    while (fis.read(buffer).also { length = it } > 0) {
                        zos.write(buffer, 0, length)
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}


fun copyFileToOutputStream(file: File, outputStream: OutputStream) {
    FileInputStream(file).use { inputStream ->
        val buffer = ByteArray(1024) // Buffer size
        var bytesRead: Int

        // Read from the file and write to the OutputStream
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
    }
}


/**
 * Converts a file size in bytes to a human-readable string (e.g., 100 KB, 1.5 MB).
 * @param size The file size in bytes (Long).
 * @return The formatted human-readable size string.
 */
fun File.formatFileSize(): String {
    val size: Long = this.length()
    if (size <= 0) return "0 Bytes"

    val units = arrayOf("Bytes", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1000.0)).toInt()

    // Using DecimalFormat for one decimal place precision
    val df = DecimalFormat("#,##0.#")

    // The formula is size / 1000^digitGroups
    val formattedSize = df.format(size / Math.pow(1000.0, digitGroups.toDouble()))

    return "$formattedSize ${units[digitGroups]}"
}