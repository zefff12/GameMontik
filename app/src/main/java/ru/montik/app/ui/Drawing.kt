package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import ru.montik.app.GameViewModel
import ru.montik.app.data.DrawStroke
import ru.montik.app.data.PaintArt
import ru.montik.app.data.PaintFill
import ru.montik.app.game.Hero

/*
 * «Нарисуй Монтика» — по макету Figma: кадр «Android Compact - 80» (экран) и «Android Compact - 81»
 * (расширенный холст). Кадр лежит фоном один в один, а поверх него — живая раскраска: обводка Монтика
 * из макета всегда сверху, мазки и заливки обрезаются по его силуэту. Вкладки, цвета, инструменты,
 * размер кисти, отмена/повтор и кнопки работают и подсвечиваются при нажатии.
 * Координаты — в единицах кадра 412×917, сняты с картинки макета.
 */

private enum class PaintTool { BRUSH, MARKER, FILL, ERASER }

/** Цвета вкладки «Цвета» — сняты с кружков кадра (по три в ряд, сверху вниз). 21-й кружок — радуга. */
private val FIGMA_COLOURS: List<Int> = listOf(
    0xFFFDDB07, 0xFFFD9505, 0xFFFC1B23,
    0xFFFD71B6, 0xFFE445E9, 0xFF9C42FD,
    0xFFB58DF6, 0xFF4727B1, 0xFF1B30B4,
    0xFF37D4FE, 0xFF2AA3FE, 0xFF02D5D3,
    0xFF6ED74B, 0xFF148A1F, 0xFF02693C,
    0xFFF8ECDC, 0xFFB05025, 0xFF512C20,
    0xFF9BA4AD, 0xFF131311
).map { it.toInt() }

/** Вкладка «Палитра» — светлые оттенки тех же цветов. */
private val PASTEL_COLOURS: List<Int> = FIGMA_COLOURS.map { lighten(it, 0.5f) }

private val COL_X = floatArrayOf(297.5f, 338.5f, 379.5f)
private val ROW_Y = floatArrayOf(239f, 286f, 332.5f, 379.5f, 426.5f, 474f, 521f)
private const val DOT = 35f

/** Цвет обводки Монтика — тёмно-серый, как в макете. */
private val LineTint = ColorFilter.tint(Color(0xFF4F4F55))

private val PanelBg = Color(0xFFD2F2FD)
private val Navy = Color(0xFF042366)
private val PaintBlue = Color(0xFF3BA8E8)
private val TileTop = Color(0xFF5CBEF9)
private val TileBottom = Color(0xFF2A93E8)
private val TileBorder = Color(0xFFB9E3FA)
private val RainbowColours = listOf(
    Color(0xFFFF3B3B), Color(0xFFFFB020), Color(0xFFFFF03A), Color(0xFF4CD964),
    Color(0xFF2FA8FF), Color(0xFF7B5CFF), Color(0xFFFF4FD8), Color(0xFFFF3B3B)
)

/** Рамка обводки внутри холста, в долях холста: обычный экран (холст 250×336) и расширенный (375×727). */
// Обводка целиком внутри холста, по центру, с полями и в своих пропорциях (820×943).
private val ART_SMALL = Rect(25f / 250f, 44f / 336f, (25f + 200f) / 250f, (44f + 230f) / 336f)
private val ART_BIG = Rect(8f / 375f, 160f / 727f, (8f + 354f) / 375f, (160f + 426f) / 727f)

