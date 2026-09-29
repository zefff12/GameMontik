package ru.montik.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import ru.montik.app.drawing.CutoutResult
import ru.montik.app.drawing.DrawingCutout
import kotlin.math.max

/** Итог обработки фотографии: готовый Монтик или понятное ребёнку сообщение об ошибке. */
sealed interface ProcessResult {
    class Ok(val bitmap: Bitmap) : ProcessResult
    class Error(val message: String) : ProcessResult
}

/** Android-обвязка вокруг [DrawingCutout]: чтение фото, поворот по EXIF, масштаб, сборка PNG. */
object DrawingProcessor {
    private const val WORK_SIDE = 720      // размер, с которым работает вырезание
    private const val SPRITE_SIDE = 640    // размер готового Монтика

    /** Загружает фото из [uri] с уменьшением и правильным поворотом. Возвращает null, если файл не открылся. */
    fun loadPhoto(context: Context, uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= WORK_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
            scaleDown(bitmap, WORK_SIDE)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Вырезает рисунок с белого листа. */
    fun cutout(photo: Bitmap): ProcessResult {
        return try {
            val w = photo.width
            val h = photo.height
            val pixels = IntArray(w * h)
            photo.getPixels(pixels, 0, w, 0, 0, w, h)
            when (val r = DrawingCutout.cutout(pixels, w, h)) {
                is CutoutResult.Success -> {
                    val bmp = Bitmap.createBitmap(r.pixels, r.width, r.height, Bitmap.Config.ARGB_8888)
                    ProcessResult.Ok(scaleDown(bmp, SPRITE_SIDE))
                }
                is CutoutResult.Failure -> ProcessResult.Error(r.reason.message)
            }
        } catch (e: Exception) {
            ProcessResult.Error("Не получилось обработать фото. Попробуй сфотографировать ещё раз.")
        } catch (e: OutOfMemoryError) {
            ProcessResult.Error("Фото слишком большое. Попробуй сфотографировать ещё раз.")
        }
    }

    /** Запасной вариант: оставить весь снимок как есть. */
    fun wholePhoto(photo: Bitmap): Bitmap = scaleDown(photo, SPRITE_SIDE)

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val k = maxSide.toFloat() / longest
        val nw = max(1, (src.width * k).toInt())
        val nh = max(1, (src.height * k).toInt())
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
