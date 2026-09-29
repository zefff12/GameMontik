package ru.montik.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset

/**
 * Один мазок кисти. Координаты — доли от стороны квадратного холста (0..1), а толщина — доля
 * от стороны: рисунок одинаково выглядит на экране любого размера и в готовой картинке.
 * [eraser] = true: мазок не красит, а стирает.
 */
class DrawStroke(
    val color: Int,
    val width: Float,
    val eraser: Boolean
) {
    /** Точки мазка. Список «живой», поэтому холст перерисовывается, пока ребёнок ведёт пальцем. */
    val points = mutableStateListOf<Offset>()
}

/** Готовит картинку героя из мазков: прозрачный фон, лишние поля обрезаны. */
object DrawingExport {
    /** Сторона рабочего холста в пикселях, пока рисунок собирается. */
    private const val WORK = 1024

    /** Наибольшая сторона готового спрайта. */
    private const val MAX_SIDE = 768

    /** Поля вокруг рисунка (доля от его размера). */
    private const val MARGIN = 0.06f

    /**
     * Возвращает картинку или null, если на холсте ничего не осталось
     * (ничего не нарисовано или всё стёрто).
     */
    fun render(strokes: List<DrawStroke>): Bitmap? {
        if (strokes.none { !it.eraser && it.points.isNotEmpty() }) return null

        val full = Bitmap.createBitmap(WORK, WORK, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        for (stroke in strokes) {
            val pts = stroke.points.toList()
            if (pts.isEmpty()) continue
            paint.xfermode = if (stroke.eraser) clear else null
            paint.color = stroke.color or 0xFF000000.toInt()
            val w = stroke.width * WORK
            if (pts.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pts[0].x * WORK, pts[0].y * WORK, w / 2f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(smoothPath(pts), paint)
            }
        }
        paint.xfermode = null

        val box = contentBounds(full) ?: return null
        return crop(full, box)
    }

    /** Гладкая линия через точки мазка: между соседними точками — середины, точки — «узлы». */
    private fun smoothPath(pts: List<Offset>): Path {
        val path = Path()
        path.moveTo(pts[0].x * WORK, pts[0].y * WORK)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val cur = pts[i]
            path.quadTo(
                prev.x * WORK, prev.y * WORK,
                (prev.x + cur.x) / 2f * WORK, (prev.y + cur.y) / 2f * WORK
            )
        }
        val last = pts.last()
        path.lineTo(last.x * WORK, last.y * WORK)
        return path
    }

    /** Рамка, в которой есть непрозрачные пиксели: [left, top, right, bottom] или null, если холст пуст. */
    private fun contentBounds(bitmap: Bitmap): IntArray? {
        val w = bitmap.width
        val h = bitmap.height
        val row = IntArray(w)
        var left = w
        var right = -1
        var top = h
        var bottom = -1
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right < 0) null else intArrayOf(left, top, right, bottom)
    }

    /** Вырезает рисунок с полями, делает квадратным и уменьшает до [MAX_SIDE]. */
    private fun crop(source: Bitmap, box: IntArray): Bitmap {
        val bw = box[2] - box[0] + 1
        val bh = box[3] - box[1] + 1
        val side = (maxOf(bw, bh) * (1f + 2f * MARGIN)).toInt().coerceAtLeast(16)
        val cx = (box[0] + box[2]) / 2
        val cy = (box[1] + box[3]) / 2

        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, (side / 2 - cx).toFloat(), (side / 2 - cy).toFloat(), null)

        return if (side > MAX_SIDE) Bitmap.createScaledBitmap(out, MAX_SIDE, MAX_SIDE, true) else out
    }
}
