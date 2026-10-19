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
 * Карточка-объяснение: иконка Монтика, пилюля с заголовком, текст и кнопка.
 * [title] — что объясняем, [body] — текст, [buttonText] — подпись кнопки.
 */
@Composable
fun LessonCard(
    headline: String,
    title: String,
    body: String,
    buttonText: String = "Понятно!",
    onButton: () -> Unit
) {
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(3.dp, Color(0xFF7DB46C), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFEFF8E4)),
                contentAlignment = Alignment.Center
            ) {
                FigIcon("fg_ui_hs_montik", "🐰", 44.dp)
            }
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFBFE6AC))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(headline, color = MontikColors.Ink, fontWeight = FontWeight.Bold, fontFamily = MontikFont, fontSize = 17.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MontikColors.Ink)
        }
        Spacer(Modifier.height(14.dp))
        BigButton(buttonText, onClick = onButton)
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

/** Знакомство по шагам: тот же стиль карточки, текст шага плавно меняется. */
@Composable
fun TourCard(step: Int, last: Boolean, onNext: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 18.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(3.dp, Color(0xFF7DB46C), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFBFE6AC))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("Знакомство · ${step + 1} из ${TOUR_STEPS.size}", color = MontikColors.Ink, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
            }
            FigIcon("fg_ui_hs_montik", "🐰", 44.dp)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(targetState = step, transitionSpec = { stepTransform() }, label = "tourText") { current ->
            Text(TOUR_STEPS[current], style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink, textAlign = TextAlign.Start)
        }
        Spacer(Modifier.height(14.dp))
        BigButton(if (last) "Понятно!" else "Дальше", onClick = onNext)
        if (!last) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().clickable(onClick = onSkip).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text("Пропустить", color = MontikColors.InkSoft, fontFamily = MontikFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}
