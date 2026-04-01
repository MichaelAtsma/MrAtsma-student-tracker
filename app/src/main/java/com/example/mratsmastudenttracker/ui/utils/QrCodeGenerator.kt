package com.example.mratsmastudenttracker.ui.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    fun generateQrCodeWithLogo(
        content: String,
        size: Int = 512,
        logo: Bitmap? = null
    ): Bitmap {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "utf-8"
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H // High error correction for logo
        hints[EncodeHintType.MARGIN] = 1

        val writer = QRCodeWriter()
        val bitMatrix = try {
            writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: Exception) {
            throw e
        }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        if (logo != null) {
            return overlayLogo(bitmap, logo)
        }

        return bitmap
    }

    private fun overlayLogo(qrBitmap: Bitmap, logo: Bitmap): Bitmap {
        val size = qrBitmap.width
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(qrBitmap, 0f, 0f, null)

        val logoSize = (size / 4).toInt()
        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)

        val left = (size - logoSize) / 2f
        val top = (size - logoSize) / 2f

        // Draw white background for logo to make it pop
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(left - 5, top - 5, left + logoSize + 5, top + logoSize + 5, paint)

        canvas.drawBitmap(scaledLogo, left, top, null)
        return result
    }
}
