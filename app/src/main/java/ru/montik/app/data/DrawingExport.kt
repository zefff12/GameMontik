package ru.montik.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset

/**
 * Один мазок раскраски. Координаты — доли от рамки обводки Монтика (x — от ширины, y — от высоты),
 * толщина — доля от ширины рамки: рисунок одинаково ложится на обводку на обычном и расширенном
 * холсте и в готовой картинке.
 * [eraser] = true: мазок не красит, а стирает. [fill] — маска заливки (ALPHA_8, в сетке [PaintArt]):
 * такой «мазок» закрашивает область внутри линий целиком.
 */
class DrawStroke(
    val color: Int,
    val width: Float,
    val eraser: Boolean,
    val fill: Bitmap? = null
) {
    /** Точки мазка. Список «живой», поэтому холст перерисовывается, пока ребёнок ведёт пальцем. */
    val points = mutableStateListOf<Offset>()
}

/** Обводка и силуэт Монтика для раскраски (картинки из макета) и пропорции их рамки. */
object PaintArt {
    /** Высота рамки обводки к ширине — как в кадрах Figma «Нарисуй Монтика» (238×289 и 354×426). */
    const val ASPECT = 289f / 238f

    /** Обводка (линии на прозрачном фоне) и силуэт (непрозрачная внутренность героя). */
    class Images(val lines: Bitmap, val base: Bitmap)

    @Volatile
    private var cached: Images? = null

    fun load(context: Context): Images? {
        cached?.let { return it }
        val res = context.resources
        val pkg = context.packageName
        val linesId = res.getIdentifier("fg_paint_lines", "drawable", pkg)
        val baseId = res.getIdentifier("fg_paint_base", "drawable", pkg)
        if (linesId == 0 || baseId == 0) return null
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val lines = BitmapFactory.decodeResource(res, linesId, opts) ?: return null
        val base = BitmapFactory.decodeResource(res, baseId, opts) ?: return null
        return Images(lines, base).also { cached = it }
    }
}

/** Готовит картинку героя из раскраски: прозрачный фон, лишние поля обрезаны. */
object DrawingExport {
    /** Ширина рабочего холста в пикселях, пока рисунок собирается. */
    private const val WORK = 1024

    /** Наибольшая сторона готового спрайта. */
    private const val MAX_SIDE = 768

    /** Поля вокруг рисунка (доля от его размера). */
    private const val MARGIN = 0.06f