@Composable
fun DrawingScreen(vm: GameViewModel) {
    var colour by rememberSaveable { mutableStateOf(FIGMA_COLOURS[0]) }
    var rainbow by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(PaintTool.BRUSH) }
    var brush by rememberSaveable { mutableStateOf(2) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var empty by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    val context = LocalContext.current
    val art = remember { PaintArt.load(context) }

    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color.White)) }) {
        if (expanded) {
            // Кадр AC81: холст на весь экран, назад — круглая стрелка.
            FrameArt("fg_figma_paint_full")
            Box(Modifier.at(19f, 107f, 375f, 727f)) {
                PaintBoard(vm, art, ART_BIG, tool, colour, rainbow, brush) { empty = false }
            }
            PressHit(14f, 33f, 54f, 53f, 27f) { expanded = false }
            return@DesignCanvas
        }

        FrameArt("fg_figma_paint")

        // Шапка: круглая стрелка назад.
        PressHit(13f, 94f, 65f, 66f, 33f) { vm.cancelDrawing() }

        // Холст: живая раскраска поверх кадра.
        Box(Modifier.at(15f, 214f, 250f, 336f)) {
            PaintBoard(vm, art, ART_SMALL, tool, colour, rainbow, brush) { empty = false }
        }
        PressHit(16f, 168f, 46f, 45f, 10f) { vm.undoStroke() }
        if (vm.canRedo) {
            // Повтор доступен: серая стрелка в кадре становится тёмной (отражённая стрелка отмены).
            ArtImage("fg_paint_ic_undo", Modifier.at(74f, 175f, 37f, 37f).graphicsLayer { scaleX = -1f }, ContentScale.Fit) {}
        }
        PressHit(72f, 173f, 41f, 40f, 10f) { vm.redoStroke() }
        PressHit(227f, 168f, 38f, 39f, 10f) { expanded = true }

        // Вкладки «Цвета» и «Палитра»: рисуем обе сами, ровно и одинаковыми, выбранная — синяя.
        Box(Modifier.at(277f, 161f, 122f, 48f).background(Color(0xFFDDF4FD)))
        listOf(Triple(281f, "Цвета", 0), Triple(341f, "Палитра", 1)).forEach { (tx, label, t) ->
            val on = tab == t
            Box(
                Modifier.at(tx, 167f, 56f, 34f).shadow(d(if (on) 3f else 1f), RoundedCornerShape(d(17f)))
                    .clip(RoundedCornerShape(d(17f)))
                    .background(if (on) Brush.verticalGradient(listOf(TileTop, TileBottom)) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1FAFF))))
                    .border(d(1.5f), if (on) Color(0xFF1F7FD0) else TileBorder, RoundedCornerShape(d(17f))),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) Color.White else Navy, fontSize = fs(11.5f, false), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        PressHit(282f, 165f, 60f, 38f, 19f) { tab = 0 }
        PressHit(344f, 165f, 57f, 40f, 19f) { tab = 1 }

        // Цвета: на вкладке «Цвета» — кружки из кадра, на «Палитре» — светлые оттенки поверх них.
        val colours = if (tab == 0) FIGMA_COLOURS else PASTEL_COLOURS
        if (tab == 1) {
            Box(Modifier.at(280f, 216f, 121f, 328f).background(PanelBg))
            colours.forEachIndexed { i, c -> GlossyDot(COL_X[i % 3], ROW_Y[i / 3], Color(c)) }
            RainbowDot(COL_X[2], ROW_Y[6])
        }
        for (i in 0..20) {
            val cx = COL_X[i % 3]
            val cy = ROW_Y[i / 3]
            val isRainbow = i == 20
            val selected = if (isRainbow) rainbow else !rainbow && colours[i] == colour
            SelectionRing(cx, cy, selected)
            PressHit(cx - DOT / 2f, cy - DOT / 2f, DOT, DOT, DOT / 2f) {
                if (isRainbow) rainbow = true else { colour = colours[i]; rainbow = false }
                if (tool == PaintTool.ERASER) tool = PaintTool.BRUSH
            }
        }

        // Инструменты: кисть, маркер, заливка, ластик — со своей подсветкой выбранного.
        // Панель инструментов — точно по макету: голубая рамка 9…402 × 567…661, четыре плитки и «Размер».
        Box(
            Modifier.at(7f, 565f, 397f, 98f).clip(RoundedCornerShape(d(16f))).background(Color.White)
        )
        Box(
            Modifier.at(9f, 567f, 393f, 94f).clip(RoundedCornerShape(d(14f))).background(Color(0xFFD2F2FE))
                .border(d(2f), Color(0xFF7FCDE4), RoundedCornerShape(d(14f)))
        )
        val toolX = floatArrayOf(17f, 83.5f, 150f, 216.5f)
        ToolTile(toolX[0], "Кисть", "fg_paint_ic_brush", tool == PaintTool.BRUSH) { tool = PaintTool.BRUSH }
        ToolTile(toolX[1], "Маркер", "fg_paint_ic_marker", tool == PaintTool.MARKER) { tool = PaintTool.MARKER }
        ToolTile(toolX[2], "Заливка", "fg_paint_ic_fill", tool == PaintTool.FILL) { tool = PaintTool.FILL }
        ToolTile(toolX[3], "Ластик", "fg_paint_ic_eraser", tool == PaintTool.ERASER) { tool = PaintTool.ERASER }

        // Размер кисти: белая карточка, три кружка, выбранный — в голубом кольце.
        Box(
            Modifier.at(282f, 578f, 110f, 72f).clip(RoundedCornerShape(d(12f))).background(Color(0xFFF2FCFF))
                .border(d(1.5f), Color(0xFFD3ECF8), RoundedCornerShape(d(12f)))
        )
        DText("Размер", 282f, 585f, 110f, 12.5f, color = Navy, align = TextAlign.Center, softWrap = false, mono = false)
        listOf(302f to 6f, 337f to 10f, 372f to 15f).forEachIndexed { i, (cx, dot) ->
            val on = brush == i
            Box(
                Modifier.at(cx - 13f, 611f, 26f, 26f).clip(CircleShape)
                    .background(if (on) Color(0xFFE3F4FF) else Color(0xFFF7FDFF))
                    .border(d(if (on) 2.2f else 1.2f), if (on) PaintBlue else Color(0xFFCFE8F4), CircleShape)
            )
            Box(Modifier.at(cx - dot / 2f, 624f - dot / 2f, dot, dot).clip(CircleShape).background(Navy))
            PressHit(cx - 17f, 603f, 34f, 42f, 17f) {
                brush = i
                if (tool == PaintTool.FILL) tool = PaintTool.BRUSH
            }
        }

        // Нижние кнопки из кадра: «Назад» и «Готово!».
        PressHit(15f, 672f, 185f, 59f, 29f) { vm.cancelDrawing() }
        PressHit(207f, 673f, 189f, 58f, 29f) { if (!vm.finishDrawing()) empty = true }
        if (empty) {
            DText(
                "Сначала раскрась Монтика — хотя бы пару линий!", 8f, 742f, 396f, 16f,
                color = MontikColors.Bad, align = TextAlign.Center, softWrap = false, mono = false
            )
        }
    }
}

