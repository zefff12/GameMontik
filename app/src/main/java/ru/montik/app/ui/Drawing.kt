package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ru.montik.app.GameViewModel
import ru.montik.app.data.DrawStroke
import ru.montik.app.game.Hero
import ru.montik.app.game.HeroPreset

/*
 * «Нарисуй Монтика»: свободное рисование пальцем на белом холсте. Бледный контур белого человечка
 * помогает не выйти за края (его можно выключить). Готовый рисунок обрезается по краям и становится
 * героем игры — так же, как фотография рисунка с бумаги.
 */

/** Полноэкранный экран рисования. Прокрутки здесь нет: движение пальцем целиком идёт на рисование. */
@Composable
fun DrawingScreen(vm: GameViewModel) {
    var colour by rememberSaveable { mutableStateOf(Hero.DRAW_PALETTE[3]) }
    var brush by rememberSaveable { mutableStateOf(1) }
    var eraser by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf(true) }
    var empty by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(MontikColors.Surface),
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Холст квадратный: справа от него две колонки цветов, снизу инструменты и кнопки.
            val side: Dp = minOf(maxWidth - PALETTE_WIDTH - 8.dp, maxHeight - 270.dp).coerceAtLeast(160.dp)

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Нарисуй Монтика",
                    style = MaterialTheme.typography.titleLarge,
                    color = MontikColors.Ink
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    DrawBoard(
                        vm = vm,
                        side = side,
                        colour = colour,
                        brush = brush,
                        eraser = eraser,
                        guide = guide,
                        onDraw = { empty = false }
                    )
                    Spacer(Modifier.size(8.dp))
                    ColourGrid(colour, !eraser) {
                        colour = it
                        eraser = false
                    }
                }

                Spacer(Modifier.height(10.dp))
                ToolRow(
                    brush = brush,
                    eraser = eraser,
                    guide = guide,
                    canUndo = vm.drawing.isNotEmpty(),
                    onBrush = {
                        brush = it
                        eraser = false
                    },
                    onEraser = { eraser = !eraser },
                    onUndo = { vm.undoStroke() },
                    onClear = { vm.clearDrawing() },
                    onGuide = { guide = !guide }
                )

                Spacer(Modifier.weight(1f))
                if (empty) {
                    Text(
                        "Сначала нарисуй Монтика — хотя бы пару линий!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.Bad,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                }
                CompactPill("Готово!", MontikColors.Lime) {
                    if (!vm.finishDrawing()) empty = true
                }
                Spacer(Modifier.height(8.dp))
                CompactPill("Назад", Color.White, outlined = true) { vm.cancelDrawing() }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private val PALETTE_WIDTH = 96.dp

/** Квадратный холст: контур-подсказка внизу, мазки сверху. */
@Composable
private fun DrawBoard(
    vm: GameViewModel,
    side: Dp,
    colour: Int,
    brush: Int,
    eraser: Boolean,
    guide: Boolean,
    onDraw: () -> Unit
) {
    // Жест запускается один раз, а цвет и кисть меняются: читаем их «свежими».
    val colourNow by rememberUpdatedState(colour)
    val brushNow by rememberUpdatedState(brush)
    val eraserNow by rememberUpdatedState(eraser)
    val onDrawNow by rememberUpdatedState(onDraw)

    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .size(side)
            .clip(shape)
            .background(Color.White)
            .border(2.dp, MontikColors.Ink, shape)
    ) {
        if (guide) {
            MontikBunny(
                modifier = Modifier.fillMaxSize().padding(8.dp).alpha(0.28f),
                palette = heroPalette(HeroPreset.WHITE)
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                // Отдельный слой: ластик стирает только мазки, а не белую бумагу под ними.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        fun norm(p: Offset) = Offset((p.x / w).coerceIn(0f, 1f), (p.y / h).coerceIn(0f, 1f))

                        val width = Hero.BRUSH_SIZES[brushNow.coerceIn(0, Hero.BRUSH_SIZES.lastIndex)] *
                            (if (eraserNow) 1.8f else 1f)
                        val stroke = DrawStroke(colourNow, width, eraserNow)
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
            val s = size.minDimension
            for (stroke in vm.drawing) {
                val pts = stroke.points
                if (pts.isEmpty()) continue
                val color = if (stroke.eraser) Color.Black else argbColor(stroke.color)
                val mode = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
                val w = stroke.width * s
                if (pts.size == 1) {
                    drawCircle(color, radius = w / 2f, center = Offset(pts[0].x * s, pts[0].y * s), blendMode = mode)
                } else {
                    val path = Path()
                    path.moveTo(pts[0].x * s, pts[0].y * s)
                    for (i in 1 until pts.size) {
                        val prev = pts[i - 1]
                        val cur = pts[i]
                        path.quadraticBezierTo(
                            prev.x * s, prev.y * s,
                            (prev.x + cur.x) / 2f * s, (prev.y + cur.y) / 2f * s
                        )
                    }
                    path.lineTo(pts.last().x * s, pts.last().y * s)
                    drawPath(
                        path,
                        color,
                        style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = mode
                    )
                }
            }
        }
    }
}

/** Двенадцать цветов в две колонки: семь из макета и ещё пять, чтобы можно было нарисовать кого угодно. */
@Composable
private fun ColourGrid(selected: Int, active: Boolean, onPick: (Int) -> Unit) {
    val colours = Hero.DRAW_PALETTE
    val half = (colours.size + 1) / 2
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (column in listOf(colours.take(half), colours.drop(half))) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (rgb in column) {
                    val chosen = active && rgb == selected
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(argbColor(rgb))
                            .border(
                                width = if (chosen) 4.dp else 2.dp,
                                color = if (chosen) MontikColors.LimeDeep else MontikColors.Ink,
                                shape = CircleShape
                            )
                            .clickable { onPick(rgb) }
                    )
                }
            }
        }
    }
}

