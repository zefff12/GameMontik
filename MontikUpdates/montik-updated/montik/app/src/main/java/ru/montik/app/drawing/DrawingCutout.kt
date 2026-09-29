package ru.montik.app.drawing

import kotlin.math.max
import kotlin.math.min

/** Результат вырезания рисунка из фотографии листа. */
sealed interface CutoutResult {
    /** [pixels] — ARGB (не premultiplied), фон прозрачный, размер уже обрезан по рисунку. */
    class Success(val pixels: IntArray, val width: Int, val height: Int) : CutoutResult

    class Failure(val reason: Reason) : CutoutResult
}

enum class Reason(val message: String) {
    EMPTY("Не удалось найти рисунок. Нарисуй Монтика фломастером или карандашом покрупнее и сфотографируй при хорошем свете."),
    NO_BACKGROUND("Не получилось отделить рисунок от листа. Положи лист на ровную поверхность, чтобы вокруг рисунка был свободный белый край.")
}

/**
 * Вырезание рисунка ребёнка с белого листа — полностью на устройстве, без интернета и облачного ИИ.
 *
 * Идея:
 *  1. Оцениваем цвет бумаги в каждой части кадра (так тени и неровный свет не мешают).
 *  2. «Чернилами» считаем всё, что заметно темнее или цветнее бумаги.
 *  3. Заливкой от краёв кадра находим фон. Внутренность контура (даже белая) остаётся частью Монтика.
 *  4. Мелкие пятнышки убираем, края сглаживаем, обрезаем по рисунку.
 */
object DrawingCutout {
    private const val INK_THRESHOLD = 0.16f
    private const val BARRIER_RADIUS = 3      // закрывает небольшие разрывы в контуре (до ~6 px)
    private const val TRIM_RADIUS = 3         // убирает белую «кайму» вокруг линий
    private const val MIN_AREA = 0.004        // доля кадра
    private const val MAX_AREA = 0.92
    private const val PAPER_FLOOR = 0.6f      // фон не может быть темнее 60% от общего цвета бумаги
    private const val MARGIN = 4

