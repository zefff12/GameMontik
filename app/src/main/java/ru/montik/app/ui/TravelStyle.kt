package ru.montik.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/*
 * Кнопки и плашки путешествий в стиле макета: стрелка «назад» и монеты — как на остальных экранах,
 * мягкие светлые плашки и выпуклые кнопки (зелёная — главное действие, кремовая — второстепенное).
 */

val TravelPlateInk = Color(0xFF14246B)
val TravelPlateSub = Color(0xFF4A5C80)

/** Стрелка «назад» из макета (кружок со стрелкой). */
@Composable
fun DesignScope.TravelBack(x: Float, y: Float, onClick: () -> Unit) {
    Box(Modifier.at(x, y, 58f, 57f)) { ArtImage("fg4_back", Modifier.fillMaxSize(), ContentScale.Fit) {} }
    PressHit(x + 4f, y + 4f, 50f, 50f, 25f, onClick = onClick)
}

/** Мягкая светлая плашка: белая с голубой обводкой, как карточки жилья и магазина. */
@Composable
fun DesignScope.TravelPlate(x: Float, y: Float, w: Float, h: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(d(22f))
    Box(
        Modifier.at(x, y, w, h).shadow(d(3f), shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), Color(0xFFF2F9FF).copy(alpha = 0.96f))))
            .border(d(2f), Color(0xFFBFE3F7), shape)
            .padding(horizontal = d(12f), vertical = d(8f)),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

/**
 * Выпуклая кнопка: [primary] — зелёная с тёмной обводкой и белым текстом с тенью,
 * иначе — кремовая с коричневой надписью. При нажатии чуть темнеет.
 */
@Composable
fun DesignScope.TravelButton(text: String, x: Float, y: Float, w: Float, h: Float, primary: Boolean = true, size: Float = 17f, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dim by animateFloatAsState(if (pressed) 0.85f else 1f, tween(if (pressed) 60 else 200), label = "travelBtn")
    val shape = RoundedCornerShape(d(h / 2f))
    val top = if (primary) Color(0xFF8BE06A) else Color(0xFFFFFBF0)
    val bottom = if (primary) Color(0xFF34A23C) else Color(0xFFF6E6C4)
    val edge = if (primary) Color(0xFF1F6E26) else Color(0xFFC9A66B)
    Box(
        Modifier.at(x, y, w, h).graphicsLayer { alpha = dim }
            .shadow(d(4f), shape).clip(shape)
            .background(edge)
            .padding(start = d(2.5f), end = d(2.5f), top = d(2.5f), bottom = d(4.5f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Блик сверху.
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = d(3f)).size(d(w - h), d(h * 0.22f))
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (primary) 0.35f else 0.7f))
        )
        Text(
            text,
            style = TextStyle(
                color = if (primary) Color.White else Color(0xFF6B3A1E),
                fontSize = fs(size, false),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                shadow = if (primary) Shadow(Color(0xFF1F6E26), androidx.compose.ui.geometry.Offset(0f, 2f), 2f) else null
            ),
            maxLines = 1
        )
    }
}

/** Квадратная плитка с картинкой и подписью — светлая, с голубой обводкой. */
@Composable
fun DesignScope.TravelTile(art: String, emoji: String, label: String, x: Float, y: Float, onClick: () -> Unit) {
    val shape = RoundedCornerShape(d(22f))
    Box(
        Modifier.at(x, y, 96f, 96f).shadow(d(3f), shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEAF6FF))))
            .border(d(2f), Color(0xFFBFE3F7), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtImage(art, Modifier.size(d(52f)), ContentScale.Fit) { Text(emoji, fontSize = fs(32f, false)) }
            Text(label, color = TravelPlateInk, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
