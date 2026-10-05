package com.example.data.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import java.util.Locale

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val decimalFormat = DecimalFormat("#,##0.#")
        return "${decimalFormat.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0.0 MB/s"
        val mbPerSec = bytesPerSec.toDouble() / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbPerSec)
        } else {
            val kbPerSec = bytesPerSec.toDouble() / 1024.0
            String.format(Locale.US, "%.1f KB/s", kbPerSec)
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "--:--"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun getMimeTypeFromExtension(extension: String): String {
        val ext = extension.lowercase(Locale.ROOT).trimStart('.')
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun getCategoryFromMimeType(mimeType: String?): com.example.data.model.TransferCategory {
        val mime = mimeType?.lowercase(Locale.ROOT) ?: ""
        return when {
            mime.startsWith("image/") -> com.example.data.model.TransferCategory.PHOTOS
            mime.startsWith("video/") -> com.example.data.model.TransferCategory.VIDEOS
            mime.startsWith("audio/") -> com.example.data.model.TransferCategory.AUDIO
            else -> com.example.data.model.TransferCategory.FILES
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val tempDir = File(context.cacheDir, "nearshare_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            val tempFile = File(tempDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getReceivedFilesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "NearShare")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun openFile(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val extension = file.extension
            val mimeType = getMimeTypeFromExtension(extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFile(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