/** Кисти трёх размеров, ластик, отмена, очистка и подсказка. */
@Composable
private fun ToolRow(
    brush: Int,
    eraser: Boolean,
    guide: Boolean,
    canUndo: Boolean,
    onBrush: (Int) -> Unit,
    onEraser: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onGuide: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dots = listOf(8.dp, 14.dp, 22.dp)
        dots.forEachIndexed { index, dot ->
            ToolButton(selected = !eraser && brush == index, onClick = { onBrush(index) }) {
                Box(Modifier.size(dot).clip(CircleShape).background(MontikColors.Ink))
            }
        }
        ToolButton(selected = eraser, onClick = onEraser) { Text("🧽", style = MaterialTheme.typography.titleMedium) }
        ToolButton(selected = false, enabled = canUndo, onClick = onUndo) { Text("↶", style = MaterialTheme.typography.titleLarge) }
        ToolButton(selected = false, enabled = canUndo, onClick = onClear) { Text("🗑", style = MaterialTheme.typography.titleMedium) }
        ToolButton(selected = guide, onClick = onGuide) { Text("👻", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (selected) MontikColors.Lime else Color.White)
            .border(if (selected) 3.dp else 2.dp, MontikColors.Ink, CircleShape)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Кнопка-пилюля пониже стандартной: на экране рисования нужно место под холст. */
@Composable
private fun CompactPill(
    text: String,
    color: Color,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = MontikSizes.ButtonMaxWidth)
            .fillMaxWidth()
            .height(52.dp)
            .clip(MontikShapes.Button)
            .clickable(onClick = onClick),
        shape = MontikShapes.Button,
        color = color,
        border = if (outlined) androidx.compose.foundation.BorderStroke(2.dp, MontikColors.Ink) else null,
        shadowElevation = if (outlined) 0.dp else 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink)
        }
    }
}
