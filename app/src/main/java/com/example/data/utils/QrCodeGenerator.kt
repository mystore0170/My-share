package com.example.data.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.util.UUID

data class NearShareQrData(
    val deviceName: String,
    val token: String? = null,
    val rawContent: String
)

object QrCodeGenerator {

    const val PROTOCOL_PREFIX = "NEARSHARE"
    const val VERSION = "v1"

    fun createPairingPayload(deviceName: String, token: String = UUID.randomUUID().toString().take(6).uppercase()): String {
        return "$PROTOCOL_PREFIX:$VERSION:DEVICE:$deviceName:TOKEN:$token"
    }

    fun parsePairingPayload(raw: String): NearShareQrData? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        return try {
            if (trimmed.startsWith("$PROTOCOL_PREFIX:$VERSION:DEVICE:")) {
                val afterDevice = trimmed.removePrefix("$PROTOCOL_PREFIX:$VERSION:DEVICE:")
                val parts = afterDevice.split(":TOKEN:")
                val devName = parts[0]
                val token = if (parts.size > 1) parts[1] else null
                NearShareQrData(deviceName = devName, token = token, rawContent = trimmed)
            } else if (trimmed.startsWith("$PROTOCOL_PREFIX:")) {
                // Legacy format: NEARSHARE:<deviceName>:<timestamp>
                val tokens = trimmed.split(":")
                val devName = if (tokens.size >= 2) tokens[1] else "Nearby Device"
                NearShareQrData(deviceName = devName, token = tokens.getOrNull(2), rawContent = trimmed)
            } else {
                // Fallback for general QR text
                NearShareQrData(deviceName = trimmed, token = null, rawContent = trimmed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateAndroidBitmap(
        content: String,
        sizePx: Int = 512,
        dotColor: Int = 0xFF0F172A.toInt(),
        bgColor: Int = 0xFFFFFFFF.toInt()
    ): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) dotColor else bgColor
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateQrCodeBitmap(
        content: String,
        sizePx: Int = 512,
        dotColor: Int = 0xFF0F172A.toInt(),
        bgColor: Int = 0xFFFFFFFF.toInt()
    ): ImageBitmap? {
        val bitmap = generateAndroidBitmap(content, sizePx, dotColor, bgColor)
        return bitmap?.asImageBitmap()
    }
}