    /**
     * Возвращает картинку или null, если ничего не раскрашено (ничего не нарисовано или всё стёрто).
     * Мазки и заливки обрезаются по силуэту Монтика, сверху ложится обводка из макета —
     * получается раскрашенный ребёнком Монтик.
     */
    fun render(strokes: List<DrawStroke>, art: PaintArt.Images?): Bitmap? {
        if (strokes.none { !it.eraser && (it.points.isNotEmpty() || it.fill != null) }) return null

        val workH = (WORK * PaintArt.ASPECT).toInt()
        val full = Bitmap.createBitmap(WORK, workH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(full)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val whole = Rect(0, 0, WORK, workH)
        // Монтик белый, пока его не раскрасили: под раскраской — белый силуэт (иначе тело прозрачное).
        if (art != null) canvas.drawBitmap(art.base, null, whole, paint)

        for (stroke in strokes) {
            val mask = stroke.fill
            if (mask != null) {
                paint.xfermode = null
                // Картинка рисуется с альфой кисти — сбрасываем её после полупрозрачного маркера.
                paint.alpha = 255
                paint.colorFilter = PorterDuffColorFilter(stroke.color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(mask, null, whole, paint)
                paint.colorFilter = null
                continue
            }
            val pts = stroke.points.toList()
            if (pts.isEmpty()) continue
            // Ластик внутри Монтика возвращает белый цвет; без обводки — стирает до прозрачности.
            val erase = stroke.eraser
            paint.xfermode = if (erase && art == null) clear else null
            // Маркер полупрозрачный: альфа хранится в цвете мазка.
            paint.color = if (erase) 0xFFFFFFFF.toInt() else stroke.color
            val w = stroke.width * WORK
            if (pts.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pts[0].x * WORK, pts[0].y * workH, w / 2f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = w
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(smoothPath(pts, workH), paint)
            }
        }
        paint.xfermode = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt()

        if (art != null) {
            // Раскраска остаётся только внутри Монтика, сверху — его обводка.
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(art.base, null, whole, paint)
            paint.xfermode = null
            // Обводка тёмно-серая, как в макете.
            paint.colorFilter = PorterDuffColorFilter(0xFF4F4F55.toInt(), PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(art.lines, null, whole, paint)
            paint.colorFilter = null
        }

        val box = contentBounds(full) ?: return null
        return crop(full, box)
    }

    /** Гладкая линия через точки мазка: между соседними точками — середины, точки — «узлы». */
    private fun smoothPath(pts: List<Offset>, workH: Int): Path {
        val path = Path()
        path.moveTo(pts[0].x * WORK, pts[0].y * workH)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val cur = pts[i]
            path.quadTo(
                prev.x * WORK, prev.y * workH,
                (prev.x + cur.x) / 2f * WORK, (prev.y + cur.y) / 2f * workH
            )
        }
        val last = pts.last()
        path.lineTo(last.x * WORK, last.y * workH)
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

/**
 * Заливка «как в раскраске»: закрашивает область внутри линий обводки, куда ткнул ребёнок.
 * Считается на уменьшенной сетке [GRID_W] × [GRID_H]: линии и край силуэта — стенки.
 */
object PaintFill {
    const val GRID_W = 240
    val GRID_H = (GRID_W * PaintArt.ASPECT).toInt()

    @Volatile
    private var walls: BooleanArray? = null

    private fun walls(art: PaintArt.Images): BooleanArray {
        walls?.let { return it }
        val lines = Bitmap.createScaledBitmap(art.lines, GRID_W, GRID_H, true)
        val base = Bitmap.createScaledBitmap(art.base, GRID_W, GRID_H, true)
        val lp = IntArray(GRID_W * GRID_H)
        val bp = IntArray(GRID_W * GRID_H)
        lines.getPixels(lp, 0, GRID_W, 0, 0, GRID_W, GRID_H)
        base.getPixels(bp, 0, GRID_W, 0, 0, GRID_W, GRID_H)
        val out = BooleanArray(GRID_W * GRID_H) { i -> (lp[i] ushr 24) > 70 || (bp[i] ushr 24) < 128 }
        return out.also { walls = it }
    }

    /** Маска заливки для точки ([nx], [ny] — доли рамки обводки) или null, если ткнули в линию или мимо Монтика. */
    fun flood(art: PaintArt.Images, nx: Float, ny: Float): Bitmap? {
        val wall = walls(art)
        val sx = (nx * GRID_W).toInt()
        val sy = (ny * GRID_H).toInt()
        if (sx !in 0 until GRID_W || sy !in 0 until GRID_H) return null
        if (wall[sy * GRID_W + sx]) return null

        val filled = BooleanArray(GRID_W * GRID_H)
        val queue = IntArray(GRID_W * GRID_H)
        var head = 0
        var tail = 0
        queue[tail++] = sy * GRID_W + sx
        filled[sy * GRID_W + sx] = true
        while (head < tail) {
            val i = queue[head++]
            val x = i % GRID_W
            val y = i / GRID_W
            fun visit(nxp: Int, nyp: Int) {
                if (nxp < 0 || nyp < 0 || nxp >= GRID_W || nyp >= GRID_H) return
                val j = nyp * GRID_W + nxp
                if (!filled[j] && !wall[j]) {
                    filled[j] = true
                    queue[tail++] = j
                }
            }
            visit(x + 1, y); visit(x - 1, y); visit(x, y + 1); visit(x, y - 1)
        }

        // Чуть расширяем область под линии, чтобы между цветом и обводкой не было белой щели.
        val pixels = IntArray(GRID_W * GRID_H)
        for (y in 0 until GRID_H) for (x in 0 until GRID_W) {
            val i = y * GRID_W + x
            var on = filled[i]
            if (!on) {
                on = (x > 0 && filled[i - 1]) || (x < GRID_W - 1 && filled[i + 1]) ||
                    (y > 0 && filled[i - GRID_W]) || (y < GRID_H - 1 && filled[i + GRID_W])
            }
            if (on) pixels[i] = 0xFF000000.toInt()
        }
        val mask = Bitmap.createBitmap(GRID_W, GRID_H, Bitmap.Config.ARGB_8888)
        mask.setPixels(pixels, 0, GRID_W, 0, 0, GRID_W, GRID_H)
        return mask
    }
}