    fun cutout(src: IntArray, w: Int, h: Int): CutoutResult {
        require(w > 0 && h > 0 && src.size >= w * h) { "Неверный размер изображения" }
        if (w < 16 || h < 16) return CutoutResult.Failure(Reason.EMPTY)
        val n = w * h

        // 1–2. Фон и «чернила».
        val grid = estimateBackground(src, w, h)
        val ink = BooleanArray(n)
        val norm = IntArray(n)
        val bg = FloatArray(3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                grid.at(x, y, bg)
                val p = src[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val strength = maxOf(1f - r / bg[0], 1f - g / bg[1], 1f - b / bg[2])
                ink[i] = strength > INK_THRESHOLD
                val nr = min(255, (r * 255f / bg[0]).toInt())
                val ng = min(255, (g * 255f / bg[1]).toInt())
                val nb = min(255, (b * 255f / bg[2]).toInt())
                norm[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }

        // 3. Фон — это всё, до чего можно дойти от краёв кадра, не пересекая линий рисунка.
        val barrier = dilate(ink, w, h, BARRIER_RADIUS)
        val background = floodFromBorder(barrier, w, h)
        val subject = BooleanArray(n) { !background[it] }

        // 4. Убираем мелкий мусор: оставляем крупные части рисунка.
        val kept = keepLargeParts(subject, w, h)
        var area = 0
        for (i in 0 until n) if (kept[i]) area++
        if (area < n * MIN_AREA) return CutoutResult.Failure(Reason.EMPTY)
        if (area > n * MAX_AREA) return CutoutResult.Failure(Reason.NO_BACKGROUND)

        // Срезаем бумажную кайму по краю, но линии рисунка оставляем целиком.
        val bgNear = dilate(BooleanArray(n) { !kept[it] }, w, h, TRIM_RADIUS)
        val keep = BooleanArray(n) { kept[it] && (ink[it] || !bgNear[it]) }

        // Границы рисунка.
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (keep[y * w + x]) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return CutoutResult.Failure(Reason.EMPTY)
        minX = max(0, minX - MARGIN); minY = max(0, minY - MARGIN)
        maxX = min(w - 1, maxX + MARGIN); maxY = min(h - 1, maxY + MARGIN)
        val cw = maxX - minX + 1
        val ch = maxY - minY + 1

        // Плавный край: альфа = доля «своих» соседей (только внутрь рисунка, наружу ореола нет).
        val out = IntArray(cw * ch)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val sx = x + minX
                val sy = y + minY
                val si = sy * w + sx
                if (!keep[si]) continue
                var cnt = 0
                var total = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = sx + dx
                    val ny = sy + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    total++
                    if (keep[ny * w + nx]) cnt++
                }
                val alpha = if (total == 0) 255 else 64 + (191 * cnt) / total
                out[y * cw + x] = (alpha shl 24) or (norm[si] and 0x00FFFFFF)
            }
        }
        return CutoutResult.Success(out, cw, ch)
    }

    // ───────────────────────── Оценка цвета бумаги ─────────────────────────

    private class Grid(
        val block: Int, val bw: Int, val bh: Int,
        val r: FloatArray, val g: FloatArray, val b: FloatArray
    ) {
        /** Цвет бумаги в точке (x, y) — билинейная интерполяция между блоками. */
        fun at(x: Int, y: Int, out: FloatArray) {
            val gx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (bw - 1).toFloat())
            val gy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (bh - 1).toFloat())
            val x0 = gx.toInt()
            val y0 = gy.toInt()
            val x1 = min(x0 + 1, bw - 1)
            val y1 = min(y0 + 1, bh - 1)
            val fx = gx - x0
            val fy = gy - y0
            out[0] = lerp2(r, x0, x1, y0, y1, fx, fy)
            out[1] = lerp2(g, x0, x1, y0, y1, fx, fy)
            out[2] = lerp2(b, x0, x1, y0, y1, fx, fy)
        }

        private fun lerp2(a: FloatArray, x0: Int, x1: Int, y0: Int, y1: Int, fx: Float, fy: Float): Float {
            val top = a[y0 * bw + x0] * (1 - fx) + a[y0 * bw + x1] * fx
            val bottom = a[y1 * bw + x0] * (1 - fx) + a[y1 * bw + x1] * fx
            return top * (1 - fy) + bottom * fy
        }
    }

    private fun lum(r: Float, g: Float, b: Float) = 0.299f * r + 0.587f * g + 0.114f * b

    private fun estimateBackground(src: IntArray, w: Int, h: Int): Grid {
        val block = max(8, max(w, h) / 20)
        val bw = (w + block - 1) / block
        val bh = (h + block - 1) / block
        val nb = bw * bh
        val r0 = FloatArray(nb)
        val g0 = FloatArray(nb)
        val b0 = FloatArray(nb)
        val hist = IntArray(256)

        // Для каждого блока — средний цвет самых светлых 15% пикселей (это бумага, а не линии).
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                val x0 = bx * block
                val y0 = by * block
                val x1 = min(w, x0 + block)
                val y1 = min(h, y0 + block)
                hist.fill(0)
                var count = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    val p = src[y * w + x]
                    val l = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000
                    hist[l]++
                    count++
                }
                val target = max(1, count * 15 / 100)
                var acc = 0
                var t = 255
                while (t > 0) {
                    acc += hist[t]
                    if (acc >= target) break
                    t--
                }
                var sr = 0L; var sg = 0L; var sb = 0L; var m = 0
                for (y in y0 until y1) for (x in x0 until x1) {
                    val p = src[y * w + x]
                    val rr = (p shr 16) and 0xFF
                    val gg = (p shr 8) and 0xFF
                    val bb = p and 0xFF
                    val l = (299 * rr + 587 * gg + 114 * bb) / 1000
                    if (l >= t) { sr += rr; sg += gg; sb += bb; m++ }
                }
                val idx = by * bw + bx
                if (m == 0) { r0[idx] = 255f; g0[idx] = 255f; b0[idx] = 255f }
                else { r0[idx] = sr.toFloat() / m; g0[idx] = sg.toFloat() / m; b0[idx] = sb.toFloat() / m }
            }
        }

        // Закрашенные крупные области рисунка не должны считаться «бумагой»:
        // берём цвет самого светлого блока в окрестности.
        val r1 = FloatArray(nb)
        val g1 = FloatArray(nb)
        val b1 = FloatArray(nb)
        val radius = 2
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                var best = -1
                var bestL = -1f
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    val x = bx + dx
                    val y = by + dy
                    if (x < 0 || y < 0 || x >= bw || y >= bh) continue
                    val j = y * bw + x
                    val l = lum(r0[j], g0[j], b0[j])
                    if (l > bestL) { bestL = l; best = j }
                }
                val idx = by * bw + bx
                r1[idx] = r0[best]; g1[idx] = g0[best]; b1[idx] = b0[best]
            }
        }

        // Общий цвет бумаги: среднее по самым светлым 40% блоков. Ниже 60% от него фон не опускается.
        val order = (0 until nb).sortedBy { lum(r1[it], g1[it], b1[it]) }
        val from = (nb * 60) / 100
        var gr = 0f; var gg = 0f; var gb = 0f; var gc = 0
        for (k in from until nb) {
            val j = order[k]
            gr += r1[j]; gg += g1[j]; gb += b1[j]; gc++
        }
        if (gc == 0) { gr = 255f; gg = 255f; gb = 255f; gc = 1 }
        gr /= gc; gg /= gc; gb /= gc
        for (j in 0 until nb) {
            r1[j] = max(r1[j], gr * PAPER_FLOOR).coerceAtLeast(1f)
            g1[j] = max(g1[j], gg * PAPER_FLOOR).coerceAtLeast(1f)
            b1[j] = max(b1[j], gb * PAPER_FLOOR).coerceAtLeast(1f)
        }

        // Сглаживание 3×3, чтобы не было видимых «ступеней» между блоками.
        val r2 = FloatArray(nb)
        val g2 = FloatArray(nb)
        val b2 = FloatArray(nb)
        for (by in 0 until bh) {
            for (bx in 0 until bw) {
                var sr = 0f; var sg = 0f; var sb = 0f; var c = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val x = bx + dx
                    val y = by + dy
                    if (x < 0 || y < 0 || x >= bw || y >= bh) continue
                    val j = y * bw + x
                    sr += r1[j]; sg += g1[j]; sb += b1[j]; c++
                }
                val idx = by * bw + bx
                r2[idx] = sr / c; g2[idx] = sg / c; b2[idx] = sb / c
            }
        }
        return Grid(block, bw, bh, r2, g2, b2)
    }

    // ───────────────────────── Работа с масками ─────────────────────────

    /** Расширение маски на [radius] пикселей (квадратное окно, два прохода). */
    private fun dilate(mask: BooleanArray, w: Int, h: Int, radius: Int): BooleanArray {
        val tmp = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var v = false
                val from = max(0, x - radius)
                val to = min(w - 1, x + radius)
                var k = from
                while (k <= to) {
                    if (mask[y * w + k]) { v = true; break }
                    k++
                }
                tmp[y * w + x] = v
            }
        }
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val from = max(0, y - radius)
            val to = min(h - 1, y + radius)
            for (x in 0 until w) {
                var v = false
                var k = from
                while (k <= to) {
                    if (tmp[k * w + x]) { v = true; break }
                    k++
                }
                out[y * w + x] = v
            }
        }
        return out
    }

    /** Заливка от границ кадра по клеткам, не занятым [barrier]. */
    private fun floodFromBorder(barrier: BooleanArray, w: Int, h: Int): BooleanArray {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun push(x: Int, y: Int) {
            val i = y * w + x
            if (!barrier[i] && !seen[i]) {
                seen[i] = true
                queue[tail++] = i
            }
        }
        for (x in 0 until w) { push(x, 0); push(x, h - 1) }
        for (y in 0 until h) { push(0, y); push(w - 1, y) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) push(x - 1, y)
            if (x < w - 1) push(x + 1, y)
            if (y > 0) push(x, y - 1)
            if (y < h - 1) push(x, y + 1)
        }
        return seen
    }

    /** Оставляет крупные связные части маски, выбрасывая пятнышки и пыль. */
    private fun keepLargeParts(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val label = IntArray(w * h)          // 0 — не размечено
        val sizes = ArrayList<Int>()
        val queue = IntArray(w * h)
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            next++
            var head = 0
            var tail = 0
            label[start] = next
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (x > 0 && mask[i - 1] && label[i - 1] == 0) { label[i - 1] = next; queue[tail++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && label[i + 1] == 0) { label[i + 1] = next; queue[tail++] = i + 1 }
                if (y > 0 && mask[i - w] && label[i - w] == 0) { label[i - w] = next; queue[tail++] = i - w }
                if (y < h - 1 && mask[i + w] && label[i + w] == 0) { label[i + w] = next; queue[tail++] = i + w }
            }
            sizes.add(tail)
        }
        if (sizes.isEmpty()) return BooleanArray(w * h)
        val biggest = sizes.max()
        val minSize = max((w * h * 0.0008).toInt(), (biggest * 0.06).toInt())
        val out = BooleanArray(w * h)
        for (i in mask.indices) {
            val l = label[i]
            if (l != 0 && sizes[l - 1] >= minSize) out[i] = true
        }
        return out
    }
}
