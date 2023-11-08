package ir.khosravi.devin.present

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.Date


fun Context.fileForCache(name: String = "DevinShareFile: ${Date()}.txt"): File {
    val imagePath = File(cacheDir, "/temp_shared")
    if (!imagePath.exists()) {
        imagePath.mkdir()
    }
    return File(imagePath, name)
}

fun Context.toUriByFileProvider(file: File): Uri {
    return FileProvider.getUriForFile(this, "ir.khosravi.devin.present.fileprovider", file)
}

fun shareTxtFileIntent(fileUri: Uri): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
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