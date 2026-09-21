package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import ru.montik.app.game.GameState
import ru.montik.app.game.HeroPart
import kotlin.math.cos
import kotlin.math.sin

/** Превращает цвет вида 0xRRGGBB из игрового состояния в цвет для экрана. */
fun argbColor(rgb: Int): Color = Color(rgb or 0xFF000000.toInt())

/** Цвета героя: те, которыми ребёнок его раскрасил, либо цвета по умолчанию. */
class HeroPalette(private val colors: Map<HeroPart, Color>) {
    operator fun get(part: HeroPart): Color = colors[part] ?: argbColor(part.default)

    companion object {
        val Default = HeroPalette(HeroPart.values().associateWith { argbColor(it.default) })
    }
}

fun heroPalette(state: GameState): HeroPalette =
    HeroPalette(HeroPart.values().associateWith { argbColor(state.heroColor(it)) })

fun heroPalette(chosen: Map<HeroPart, Int>): HeroPalette =
    HeroPalette(HeroPart.values().associateWith { argbColor(chosen[it] ?: it.default) })

/**
 * Геометрия героя в долях от стороны квадрата (0..1). Один и тот же список используется
 * и для рисования, и чтобы понять, по какой части ребёнок ткнул пальцем на экране раскраски.
 */
private class Blob(
    val part: HeroPart,
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val rotation: Float = 0f,
    val pivotX: Float = 0f,
    val pivotY: Float = 0f,
    /** Оттенок основного цвета: больше нуля — светлее, меньше — темнее. */
    val shade: Float = 0f,
    /** Правая лапка: она машет на экране «Готов начать?». */
    val wavingArm: Boolean = false
) {
    fun contains(x: Float, y: Float): Boolean {
        var px = x
        var py = y
        if (rotation != 0f) {
            val a = (-rotation * Math.PI / 180.0).toFloat()
            val dx = x - pivotX
            val dy = y - pivotY
            px = pivotX + dx * cos(a) - dy * sin(a)
            py = pivotY + dx * sin(a) + dy * cos(a)
        }
        val nx = (px - cx) / rx
        val ny = (py - cy) / ry
        return nx * nx + ny * ny <= 1f
    }
}

/** Порядок рисования снизу вверх: нарисованное позже лежит выше и первым ловит палец. */
private val BODY: List<Blob> = listOf(
    Blob(HeroPart.PACK, 0.50f, 0.70f, 0.34f, 0.18f),
    Blob(HeroPart.EARS, 0.335f, 0.23f, 0.095f, 0.21f, -14f, 0.33f, 0.30f, shade = -0.10f),
    Blob(HeroPart.EARS, 0.335f, 0.225f, 0.05f, 0.145f, -14f, 0.33f, 0.30f, shade = 0.18f),
    Blob(HeroPart.EARS, 0.665f, 0.23f, 0.095f, 0.21f, 14f, 0.67f, 0.30f, shade = -0.10f),
    Blob(HeroPart.EARS, 0.665f, 0.225f, 0.05f, 0.145f, 14f, 0.67f, 0.30f, shade = 0.18f),
    Blob(HeroPart.FUR, 0.50f, 0.71f, 0.26f, 0.21f),
    Blob(HeroPart.BELLY, 0.50f, 0.74f, 0.16f, 0.14f),
    Blob(HeroPart.FUR, 0.36f, 0.91f, 0.09f, 0.05f, shade = -0.12f),
    Blob(HeroPart.FUR, 0.64f, 0.91f, 0.09f, 0.05f, shade = -0.12f),
    Blob(HeroPart.FUR, 0.225f, 0.69f, 0.065f, 0.11f, shade = -0.12f),
    Blob(HeroPart.FUR, 0.775f, 0.69f, 0.065f, 0.11f, shade = -0.12f, wavingArm = true),
    Blob(HeroPart.FUR, 0.50f, 0.46f, 0.32f, 0.24f),
    Blob(HeroPart.BELLY, 0.50f, 0.55f, 0.20f, 0.13f)
)

/** Корона: не эллипс, поэтому задана точками. */
private val CROWN_POINTS = listOf(
    0.42f to 0.19f, 0.42f to 0.128f, 0.46f to 0.156f,
    0.50f to 0.078f, 0.54f to 0.156f, 0.58f to 0.128f, 0.58f to 0.19f
)