/** Кольцо вокруг выбранного цвета: появляется с пружинкой. */
@Composable
private fun DesignScope.SelectionRing(cx: Float, cy: Float, selected: Boolean) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ring")
    val size = DOT + 8f
    Box(
        Modifier.at(cx - size / 2f, cy - size / 2f, size, size)
            .graphicsLayer { alpha = ring; scaleX = 0.7f + 0.3f * ring; scaleY = 0.7f + 0.3f * ring }
            .border(d(3f), Navy, CircleShape)
    )
}

/** Кружок цвета с бликом — как в макете. */
@Composable
private fun DesignScope.GlossyDot(cx: Float, cy: Float, colour: Color) {
    Box(
        Modifier.at(cx - DOT / 2f, cy - DOT / 2f, DOT, DOT).shadow(d(2f), CircleShape).clip(CircleShape)
            .background(colour)
            .border(d(1.5f), Color.Black.copy(alpha = 0.12f), CircleShape)
    )
    // Блик сверху слева.
    Box(Modifier.at(cx - 10f, cy - 12f, 11f, 7f).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
}

/** Радужный кружок: каждый мазок — новый цвет радуги. */
@Composable
private fun DesignScope.RainbowDot(cx: Float, cy: Float) {
    Box(
        Modifier.at(cx - DOT / 2f, cy - DOT / 2f, DOT, DOT).shadow(d(2f), CircleShape).clip(CircleShape)
            .background(Brush.sweepGradient(RainbowColours))
            .border(d(1.5f), Color.Black.copy(alpha = 0.12f), CircleShape)
    )
}

/** Плитка инструмента: выбранная — синяя, с белой подписью, и чуть больше; при нажатии сжимается. */
@Composable
private fun DesignScope.ToolTile(x: Float, label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    // Плитка как в макете: выбранная — синяя сверху и светлая полоска с синей подписью снизу, в синей рамке;
    // остальные — светлые, без рамки. Без «подпрыгивания».
    val shape = RoundedCornerShape(d(10f))
    Box(
        Modifier.at(x, 577f, 57f, 72f)
            .clip(shape)
            .background(if (selected) Color(0xFFDFF5FF) else Color(0xFFE9F8FF))
            .then(if (selected) Modifier.border(d(2f), Color(0xFF4FA3DA), shape) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        if (selected) {
            Box(
                Modifier.fillMaxWidth().height(d(51f))
                    .clip(RoundedCornerShape(topStart = d(10f), topEnd = d(10f), bottomStart = d(8f), bottomEnd = d(8f)))
                    .background(Brush.verticalGradient(listOf(Color(0xFF3AACFF), Color(0xFF2196F3))))
            )
        }
        ArtImage(icon, Modifier.padding(start = d(9.5f), top = d(6f)).size(d(38f)), ContentScale.Fit) {}
        Box(Modifier.fillMaxWidth().padding(top = d(51f)).height(d(19f)), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color(0xFF2B8FE3) else Navy,
                fontSize = fs(11f, false),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Холст раскраски. [artRect] — где на холсте лежит обводка (доли холста). Точки мазков хранятся
 * в долях этой рамки, поэтому рисунок не съезжает при переходе на расширенный холст.
 */
@Composable
private fun PaintBoard(
    vm: GameViewModel,
    art: PaintArt.Images?,
    artRect: Rect,
    tool: PaintTool,
    colour: Int,
    rainbow: Boolean,
    brush: Int,
    onDraw: () -> Unit
) {
    // Жест запускается один раз, а цвет и инструмент меняются: читаем их «свежими».
    val toolNow by rememberUpdatedState(tool)
    val colourNow by rememberUpdatedState(colour)
    val rainbowNow by rememberUpdatedState(rainbow)
    val brushNow by rememberUpdatedState(brush)
    val onDrawNow by rememberUpdatedState(onDraw)
    var hue by remember { mutableFloatStateOf(0f) }
    val lines = remember(art) { art?.lines?.asImageBitmap() }
    val base = remember(art) { art?.base?.asImageBitmap() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val aL = artRect.left * size.width
                    val aT = artRect.top * size.height
                    val aW = artRect.width * size.width
                    val aH = artRect.height * size.height
                    fun norm(p: Offset) = Offset(
                        ((p.x - aL) / aW).coerceIn(-0.3f, 1.3f),
                        ((p.y - aT) / aH).coerceIn(-0.3f, 1.3f)
                    )
                    val c = if (rainbowNow) {
                        hue = (hue + 47f) % 360f
                        Color.hsv(hue, 0.85f, 0.95f).toArgb()
                    } else colourNow

                    if (toolNow == PaintTool.FILL) {
                        val n = norm(down.position)
                        val images = art
                        if (images != null) {
                            PaintFill.flood(images, n.x, n.y)?.let {
                                vm.addStroke(DrawStroke(c, 0f, false, it))
                                onDrawNow()
                            }
                        }
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        return@awaitEachGesture
                    }

                    val baseWidth = Hero.BRUSH_SIZES[brushNow.coerceIn(0, Hero.BRUSH_SIZES.lastIndex)]
                    val width = when (toolNow) {
                        PaintTool.MARKER -> baseWidth * 1.9f
                        PaintTool.ERASER -> baseWidth * 1.8f
                        else -> baseWidth
                    }
                    // Маркер полупрозрачный: цвет с альфой 55%.
                    val strokeColour = if (toolNow == PaintTool.MARKER) (c and 0x00FFFFFF) or 0x8C000000.toInt() else c
                    val stroke = DrawStroke(strokeColour, width, toolNow == PaintTool.ERASER)
                    stroke.points.add(norm(down.position))
                    vm.addStroke(stroke)
                    onDrawNow()
                    down.consume()

                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            pressed = false
                        } else {
                            stroke.points.add(norm(change.position))
                            change.consume()
                        }
                    }
                }
            }
    ) {
        // Слой раскраски: мазки и заливки, обрезанные по силуэту Монтика.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val aL = artRect.left * size.width
            val aT = artRect.top * size.height
            val aW = artRect.width * size.width
            val aH = artRect.height * size.height
            val dstOffset = IntOffset(aL.roundToInt(), aT.roundToInt())
            val dstSize = IntSize(aW.roundToInt(), aH.roundToInt())
            for (stroke in vm.drawing) {
                val mask = stroke.fill
                if (mask != null) {
                    drawImage(
                        mask.asImageBitmap(), dstOffset = dstOffset, dstSize = dstSize,
                        colorFilter = ColorFilter.tint(Color(stroke.color)), filterQuality = FilterQuality.Medium
                    )
                    continue
                }
                drawStroke(stroke, aL, aT, aW, aH)
            }
            base?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize, blendMode = BlendMode.DstIn, filterQuality = FilterQuality.Medium) }
        }
        // Обводка из макета — всегда поверх раскраски.
        Canvas(Modifier.fillMaxSize()) {
            val dstOffset = IntOffset((artRect.left * size.width).roundToInt(), (artRect.top * size.height).roundToInt())
            val dstSize = IntSize((artRect.width * size.width).roundToInt(), (artRect.height * size.height).roundToInt())
            lines?.let { drawImage(it, dstOffset = dstOffset, dstSize = dstSize, colorFilter = LineTint, filterQuality = FilterQuality.High) }
        }
    }
}

/** Мазок в рамке обводки: точки — доли рамки, толщина — доля её ширины. */
private fun DrawScope.drawStroke(stroke: DrawStroke, aL: Float, aT: Float, aW: Float, aH: Float) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    val color = if (stroke.eraser) Color.Black else Color(stroke.color)
    val mode = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
    val w = stroke.width * aW
    fun px(i: Int) = Offset(aL + pts[i].x * aW, aT + pts[i].y * aH)
    if (pts.size == 1) {
        drawCircle(color, radius = w / 2f, center = px(0), blendMode = mode)
        return
    }
    val path = Path()
    val first = px(0)
    path.moveTo(first.x, first.y)
    for (i in 1 until pts.size) {
        val prev = px(i - 1)
        val cur = px(i)
        path.quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    val last = px(pts.lastIndex)
    path.lineTo(last.x, last.y)
    drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = mode)
}

/** Светлее на долю [t] (0 — тот же цвет, 1 — белый). */
private fun lighten(c: Int, t: Float): Int {
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val b = c and 0xFF
    fun mix(v: Int) = (v + (255 - v) * t).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (mix(r) shl 16) or (mix(g) shl 8) or mix(b)
}
