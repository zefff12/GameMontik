package ru.montik.app.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Общие окна игры в одном стиле: без серого затемнения, светлая карточка с зелёной рамкой,
 * заголовок-пилюля и лаймовая кнопка. Используются для «Монтик объясняет», знакомства и подсказок.
 */

/**
 * Убирает серое затемнение под системным окном (Dialog / AlertDialog): окно стоит прямо на игре.
 * Вызывается внутри содержимого окна.
 */
@Composable
fun NoDialogDim() {
    val view = LocalView.current
    // Под окном основной экран затемняется наполовину — как на рисунке «Жильё и уровень».
    SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.5f) }
}

/**
 * Слой поверх экрана: под окном основной экран затемняется (чёрный 50%, как «Жильё и уровень»).
 * Касания мимо окна не пропускаются, «назад» закрывает окно через [onDismiss].
 */
@Composable
fun GameOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(enabled = true) { onDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Карточка-объяснение в стиле новых плашек «Монтик объясняет»: кремовое окно в толстой зелёной рамке,
 * сверху кружок с иконкой и зелёная пилюля, крупный заголовок, текст, Монтик и выпуклая кнопка «Понятно!».
 * [headline] — подпись в пилюле (первый символ, если это эмодзи, уходит в кружок).
 */
@Composable
fun LessonCard(
    headline: String,
    title: String,
    body: String,
    buttonText: String = "Понятно!",
    onButton: () -> Unit
) {
    val ink = Color(0xFF0E2A12)
    val navy = Color(0xFF13235E)
    val icon = headline.substringBefore(' ').takeIf { it.isNotEmpty() && !it[0].isLetter() }
    val label = if (icon != null) headline.substringAfter(' ') else headline
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
    ) {
        Column(
            Modifier
                .padding(top = 30.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF6BD844), Color(0xFF1F9A25))))
                .padding(6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF0), Color(0xFFF4FBE6))))
                .padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 16.dp)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAF5D8))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(title, color = ink, fontFamily = MontikFont, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 30.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(body, color = navy, fontFamily = MontikFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LessonButton(buttonText, Modifier.weight(1f), onButton)
                Spacer(Modifier.size(10.dp))
                ArtImage("fg_hero_white", Modifier.size(70.dp), androidx.compose.ui.layout.ContentScale.Fit) { Text("🐰", fontSize = 40.sp) }
            }
        }
        // Шапка поверх рамки: кружок с иконкой и пилюля «Монтик объясняет».
        Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6BD844), Color(0xFF1F9A25))))
                    .padding(4.dp).clip(CircleShape).background(Color(0xFFF6FFEC)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) Text(icon, fontSize = 28.sp) else FigIcon("fg_ui_hs_montik", "🐰", 44.dp)
            }
            Box(
                Modifier.padding(start = 0.dp).height(42.dp)
                    .clip(RoundedCornerShape(topEnd = 21.dp, bottomEnd = 21.dp))
                    .background(Color(0xFF1F9A25))
                    .padding(top = 3.dp, bottom = 3.dp, end = 3.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF1FCE4), Color(0xFFC6EEA8))))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = ink, fontFamily = MontikFont, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
            }
        }
    }
}

/** Выпуклая зелёная кнопка «Понятно! ✓», как на новых плашках. */
@Composable
private fun LessonButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(54.dp).clip(shape).background(Color(0xFF1B7A22))
            .padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 5.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF8BE36A), Color(0xFF2FA534))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$text ✓",
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White, fontFamily = MontikFont, fontWeight = FontWeight.Black, fontSize = 21.sp,
                shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF1B6B20), androidx.compose.ui.geometry.Offset(0f, 3f), 3f)
            ),
            maxLines = 1
        )
    }
}

/** «Монтик объясняет»: объяснение темы, которое показывается один раз. */
@Composable
fun LessonDialog(lesson: ru.montik.app.game.Lesson, onDismiss: () -> Unit) {
    GameOverlay(onDismiss) {
        LessonCard(
            headline = "${lesson.emoji} Монтик объясняет",
            title = lesson.title,
            body = lesson.text,
            onButton = onDismiss
        )
    }
}