/** По какой части героя пришёлся тап. Координаты — доли от стороны квадрата. */
fun heroPartAt(x: Float, y: Float): HeroPart? {
    if (y in 0.07f..0.20f && x in 0.41f..0.59f) return HeroPart.CROWN
    for (blob in BODY.asReversed()) {
        if (blob.contains(x, y)) return blob.part
    }
    return null
}

private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
    red = from.red + (to.red - from.red) * t,
    green = from.green + (to.green - from.green) * t,
    blue = from.blue + (to.blue - from.blue) * t,
    alpha = from.alpha
)

private fun Color.shaded(amount: Float): Color = when {
    amount > 0f -> lerpColor(this, Color.White, amount)
    amount < 0f -> lerpColor(this, Color.Black, -amount)
    else -> this
}

/**
 * Герой, нарисованный кодом, в цветах ребёнка.
 * Используется на вступительных экранах, на экране раскраски и везде, где нет фотографии рисунка.
 */
@Composable
fun MontikBunny(
    modifier: Modifier = Modifier,
    waving: Boolean = false,
    palette: HeroPalette = HeroPalette.Default,
    outlined: Boolean = true
) {
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        fun x(f: Float) = ox + s * f
        fun y(f: Float) = oy + s * f

        val ink = Color(0xFF2A1E1E)
        val line = Stroke(width = s * 0.012f)

        for (blob in BODY) {
            val color = palette[blob.part].shaded(blob.shade)
            val topLeft = Offset(x(blob.cx - blob.rx), y(blob.cy - blob.ry))
            val blobSize = Size(s * blob.rx * 2f, s * blob.ry * 2f)
            val turn = when {
                waving && blob.wavingArm -> 25f
                else -> blob.rotation
            }
            val pivot = if (waving && blob.wavingArm) {
                Offset(x(0.78f), y(0.62f))
            } else {
                Offset(x(blob.pivotX), y(blob.pivotY))
            }
            if (turn != 0f) {
                rotate(turn, pivot = pivot) {
                    drawOval(color, topLeft, blobSize)
                    if (outlined) drawOval(ink, topLeft, blobSize, style = line)
                }
            } else {
                drawOval(color, topLeft, blobSize)
                if (outlined) drawOval(ink, topLeft, blobSize, style = line)
            }
        }

        // Щёчки.
        val cheek = palette[HeroPart.EARS].shaded(0.1f)
        drawCircle(cheek.copy(alpha = 0.75f), radius = s * 0.045f, center = Offset(x(0.27f), y(0.52f)))
        drawCircle(cheek.copy(alpha = 0.75f), radius = s * 0.045f, center = Offset(x(0.73f), y(0.52f)))

        // Глаза.
        for (fx in listOf(0.38f, 0.62f)) {
            drawCircle(ink, radius = s * 0.062f, center = Offset(x(fx), y(0.42f)))
            drawCircle(Color.White, radius = s * 0.02f, center = Offset(x(fx + 0.018f), y(0.40f)))
            drawCircle(Color.White.copy(alpha = 0.8f), radius = s * 0.01f, center = Offset(x(fx - 0.015f), y(0.445f)))
        }

        // Нос и улыбка.
        drawOval(ink, Offset(x(0.47f), y(0.49f)), Size(s * 0.06f, s * 0.04f))
        drawArc(
            color = ink,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(x(0.44f), y(0.50f)),
            size = Size(s * 0.12f, s * 0.08f),
            style = Stroke(width = s * 0.012f, cap = StrokeCap.Round)
        )

        // Лямки рюкзака.
        val strap = palette[HeroPart.PACK].shaded(0.18f)
        drawRect(strap, Offset(x(0.30f), y(0.56f)), Size(s * 0.05f, s * 0.20f))
        drawRect(strap, Offset(x(0.65f), y(0.56f)), Size(s * 0.05f, s * 0.20f))

        // Корона между ушами.
        val crown = Path().apply {
            CROWN_POINTS.forEachIndexed { i, point ->
                if (i == 0) moveTo(x(point.first), y(point.second)) else lineTo(x(point.first), y(point.second))
            }
            close()
        }
        drawPath(crown, palette[HeroPart.CROWN])
        if (outlined) drawPath(crown, ink, style = line)
    }
}
