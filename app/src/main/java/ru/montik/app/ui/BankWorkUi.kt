package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BankWork
import ru.montik.app.game.NoteAnswer
import ru.montik.app.game.NoteDefect

/*
 * Работа в банке — кадры макета Frame 29 (зал банка, Монтик за компьютером), Frame 27 и 28
 * (стол «Проверяй деньги!»: образец слева, купюра клиента справа, красная и зелёная кнопки).
 * С картинки стола убрана правая купюра: игра кладёт свою — нормальную или с дефектом
 * (fg_bank_note_ok / scribble / tear / stain / hole / color / faded). Правила — game/BankWork.kt.
 */

private const val BK = 412f / 841f
private val OkGreen = Color(0xFF2FA84F)
private val BadRed = Color(0xFFE0453A)

private enum class BankStep { Intro, Playing }

@Composable
fun BankJobScreen(vm: GameViewModel) {
    var step by remember { mutableStateOf(BankStep.Intro) }
    BackHandler { vm.goTo(Screen.Work) }
    when (step) {
        BankStep.Intro -> BankIntro(vm) { step = BankStep.Playing }
        BankStep.Playing -> BankDesk(vm)
    }
}

@Composable
private fun BankIntro(vm: GameViewModel, onStart: () -> Unit) {
    DesignCanvas(background = { BankBackground("fg_bank_hall") }) {
        if (rememberHasArt("fg_bank_hall")) Art("fg_bank_hall", 0f, 0f, DESIGN_W, 1870f * BK, ContentScale.FillBounds)
        // Белый человечек из макета стёрт — на его месте твой Монтик.
        SceneMontik(vm, -30f, 520f, 260f)
        Bubble(
            "Сегодня Монтик проверяет деньги! Слева — образец купюры. Сравни с ним купюру клиента: " +
                "если всё совпадает — жми ✓, если она испорчена или подделана — ✗.",
            24f, 176f, 364f, 150f, size = 16f
        )
        Bubble("Будь внимателен: смотри на цвет, рисунок, края и пятна.", 60f, 740f, 292f, 70f, size = 15f)
        DPill("Начать", 121f, 828f, 169f, 59f, fill = Color(0xFF070707)) { onStart() }
        Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
    }
}

@Composable
private fun BankDesk(vm: GameViewModel) {
    val notes = remember { BankWork.notes(vm.state) }
    val answers = remember { mutableStateListOf<NoteAnswer>() }
    var index by remember { mutableStateOf(0) }
    var reveal by remember { mutableStateOf<NoteAnswer?>(null) }
    val startedAt = remember { System.currentTimeMillis() }
    val fb = rememberFeedback()
    val slide = remember { Animatable(0f) }

    // После ответа — показать, какой была купюра, и через секунду взять следующую.
    LaunchedEffect(reveal) {
        val r = reveal ?: return@LaunchedEffect
        delay(if (r.correct) 900 else 1900)
        if (index + 1 >= notes.size) {
            val elapsed = System.currentTimeMillis() - startedAt
            val right = answers.count { it.correct }
            val missed = answers.filter { !it.correct }
            val details = buildList {
                add("Проверено купюр: ${answers.size}, верно: $right.")
                if (missed.isNotEmpty()) add("Ошибки: " + missed.joinToString(", ") { if (it.defect.defective) "пропущен дефект «${it.defect.title}»" else "хорошая купюра отмечена как дефектная" } + ".")
                else add("Ни одной ошибки — настоящий банковский специалист!")
            }
            vm.finishBankShift(BankWork.performance(answers.toList(), elapsed), details)
            vm.goTo(Screen.Work)
        } else {
            index += 1
            reveal = null
            slide.snapTo(60f)
            slide.animateTo(0f, tween(250))
        }
    }

    fun answer(saidDefective: Boolean) {
        if (reveal != null) return
        val a = NoteAnswer(notes[index], saidDefective)
        answers.add(a)
        if (a.correct) fb.good() else fb.bad()
        reveal = a
    }

    DesignCanvas(background = { BankBackground("fg_bank_desk") }) {
        if (rememberHasArt("fg_bank_desk")) Art("fg_bank_desk", 0f, 0f, DESIGN_W, 1870f * BK, ContentScale.FillBounds)
        SceneMontik(vm, -60f, 470f, 270f)

        // Купюра клиента — на месте правой купюры в макете.
        val defect = notes[index]
        Box(Modifier.at(445f * BK, 761f * BK, 383f * BK, 198f * BK).graphicsLayer { translationX = slide.value }) {
            ArtImage("fg_bank_note_${defect.art}", Modifier.fillMaxSize(), ContentScale.FillBounds) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(d(6f))).background(Color(0xFFF3D9C8)), contentAlignment = Alignment.Center) {
                    Text("5000", color = Color(0xFFB0553A), fontSize = fs(26f, false), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Подписи под купюрами, как в кадре Frame 28.
        Chip("✓  Образец", 72f, 452f, 112f, OkGreen)
        reveal?.let { r ->
            if (r.defect.defective) Chip("✗  Дефектная", 244f, 470f, 140f, BadRed)
            else Chip("✓  Нормальная", 244f, 470f, 140f, OkGreen)
        }

        // Счётчик и подсказка.
        Bubble("Купюра ${index + 1} из ${notes.size}", 122f, 30f, 170f, 40f, size = 15f)
        reveal?.let { r ->
            val text = when {
                r.correct && r.defect.defective -> "Верно! ${r.defect.explain}"
                r.correct -> "Верно! Купюра в порядке."
                r.defect.defective -> "Ой! ${r.defect.explain}"
                else -> "Ой! Эта купюра была в порядке — сравни ещё раз с образцом."
            }
            Bubble(text, 150f, 650f, 252f, 96f, size = 14f, color = if (r.correct) Color(0xFF45602B) else BadRed)
        }

        // Красная ✗ — «дефектная», зелёная ✓ — «нормальная».
        Hit(203f, 544f, 71f, 57f) { answer(saidDefective = true) }
        Hit(289f, 558f, 79f, 57f) { answer(saidDefective = false) }
    }
}

@Composable
private fun DesignScope.Chip(text: String, x: Float, y: Float, w: Float, color: Color) {
    Box(
        Modifier.at(x, y, w, 26f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun BankBackground(art: String) {
    ArtImage(art, Modifier.fillMaxSize(), ContentScale.Crop) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF4B5C7A)))))
    }
}
