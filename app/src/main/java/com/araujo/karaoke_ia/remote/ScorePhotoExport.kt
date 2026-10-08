package com.araujo.karaoke_ia.remote

import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Creates a bounded, shareable copy; the original podium photo stays unchanged. */
object ScorePhotoExport {
    fun create(context: Context, source: File, token: String, score: Int, title: String, place: Int?): File {
        require(token.matches(Regex("[a-f0-9-]{36}")))
        val target = File(context.filesDir, "score_exports/$token.jpg")
        if (target.isFile && target.length() > 0) return target
        target.parentFile?.mkdirs()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1600) inSampleSize *= 2
        }
        val decoded = requireNotNull(BitmapFactory.decodeFile(source.absolutePath, options))
        val matrix = Matrix()
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(1)
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(270f)
        }
        val photo = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val result = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 1080f, 1350f,
                intArrayOf(Color.rgb(245, 48, 126), Color.rgb(111, 65, 227), Color.rgb(24, 190, 204)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 1080f, 1350f, paint)
            paint.shader = null
            paint.color = Color.rgb(15, 16, 23)
            canvas.drawRoundRect(RectF(32f, 32f, 1048f, 1318f), 36f, 36f, paint)
            val area = RectF(64f, 148f, 1016f, 1100f)
            val scale = maxOf(area.width() / photo.width, area.height() / photo.height)
            val width = area.width() / scale
            val height = area.height() / scale
            val left = ((photo.width - width) / 2).toInt()
            val top = ((photo.height - height) / 2).toInt()
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(area, 24f, 24f, Path.Direction.CW) })
            canvas.drawBitmap(photo, Rect(left, top, left + width.toInt(), top + height.toInt()), area, paint)
            canvas.restore()
            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = 38f
            canvas.drawText("KARAOKÊ", 64f, 106f, paint)
            paint.textSize = 54f
            canvas.drawText("$score pontos", 64f, 1180f, paint)
            place?.let {
                paint.textSize = 30f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${it}º no pódio", 1016f, 1180f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            paint.textSize = 32f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            val text = android.text.TextUtils.ellipsize(title.replace('\n', ' '), android.text.TextPaint(paint), 952f,
                android.text.TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, 64f, 1252f, paint)
            val temp = File(target.parentFile, "$token.tmp")
            try {
                temp.outputStream().use { check(result.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
                check(temp.renameTo(target))
            } finally { temp.delete() }
            return target
        } finally {
            result.recycle()
            if (photo !== decoded) photo.recycle()
            decoded.recycle()
        }
    }
}
