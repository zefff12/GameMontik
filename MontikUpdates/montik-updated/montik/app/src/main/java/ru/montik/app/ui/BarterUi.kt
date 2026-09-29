package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BarterGame

/**
 * Экран с енотом на рынке: учим справедливому обмену.
 * Енот предлагает 3 сделки, за правильные ответы даём опыт и монеты.
 */
@Composable
fun BarterScreen(vm: GameViewModel) {
    BackHandler { vm.back() }

    val trades = remember { BarterGame.getSessionTrades(vm.state) }
    var tradeIndex by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableIntStateOf(-1) }
    var showResult by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("intro") }  // intro → trading → summary

    val fb = rememberFeedback()

    LaunchedEffect(showResult) {
        if (showResult) {
            delay(1500)
            tradeIndex += 1
            selectedAnswer = -1
            showResult = false

            if (tradeIndex >= trades.size) {
                phase = "summary"
            }
        }
    }

    DesignCanvas(background = {
        ArtImage("fg_market_bg", Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Color(0xFFE8D4A8)))
        }
    }) {
        when (phase) {
            "intro" -> BarterIntro(vm, trades.size) { phase = "trading" }
            "trading" -> BarterTrading(
                trade = trades[tradeIndex],
                tradeNum = tradeIndex + 1,
                totalTrades = trades.size,
                selectedAnswer = selectedAnswer,
                showResult = showResult,
                onSelectAnswer = { idx ->
                    if (!showResult) {
                        selectedAnswer = idx
                        showResult = true
                        val correct = idx == trades[tradeIndex].correctIndex
                        if (correct) {
                            correctCount += 1
                            fb.good()
                        } else {
                            fb.bad()
                        }
                    }
                },
                fb = fb
            )
            "summary" -> BarterSummary(
                correctCount = correctCount,
                totalTrades = trades.size,
                understanding = vm.state.barterUnderstanding,
                onClose = {
                    vm.completeBarterSession(correctCount)
                    vm.back()
                }
            )
        }
    }
}

@Composable
private fun DesignScope.BarterIntro(
    vm: GameViewModel,
    totalTrades: Int,
    onStart: () -> Unit
) {
    // Енот приветствует
    Box(Modifier.at(50f, 100f, 312f, 280f), contentAlignment = Alignment.Center) {
        // Рисуем енота (временно текст, позже картинка)
        Text("🦝", fontSize = fs(120f, false))
    }

    Bubble(
        "Привет, Монтик! Я торговец с рынка. Хочешь научиться справедливому обмену? " +
        "Я буду предлагать товары, а ты выбирай, что в них хочешь отдать в обмен!",
        30f, 380f, 352f, 150f, size = 15f
    )

    Bubble(
        "Будет $totalTrades сделок. За каждый правильный ответ ты получишь опыт и монеты!",
        50f, 550f, 312f, 80f, size = 14f, color = Color(0xFF2FA84F)
    )

    DPill("Начнём!", 121f, 750f, 169f, 59f, fill = Color(0xFF070707)) { onStart() }
    Box(Modifier.at(8f, 26f)) { BackCircle({ vm.back() }) }
}

