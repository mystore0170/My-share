package com.example.data.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

object QrCodeDecoder {

    private const val TAG = "QrCodeDecoder"

    private val readerHints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
    }

    /**
     * Decodes a QR code from an Android Bitmap using ZXing.
     */
    fun decodeBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)

        // 1. Try with HybridBinarizer (best for varying lighting)
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(readerHints) }
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: NotFoundException) {
            // Not found with HybridBinarizer, try fallback
        } catch (e: Exception) {
            Log.d(TAG, "HybridBinarizer error: ${e.message}")
        }

        // 2. Fallback to GlobalHistogramBinarizer (best for low contrast/screens)
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            val reader = MultiFormatReader().apply { setHints(readerHints) }
            val result = reader.decodeWithState(binaryBitmap)
            return result.text
        } catch (_: NotFoundException) {
            // Not found
        } catch (e: Exception) {
            Log.d(TAG, "GlobalHistogramBinarizer error: ${e.message}")
        }

        return null
    }

    /**
     * Decodes a CameraX ImageProxy frame using ZXing.
     */
    fun decodeImageProxy(image: ImageProxy): String? {
        return try {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val bitmap = image.toBitmap()
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
            decodeBitmap(rotatedBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding ImageProxy", e)
            null
        }
    }

    /**
     * Decodes a QR code from a file / gallery image Uri.
     */
    fun decodeUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    decodeBitmap(bitmap)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding image URI", e)
            null
        }
    }
}
