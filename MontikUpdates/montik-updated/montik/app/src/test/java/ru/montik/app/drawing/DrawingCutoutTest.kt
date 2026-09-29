package ru.montik.app.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Проверки вырезания рисунка на искусственных «фотографиях» листа. */
class DrawingCutoutTest {

    private val size = 240

    /** Лист с неравномерным освещением: слева светлее, справа темнее. */
    private fun paper(noise: Int = 3, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(size * size) { i ->
            val x = i % size
            val base = 240 - x / 6 + (if (noise > 0) rnd.nextInt(-noise, noise + 1) else 0)
            rgb(base, base, base - 6)
        }
    }

    private fun rgb(r: Int, g: Int, b: Int) =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Кружок-контур с белой серединой и красным «животиком». [gapDegrees] — разрыв в линии. */
    private fun drawMontik(img: IntArray, gapDegrees: Int = 0) {
        val cx = 120.0
        val cy = 120.0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                val onLine = abs(d - 60.0) <= 2.0
                if (onLine) {
                    val angle = Math.toDegrees(Math.atan2(y - cy, x - cx))
                    val inGap = gapDegrees > 0 && angle > -gapDegrees / 2.0 && angle < gapDegrees / 2.0
                    if (!inGap) img[y * size + x] = rgb(40, 40, 45)
                }
                if (x in 105..135 && y in 105..135) img[y * size + x] = rgb(225, 60, 60)
            }
        }
    }

    private fun alphaAt(r: CutoutResult.Success, srcX: Int, srcY: Int, minX: Int, minY: Int): Int {
        val x = srcX - minX
        val y = srcY - minY
        if (x < 0 || y < 0 || x >= r.width || y >= r.height) return 0
        return (r.pixels[y * r.width + x] ushr 24) and 0xFF
    }

    @Test
    fun cutsOutOutlinedDrawingAndKeepsWhiteInterior() {
        val img = paper()
        drawMontik(img)
        val result = DrawingCutout.cutout(img, size, size)
        assertTrue(result is CutoutResult.Success)
        result as CutoutResult.Success
        // Круг радиусом 60 (+линия): ширина и высота около 124 + поля.
        assertTrue("ширина ${result.width}", result.width in 120..140)
        assertTrue("высота ${result.height}", result.height in 120..140)
        val minX = 120 - result.width / 2
        val minY = 120 - result.height / 2
        // Центр (красный) и белая часть внутри контура непрозрачны.
        assertEquals(255, alphaAt(result, 120, 120, minX, minY))
        assertEquals(255, alphaAt(result, 120, 75, minX, minY))
        // Бумага внутри контура «отбелена» — тень и неровный свет убраны.
        val inner = result.pixels[(75 - minY) * result.width + (120 - minX)]
        assertTrue("цвет бумаги внутри слишком тёмный", ((inner shr 16) and 0xFF) >= 235)
    }

    @Test
    fun paperBackgroundBecomesTransparent() {
        val img = paper()
        drawMontik(img)
        val result = DrawingCutout.cutout(img, size, size) as CutoutResult.Success
        var opaque = 0
        for (p in result.pixels) if ((p ushr 24) > 200) opaque++
        // Прозрачным в углах остаётся всё, что вне круга: непрозрачных заметно меньше, чем площадь прямоугольника.
        assertTrue(opaque < result.width * result.height * 0.9)
        assertEquals(0, result.pixels[0] ushr 24)   // левый верхний угол вне круга
    }

    @Test
    fun smallGapInOutlineDoesNotEmptyTheInside() {
        val img = paper()
        drawMontik(img, gapDegrees = 4)               // разрыв в линии около 4 px
        val result = DrawingCutout.cutout(img, size, size)
        assertTrue(result is CutoutResult.Success)
        result as CutoutResult.Success
        val minX = 120 - result.width / 2
        val minY = 120 - result.height / 2
        assertEquals(255, alphaAt(result, 120, 75, minX, minY))
    }

    @Test
    fun dustSpecksAreIgnored() {
        val img = paper()
        drawMontik(img)
        val clean = DrawingCutout.cutout(img.copyOf(), size, size) as CutoutResult.Success
        for ((x, y) in listOf(5 to 5, 230 to 12, 10 to 225, 228 to 228, 30 to 200)) {
            img[y * size + x] = rgb(20, 20, 20)
            img[y * size + x + 1] = rgb(20, 20, 20)
        }
        val dusty = DrawingCutout.cutout(img, size, size) as CutoutResult.Success
        assertEquals(clean.width, dusty.width)
        assertEquals(clean.height, dusty.height)
    }

    @Test
    fun blankPaperGivesFailure() {
        val result = DrawingCutout.cutout(paper(noise = 4), size, size)
        assertTrue(result is CutoutResult.Failure)
        assertEquals(Reason.EMPTY, (result as CutoutResult.Failure).reason)
    }

    @Test
    fun filledColorfulBlobIsFound() {
        val img = paper()
        for (y in 60..180) for (x in 70..170) {
            val dx = x - 120
            val dy = y - 120
            if (dx * dx + dy * dy < 55 * 55) img[y * size + x] = rgb(250, 200, 30)   // жёлтая заливка
        }
        val result = DrawingCutout.cutout(img, size, size)
        assertTrue(result is CutoutResult.Success)
        result as CutoutResult.Success
        assertTrue("ширина ${result.width}", result.width in 100..125)
    }
}
