package ru.montik.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * Окна из макета Figma (новый уровень, медаль, «Монтик объясняет», расчётный листок, знакомство,
 * совет, кошелёк). Картинка окна — фон; живые надписи кладутся поверх в долях картинки
 * (0…1 по ширине и высоте), старый текст закрывается подложкой того же цвета.
 * Окно выпрыгивает с пружинкой, кнопки подсвечиваются и «проседают» при нажатии.
 */

/** Цвет кремовой подложки окон макета. */
val PopCream = Color(0xFFFFF8E2)

/** Тёмно-синий текст окон. */
val PopInk = Color(0xFF0C2C5A)

/** Помощник раскладки в долях картинки окна. */
class PopScope(val w: Dp, val h: Dp, private val density: Density) {
    fun Modifier.frac(x: Float, y: Float, fw: Float, fh: Float): Modifier =
        this.offset(w * x, h * y).size(w * fw, h * fh)

    /** Размер шрифта как доля ширины окна (не зависит от настроек шрифта телефона). */
    fun sp(k: Float): TextUnit = with(density) { (w * k).toSp() }
}

/** Окно-картинка. [aspect] — ширина / высота картинки. */
@Composable
fun ArtPopup(
    art: String?,
    aspect: Float,
    onDismiss: () -> Unit,
    maxWidth: Dp = 400.dp,
    content: @Composable PopScope.() -> Unit
) {
    GameOverlay(onDismiss = onDismiss) {
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints(
                Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .graphicsLayer {
                        val v = enter.value
                        alpha = v.coerceIn(0f, 1f)
                        val s = 0.82f + 0.18f * v
                        scaleX = s
                        scaleY = s
                        translationY = (1f - v) * 60f
                    }
            ) {
                if (art != null) ArtImage(art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
                val density = LocalDensity.current
                val scope = remember(maxWidth, maxHeight, density) { PopScope(maxWidth, maxHeight, density) }
                scope.content()
            }
        }
    }
}

/** Закрыть старую надпись подложкой. */
@Composable
fun PopScope.Cover(x: Float, y: Float, fw: Float, fh: Float, color: Color = PopCream, radius: Dp = 8.dp) {
    Box(Modifier.frac(x, y, fw, fh).clip(RoundedCornerShape(radius)).background(color))
}

/** Живая надпись в рамке окна. */
@Composable
fun PopScope.PopText(
    text: String,
    x: Float,
    y: Float,
    fw: Float,
    fh: Float,
    size: Float,
    color: Color = PopInk,
    weight: FontWeight = FontWeight.Bold,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    bg: Color? = null
) {
    Box(
        Modifier.frac(x, y, fw, fh).let { if (bg != null) it.clip(RoundedCornerShape(8.dp)).background(bg) else it },
        contentAlignment = when (align) {
            TextAlign.Center -> Alignment.Center
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text,
            color = color,
            fontSize = sp(size),
            lineHeight = sp(size * 1.22f),
            fontWeight = weight,
            textAlign = align,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Нажимаемая зона поверх нарисованной кнопки: вспышка и «проседание». */
@Composable
fun PopScope.PopTap(x: Float, y: Float, fw: Float, fh: Float, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val flash by animateFloatAsState(if (pressed) 0.3f else 0f, tween(if (pressed) 50 else 240), label = "popTap")
        Box(
        Modifier.frac(x, y, fw, fh)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = flash * 0.4f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

/** Картинка-деталь окна в долях (например, кнопка «Понятно!» из макета). */
@Composable
fun PopScope.PopArt(name: String, x: Float, y: Float, fw: Float, fh: Float) {
    ArtImage(name, Modifier.frac(x, y, fw, fh), ContentScale.Fit) {}
}
