package com.araujo.karaoke_ia.remote

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object QrCodeGenerator {
    fun createBitmap(content: String, size: Int = 240): Bitmap? {
        return try {
            val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bitmap ->
                for (x in 0 until size) for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
