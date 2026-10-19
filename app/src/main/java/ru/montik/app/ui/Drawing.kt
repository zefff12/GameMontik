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
private val ART_SMALL = Rect(6f / 250f, 12f / 336f, (6f + 238f) / 250f, (12f + 289f) / 336f)
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

        // Вкладки «Цвета» и «Палитра».
        if (tab == 1) {
            Box(
                Modifier.at(283f, 166f, 58f, 36f).clip(RoundedCornerShape(d(18f))).background(Color.White)
                    .border(d(1.5f), TileBorder, RoundedCornerShape(d(18f))),
                contentAlignment = Alignment.Center
            ) { Text("Цвета", color = Navy, fontSize = fs(12f, false), fontWeight = FontWeight.Bold) }
            Box(
                Modifier.at(344f, 166f, 57f, 36f).clip(RoundedCornerShape(d(18f)))
                    .background(Brush.verticalGradient(listOf(TileTop, TileBottom))),
                contentAlignment = Alignment.Center
            ) { Text("Палитра", color = Color.White, fontSize = fs(11f, false), fontWeight = FontWeight.Bold) }
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
        Box(Modifier.at(13f, 573f, 262f, 83f).background(PanelBg))
        ToolTile(17f, "Кисть", "fg_paint_ic_brush", tool == PaintTool.BRUSH) { tool = PaintTool.BRUSH }
        ToolTile(82f, "Маркер", "fg_paint_ic_marker", tool == PaintTool.MARKER) { tool = PaintTool.MARKER }
        ToolTile(146f, "Заливка", "fg_paint_ic_fill", tool == PaintTool.FILL) { tool = PaintTool.FILL }
        ToolTile(211f, "Ластик", "fg_paint_ic_eraser", tool == PaintTool.ERASER) { tool = PaintTool.ERASER }

        // Размер кисти: три точки, выбранная — в голубом кольце.
        Box(Modifier.at(279f, 573f, 121f, 83f).background(PanelBg))
        Box(
            Modifier.at(284f, 582f, 112f, 72f).shadow(d(1f), RoundedCornerShape(d(14f)))
                .clip(RoundedCornerShape(d(14f))).background(Color.White)
                .border(d(1.5f), TileBorder, RoundedCornerShape(d(14f)))
        )
        DText("Размер", 284f, 588f, 112f, 12f, color = Navy, align = TextAlign.Center, softWrap = false, mono = false)
        listOf(302f to 6f, 338f to 10f, 374f to 16f).forEachIndexed { i, (cx, dot) ->
            val ring by animateFloatAsState(if (brush == i) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "size")
            Box(
                Modifier.at(cx - 14f, 608f, 28f, 28f)
                    .graphicsLayer { alpha = ring; scaleX = 0.6f + 0.4f * ring; scaleY = 0.6f + 0.4f * ring }
                    .clip(CircleShape).background(Color(0xFFDDF1FF)).border(d(2f), PaintBlue, CircleShape)
            )
            Box(Modifier.at(cx - dot / 2f, 622f - dot / 2f, dot, dot).clip(CircleShape).background(Navy))
            PressHit(cx - 17f, 604f, 34f, 36f, 17f) {
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
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.8f else 1f,
        tween(if (pressed) 60 else 200),
        label = "tool"
    )
    val shape = RoundedCornerShape(d(12f))
    Box(
        Modifier.at(x, 579f, 58f, 72f)
            .graphicsLayer { alpha = scale }
            .shadow(if (selected) d(4f) else d(1f), shape)
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(TileTop, TileBottom))
                else Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF9FF)))
            )
            .border(d(1.5f), if (selected) Color(0xFF1F7FD0) else TileBorder, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(d(3f)))
            ArtImage(icon, Modifier.size(d(46f)), ContentScale.Fit) {}
            Text(
                label,
                color = if (selected) Color.White else Navy,
                fontSize = fs(10.5f, false),
                fontWeight = FontWeight.Bold,
                maxLines = 1
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
