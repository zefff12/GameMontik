package ru.montik.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/*
 * Общие движения приложения: появление окон, переходы между экранами и страницами истории.
 * Длительности короткие (200–400 мс), чтобы игра ощущалась живой, а не тормозила на каждом нажатии.
 */

/** Сколько длится основной переход. Не меняйте без проверки на слабом телефоне. */
const val MOTION_MS = 320

/** Появление один раз: прогресс от 0 до 1 при первом показе окна или экрана. */
@Composable
fun rememberEnter(durationMs: Int = MOTION_MS): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return progress
}

/** Окно плавно проявляется. */
fun Modifier.enterPop(progress: Animatable<Float, AnimationVector1D>): Modifier = graphicsLayer {
    // Без «подпрыгивания»: окно только плавно проявляется.
    alpha = progress.value
}

/**
 * Переход между экранами или страницами, у которых есть порядок (например, страницы истории):
 * вперёд — новое выезжает справа, назад — слева; старое уходит в противоположную сторону и бледнеет.
 */
fun slideStep(forward: Boolean): ContentTransform =
    (slideInHorizontally(tween(MOTION_MS)) { if (forward) it / 5 else -it / 5 } +
        fadeIn(tween(MOTION_MS))) togetherWith
        (slideOutHorizontally(tween(MOTION_MS)) { if (forward) -it / 5 else it / 5 } +
            fadeOut(tween(MOTION_MS - 60)))

/** Переход между экранами без порядка: новый проявляется и чуть растёт, старый тает. */
fun sceneFade(): ContentTransform =
    (fadeIn(tween(MOTION_MS)) + scaleIn(initialScale = 0.975f, animationSpec = tween(MOTION_MS))) togetherWith
        fadeOut(tween(MOTION_MS - 100))

/** Переход по номеру шага: вперёд или назад — определяется по сравнению шагов. */
fun AnimatedContentTransitionScope<Int>.stepTransform(): ContentTransform =
    slideStep(targetState > initialState)

/**
 * Строка корзины плавно появляется: проявляется и раскрывается по высоте.
 * Ключ строки в списке задаётся снаружи через key(...), поэтому анимация идёт только у новых строк.
 */
@Composable
fun ColumnScope.Entering(content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(MOTION_MS)) + expandVertically(tween(MOTION_MS)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
    ) {
        content()
    }
}
