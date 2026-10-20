package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import ru.montik.app.GameViewModel

/*
 * Знакомство с игрой (кадры «Обучение пользователя» в макете): экран затемняется, а то, о чём
 * сейчас рассказывают, остаётся светлым и подсвечено. Плашка «Знакомство N из 7» — картинка из макета,
 * «Дальше» — следующий шаг, «Пропустить» — закончить знакомство.
 */

/** Шаг знакомства: плашка, её рамка в макете, зоны «Дальше»/«Пропустить» (доли картинки), подсвеченные места. */
private class TourStep(
    val art: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val next: FloatArray,
    val skip: FloatArray,
    val spots: List<FloatArray>
)

private val WIDE_NEXT = floatArrayOf(0.14f, 0.58f, 0.68f, 0.24f)
private val WIDE_SKIP = floatArrayOf(0.25f, 0.82f, 0.45f, 0.13f)
private val LONG_NEXT = floatArrayOf(0.17f, 0.64f, 0.64f, 0.25f)
private val LONG_SKIP = floatArrayOf(0.28f, 0.89f, 0.40f, 0.10f)

private val TOUR = listOf(
    TourStep("fg5_t1", 27f, 314f, 357f, 238f, WIDE_NEXT, WIDE_SKIP, emptyList()),
    TourStep("fg5_t2", 20f, 288f, 371f, 247f, WIDE_NEXT, WIDE_SKIP, listOf(floatArrayOf(219f, 31f, 191f, 60f, 30f))),
    TourStep("fg5_t3", 13f, 302f, 385f, 257f, WIDE_NEXT, WIDE_SKIP, listOf(floatArrayOf(62f, 662f, 290f, 102f, 50f))),
    TourStep("fg5_t4", 6f, 346f, 400f, 200f, LONG_NEXT, LONG_SKIP, listOf(floatArrayOf(292f, 182f, 104f, 112f, 50f))),
    TourStep("fg5_t5", 25f, 296f, 362f, 241f, WIDE_NEXT, WIDE_SKIP, listOf(floatArrayOf(10f, 184f, 92f, 96f, 18f))),
    TourStep(
        "fg5_t6", 23f, 149f, 366f, 183f, LONG_NEXT, LONG_SKIP,
        listOf(floatArrayOf(292f, 392f, 104f, 114f, 50f), floatArrayOf(291f, 758f, 112f, 44f, 22f))
    ),
    TourStep("fg5_t7", 23f, 318f, 366f, 183f, LONG_NEXT, LONG_SKIP, emptyList())
)

/** Сколько шагов в знакомстве (для проверки в App.kt). */
internal val TOUR_STEPS: List<Int> = List(TOUR.size) { it }

@Composable
fun FirstRunTour(vm: GameViewModel) {
    val index = vm.state.tourStep.coerceIn(0, TOUR.lastIndex)
    val step = TOUR[index]
    val last = index == TOUR.lastIndex
    BackHandler(enabled = true) { vm.finishTour() }
    val appear = remember(index) { Animatable(0f) }
    LaunchedEffect(index) { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }

    DesignCanvas {
        // Затемнение на весь экран с «окошками» над тем, о чём рассказываем.
        val spots = step.spots
        Box(
            Modifier.absoluteOffset(d(-bleed), d(-padTop))
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(d(DESIGN_W + 2f * bleed), d(DESIGN_H + padTop + padBottom))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val k = size.width / (DESIGN_W + 2f * bleed)
                drawRect(Color.Black.copy(alpha = 0.62f))
                for (s in spots) {
                    val tl = Offset((s[0] + bleed) * k, (s[1] + padTop) * k)
                    val sz = Size(s[2] * k, s[3] * k)
                    drawRoundRect(Color.Black, tl, sz, CornerRadius(s[4] * k), blendMode = BlendMode.Clear)
                }
                // Мягкое свечение вокруг подсвеченного.
                val a = appear.value
                for (s in spots) {
                    for ((grow, alpha) in listOf(3f to 0.85f, 7f to 0.35f, 11f to 0.15f)) {
                        val g = grow * k
                        drawRoundRect(
                            Color.White.copy(alpha = alpha * a),
                            Offset((s[0] + bleed) * k - g, (s[1] + padTop) * k - g),
                            Size(s[2] * k + 2 * g, s[3] * k + 2 * g),
                            CornerRadius(s[4] * k + g),
                            style = Stroke(width = 2.5f * k)
                        )
                    }
                }
            }
        }
        // Плашка шага.
        Box(
            Modifier.at(step.x, step.y, step.w, step.h).graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 18f * density
            }
        ) { ArtImage(step.art, Modifier.fillMaxSize(), ContentScale.FillBounds) {} }
        val n = step.next
        PressHit(step.x + n[0] * step.w, step.y + n[1] * step.h, n[2] * step.w, n[3] * step.h, n[3] * step.h / 2f) {
            if (last) vm.finishTour() else vm.advanceTour()
        }
        val sk = step.skip
        Hit(step.x + sk[0] * step.w, step.y + sk[1] * step.h, sk[2] * step.w, sk[3] * step.h) { vm.finishTour() }
    }
}
