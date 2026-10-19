package ru.montik.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/*
 * Живые элементы поверх кадров Figma. Кадр лежит фоном один в один (FrameArt), а всё, что меняется
 * в игре, — монеты, суммы, полосы, вопросы, ответы — рисуется кодом точно на месте нарисованного:
 * старая надпись закрашивается цветом подложки из макета, сверху пишется настоящее значение.
 * Кнопки макета нажимаются и подсвечиваются.
 */

/** Цвет текста кадров «Работа», «Копилка» и др. (тёмно-синий, как в макете). */
val FigmaInk = Color(0xFF0B2A5B)

/**
 * Невидимая кнопка поверх нарисованной в кадре: при нажатии кнопка подсвечивается (белая вспышка)
 * и чуть «проседает». Неактивная кнопка нажатий не принимает.
 */
@Composable
fun DesignScope.PressHit(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    radius: Float,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val flash by animateFloatAsState(if (pressed) 0.12f else 0f, tween(if (pressed) 60 else 220), label = "press")
    Box(
        Modifier.at(x, y, w, h)
            .clip(RoundedCornerShape(d(radius)))
            .background(Color.Black.copy(alpha = flash))
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
    )
}

/**
 * Настоящее значение на месте нарисованного: подложка цвета [bg] закрывает старую надпись,
 * сверху — [text]. Многострочный текст переносится внутри рамки.
 */
@Composable
fun DesignScope.LiveText(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    bg: Color,
    size: Float,
    color: Color = FigmaInk,
    align: Alignment = Alignment.Center,
    radius: Float = 6f,
    weight: FontWeight = FontWeight.ExtraBold,
    maxLines: Int = 1
) {
    Box(
        Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(radius))).background(bg),
        contentAlignment = align
    ) {
        Text(
            text,
            color = color,
            fontSize = fs(size, false),
            lineHeight = fs(size * 1.18f, false),
            fontWeight = weight,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = when (align) {
                Alignment.CenterStart, Alignment.TopStart -> TextAlign.Start
                Alignment.CenterEnd -> TextAlign.End
                else -> TextAlign.Center
            }
        )
    }
}

/** Полоса еды, воды или сил: дорожка и заливка; заливка плавно растёт и убывает. */
@Composable
fun DesignScope.LiveBar(x: Float, y: Float, w: Float, h: Float, value: Int, track: Color, fill: Color) {
    val part by animateFloatAsState((value / 100f).coerceIn(0f, 1f), tween(600), label = "bar")
    Box(Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(h / 2f))).background(track)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(part.coerceAtLeast(0.001f))
                .clip(RoundedCornerShape(d(h / 2f))).background(fill)
        )
    }
}

/** Монеты «докручиваются» до нового значения. */
@Composable
fun rememberCountUp(target: Int): Int {
    val shown by animateIntAsState(target, tween(700), label = "coins")
    return shown
}

/**
 * Карточка «пока закрыто»: тёмная полупрозрачная пелена поверх карточки макета и плашка с причиной.
 */
@Composable
fun DesignScope.LockedVeil(x: Float, y: Float, w: Float, h: Float, text: String) {
    Box(
        Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(22f))).background(Color(0x99474A52)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            Modifier.padding(end = d(14f)).clip(RoundedCornerShape(d(14f))).background(Color(0xE6202228))
                .padding(horizontal = d(10f), vertical = d(6f))
        ) {
            Text("🔒 $text", color = Color.White, fontSize = fs(12.5f, false), fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

/** Кнопка ответа мини-игры: при выборе зеленеет (верно) или краснеет и вздрагивает (неверно). */
@Composable
fun DesignScope.AnswerButton(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    state: Int,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            else -> 1f
        },
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "answer"
    )
    val fill = when (state) {
        1 -> Color(0xFFBDEBA8)
        -1 -> Color(0xFFFFC9C4)
        else -> Color(0xFFF8FDF3)
    }
    val border = when (state) {
        1 -> Color(0xFF3FA944)
        -1 -> Color(0xFFE0574B)
        else -> Color(0xFF8FC38E)
    }
    Box(
        Modifier.at(x, y, w, h)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(d(18f)))
            .background(border)
            .padding(d(2.5f))
            .clip(RoundedCornerShape(d(16f)))
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = FigmaInk, fontSize = fs(24f, false), fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Закрашивает область макета цветом подложки (под живые элементы). */
@Composable
fun DesignScope.Cover(x: Float, y: Float, w: Float, h: Float, bg: Color, radius: Float = 0f) {
    Box(Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(radius))).background(bg))
}

/** Кнопка-картинка из макета: при нажатии чуть темнеет (без «подпрыгивания»). */
@Composable
fun DesignScope.ArtButton(art: String, x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.8f else 1f, tween(if (pressed) 60 else 200), label = "artBtn")
    Box(
        Modifier.at(x, y, w, h)
            .graphicsLayer { alpha = press }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        ArtImage(art, Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Fit) {}
    }
}