@Composable
private fun DesignScope.BarterTrading(
    trade: BarterGame.BarterTrade,
    tradeNum: Int,
    totalTrades: Int,
    selectedAnswer: Int,
    showResult: Boolean,
    onSelectAnswer: (Int) -> Unit,
    fb: Feedback
) {
    // Енот вверху
    Box(Modifier.at(120f, 30f, 172f, 140f), contentAlignment = Alignment.Center) {
        Text("🦝", fontSize = fs(80f, false))
    }

    // Номер сделки
    Bubble("Сделка $tradeNum из $totalTrades", 120f, 10f, 170f, 30f, size = 12f)

    // Предложение енота: что он дает
    Box(
        Modifier.at(40f, 190f, 332f, 60f)
            .clip(RoundedCornerShape(d(16f)))
            .background(Color(0xFFFFF8E1))
            .border(d(2f), Color(0xFFFFD54F), RoundedCornerShape(d(16f))),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Енот предлагает:", fontSize = fs(12f, false), fontWeight = FontWeight.Bold, color = Color(0xFF6F4E37))
            Text(trade.offers, fontSize = fs(18f, false), fontWeight = FontWeight.Bold, color = Color(0xFFF57C00))
        }
    }

    // Стрелка обмена
    Text("⬇️  Что ты дашь в обмен?  ⬇️", Modifier.at(100f, 270f), fontSize = fs(14f, false), fontWeight = FontWeight.Bold)

    // Варианты ответов (кнопки)
    val buttonHeight = 50f
    val gapY = 70f

    trade.wantsOptions.forEachIndexed { idx, option ->
        val isSelected = selectedAnswer == idx
        val isCorrect = idx == trade.correctIndex

        val bgColor = when {
            !showResult -> Color(0xFFE3F2FD)  // голубой до ответа
            isSelected && isCorrect -> Color(0xFFC8E6C9)  // зелёный правильно
            isSelected && !isCorrect -> Color(0xFFFFCDD2)  // красный неправильно
            else -> Color(0xFFF5F5F5)  // серый остальное
        }

        val borderColor = when {
            isSelected && showResult -> if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
            isSelected -> Color(0xFF1976D2)
            else -> Color(0xFFBDBDBD)
        }

        Box(
            Modifier.at(50f, 330f + idx * gapY, 312f, buttonHeight)
                .clip(RoundedCornerShape(d(12f)))
                .background(bgColor)
                .border(d(2f), borderColor, RoundedCornerShape(d(12f))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                option,
                fontSize = fs(14f, false),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF1D1D1D)
            )
        }

        if (!showResult) {
            Hit(50f, 330f + idx * gapY, 312f, buttonHeight) {
                onSelectAnswer(idx)
            }
        }
    }

    // Показываем результат
    if (showResult) {
        val trade_obj = trade
        val correct = selectedAnswer == trade_obj.correctIndex
        val resultText = if (correct) "✅ Верно!" else "❌ Ошибка"
        val resultColor = if (correct) Color(0xFF2E7D32) else Color(0xFFC62828)

        Bubble(
            "$resultText\n${trade_obj.lesson}",
            30f, 650f, 352f, 120f,
            size = 13f,
            color = resultColor
        )
    }

    // Текущий прогресс внизу (временно скрыто, так как понимание обновляется только в summary)
    // Text(
    //     "Уровень: ${BarterGame.getLevelDescription(understanding)}",
    //     Modifier.at(50f, 825f),
    //     fontSize = fs(12f, false),
    //     fontWeight = FontWeight.Bold
    // )
}

@Composable
private fun DesignScope.BarterSummary(
    correctCount: Int,
    totalTrades: Int,
    understanding: Int,
    onClose: () -> Unit
) {
    // Итоги
    Box(Modifier.at(50f, 150f, 312f, 600f), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉 Отлично!", fontSize = fs(32f, false), fontWeight = FontWeight.Bold)

            Text(
                "Ты ответил правильно на $correctCount из $totalTrades",
                fontSize = fs(16f, false),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            val stars = when (correctCount) {
                0 -> "⭐"
                1 -> "⭐⭐"
                else -> "⭐⭐⭐"
            }
            Text(stars, fontSize = fs(24f, false), modifier = Modifier.padding(top = 16.dp))

            Text(
                "Понимание бартера: ${BarterGame.getLevelDescription(understanding)}",
                fontSize = fs(14f, false),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp)
            )

            val reward = if (correctCount >= 2) "${BarterGame.COIN_REWARD} монет 💰" else "опыт ✨"
            Text(
                "Награда: $reward",
                fontSize = fs(14f, false),
                color = Color(0xFF2FA84F),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    Bubble(
        "Спасибо за игру! Теперь ты понимаешь торговлю лучше. Приходи через 10 дней!",
        30f, 750f, 352f, 100f, size = 14f
    )

    DPill("Выйти", 121f, 870f, 169f, 59f, fill = Color(0xFF8AD742)) { onClose() }
}
