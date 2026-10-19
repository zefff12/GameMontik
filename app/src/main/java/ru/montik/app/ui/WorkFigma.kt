package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.montik.app.ActiveShift
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BankWork
import ru.montik.app.game.BusinessEngine
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Job
import ru.montik.app.game.ShopWork

/*
 * «Работа» по макету Figma: список работ — кадр «Android Compact - 75», смена с мини-игрой — «Frame 52».
 * Кадры лежат фоном, всё живое (монеты, полосы, суммы, вопрос, коробки, ответы) — настоящие элементы.
 * Суммы берутся из правил игры: ставка помощника, ставка в магазине по должности, ставка в банке.
 */

/** Список работ (кадр AC75). Если Монтик не готов работать — поверх карточка «Сегодня работать нельзя». */
@Composable
fun FigmaWorkList(vm: GameViewModel) {
    val s = vm.state
    val blocker = vm.workBlocker
    val canWork = blocker == null
    val storeOpen = ShopWork.unlocked(s)
    val bankOpen = BankWork.unlocked(s)
    val businessOpen = BusinessEngine.canStart(s)
    val coins = rememberCountUp(s.coins)

    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFF3E4CC))) }) {
        FrameArt("fg_figma_work")

        // Шапка: назад и живой баланс.
        PressHit(18f, 58f, 52f, 45f, 26f) { vm.back() }
        LiveText("$coins", 336f, 64f, 56f, 34f, bg = Color(0xFFFEFEFE), size = 22f)

        // Еда, вода, силы.
        LiveBar(65f, 193f, 140f, 14f, s.food, Color(0xFFD5E7F3), Color(0xFFFB7E66))
        LiveBar(65f, 226f, 140f, 14f, s.water, Color(0xFFD5E7F3), Color(0xFF32ADFD))
        LiveBar(65f, 258f, 140f, 14f, s.energy, Color(0xFFD5E7F3), Color(0xFF6EC542))

        // Ставки за смену — из правил игры.
        val amountBg = Color(0xFFFEFBF2)
        LiveText("+${Job.HELPER.base}", 214f, 382f, 50f, 26f, amountBg, 17f, align = Alignment.CenterStart)
        LiveText("+${ShopWork.rank(s).base}", 214f, 498f, 50f, 26f, amountBg, 17f, align = Alignment.CenterStart)
        LiveText("+${BankWork.base(s)}", 214f, 611f, 54f, 27f, amountBg, 17f, align = Alignment.CenterStart)

        // Карточки работ: нажимается вся карточка и зелёная стрелка.
        PressHit(18f, 320f, 380f, 112f, 22f, enabled = canWork) { vm.startShift(Job.HELPER) }
        PressHit(18f, 440f, 380f, 104f, 22f, enabled = canWork && storeOpen) { vm.goTo(Screen.Store) }
        PressHit(18f, 551f, 380f, 109f, 22f, enabled = canWork && bankOpen) { vm.goTo(Screen.BankJob) }
        PressHit(18f, 668f, 380f, 108f, 22f, enabled = businessOpen) { vm.goTo(Screen.Business) }
        if (!storeOpen) LockedVeil(18f, 440f, 380f, 104f, "Откроется через ${ShopWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWord(ShopWork.UNLOCK_SHIFTS - s.shifts)}")
        if (!bankOpen) LockedVeil(18f, 551f, 380f, 109f, "Откроется через ${BankWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWord(BankWork.UNLOCK_SHIFTS - s.shifts)}")
        if (businessOpen) {
            // Бизнес уже можно открыть: снимаем серую пелену макета и зовём внутрь.
            Box(Modifier.at(18f, 668f, 380f, 108f).clip(RoundedCornerShape(d(22f))).background(Color(0xB3FFFFFF)))
            LiveText("Открыть ›", 262f, 700f, 120f, 40f, Color(0xFF55C13B), 17f, Color.White, radius = 20f)
        }

        if (!canWork) {
            // Монтик устал или голоден: выйти на работу нельзя — только поесть или поспать.
            val enter = rememberEnter()
            Box(Modifier.at(10f, 330f, 392f, 221f).enterPop(enter)) {
                ArtImage("fg_figma_nowork", Modifier.fillMaxSize(), ContentScale.FillBounds) {}
            }
            PressHit(10f + 392f * 0.043f, 330f + 221f * 0.727f, 392f * 0.466f, 221f * 0.177f, 16f) { vm.goTo(Screen.Grocery) }
            PressHit(10f + 392f * 0.520f, 330f + 221f * 0.727f, 392f * 0.414f, 221f * 0.177f, 16f) { vm.goTo(Screen.Sleep) }
        }
    }
}

/** Смена с мини-игрой (кадр Frame 52): вопрос, коробки с деталями и три ответа. */
@Composable
fun FigmaWorkShift(vm: GameViewModel, shift: ActiveShift) {
    val s = vm.state
    val task = shift.task
    val coins = rememberCountUp(s.coins)
    val scope = rememberCoroutineScope()
    var picked by remember(task) { mutableStateOf(-1) }

    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFF9F8F0))) }) {
        FrameArt("fg_figma_work_task")

        PressHit(18f, 15f, 46f, 46f, 23f) { vm.cancelShift(); vm.back() }
        LiveText("$coins", 343f, 24f, 50f, 30f, bg = Color(0xFFFEFEFE), size = 22f)

        // Состояние Монтика: полосы, проценты и фраза в облачке.
        val track = Color(0xFFE4EADF)
        val statsBg = Color(0xFFEFF8EA)
        LiveBar(122f, 88f, 104f, 12f, s.food, track, Color(0xFFFD8D41))
        LiveBar(122f, 120f, 104f, 12f, s.water, track, Color(0xFF7CC36A))
        LiveBar(122f, 152f, 104f, 12f, s.energy, track, Color(0xFFFED844))
        LiveText("${s.food}%", 231f, 86f, 44f, 17f, statsBg, 14f, align = Alignment.CenterStart)
        LiveText("${s.water}%", 231f, 118f, 44f, 17f, statsBg, 14f, align = Alignment.CenterStart)
        LiveText("${s.energy}%", 231f, 150f, 44f, 17f, statsBg, 14f, align = Alignment.CenterStart)
        val eff = GameEngine.efficiency(s)
        val mood = if (eff >= 100) "Монтик полон сил и готов работать!" else "Монтик устал: работает на $eff%"
        LiveText(mood, 287f, 88f, 102f, 88f, Color(0xFFE0F0D5), 12.5f, radius = 34f, maxLines = 4)

        // Другие работы со ставками из правил.
        val pill = Color(0xFFFEF3D6)
        LiveText("+${ShopWork.rank(s).base}", 205f, 260f, 54f, 24f, pill, 17f, align = Alignment.CenterStart)
        LiveText("+${BankWork.base(s)}", 205f, 376f, 54f, 24f, pill, 17f, align = Alignment.CenterStart)
        PressHit(15f, 197f, 382f, 100f, 20f, enabled = ShopWork.unlocked(s)) { vm.cancelShift(); vm.goTo(Screen.Store) }
        PressHit(15f, 310f, 382f, 102f, 20f, enabled = BankWork.unlocked(s)) { vm.cancelShift(); vm.goTo(Screen.BankJob) }
        if (!ShopWork.unlocked(s)) LockedVeil(15f, 197f, 382f, 100f, "Откроется через ${ShopWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWord(ShopWork.UNLOCK_SHIFTS - s.shifts)}")
        if (!BankWork.unlocked(s)) LockedVeil(15f, 310f, 382f, 102f, "Откроется через ${BankWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWord(BankWork.UNLOCK_SHIFTS - s.shifts)}")

        // Задание смены: название работы, ставка, вопрос.
        val card = Color(0xFFF5FBFE)
        PressHit(30f, 441f, 40f, 42f, 20f) { vm.cancelShift() }
        LiveText(shift.job.title, 88f, 441f, 200f, 22f, card, 17f, align = Alignment.CenterStart)
        LiveText(if (shift.job == Job.HELPER) "Собирай детали" else "Считай сдачу", 88f, 463f, 200f, 18f, card, 13f,
            color = Color(0xFF8495A8), align = Alignment.CenterStart, weight = FontWeight.SemiBold)
        LiveText("+${shift.job.base}", 338f, 451f, 50f, 26f, pill, 17f, align = Alignment.CenterStart)
        LiveText(task.question, 196f, 508f, 182f, 98f, Color.White, 14.5f, align = Alignment.CenterStart, maxLines = 5)

        // Коробки с деталями — сколько и каких, берётся из задания.
        Cover(22f, 614f, 370f, 114f, card)
        TaskBoxes(task.boxes, task.broken)

        // Ответы: выбор подсвечивается, через миг засчитывается.
        val xs = floatArrayOf(30f, 150f, 269f)
        task.options.take(3).forEachIndexed { i, option ->
            // После выбора: верный ответ — зелёный, ошибочный выбор — красный.
            val state = when {
                picked < 0 -> 0
                i == task.correctIndex -> 1
                i == picked -> -1
                else -> 0
            }
            AnswerButton(option, xs[i], 735f, 113f, 54f, state) {
                if (picked >= 0) return@AnswerButton
                picked = i
                scope.launch {
                    delay(650)
                    vm.answerTask(i)
                }
            }
        }
        PressHit(53f, 810f, 305f, 46f, 23f) { if (picked < 0) vm.skipTask() }
    }
}

/** Коробки мини-игры по макету: картинка коробки, подпись «N детали», между ними «+» (или «−» для брака). */
@Composable
private fun DesignScope.TaskBoxes(boxes: List<Int>, broken: Int) {
    if (boxes.isEmpty()) return
    val items: List<Pair<String, Boolean>> = if (broken > 0) {
        listOf("${boxes[0]} ${GameEngine.details(boxes[0])}" to false, "$broken брак" to true)
    } else {
        boxes.map { "$it ${GameEngine.details(it)}" to false }
    }
    val n = items.size
    val boxW = if (n <= 3) 86f else 70f
    val gap = if (n <= 3) 34f else 26f
    val total = n * boxW + (n - 1) * gap
    var x = 22f + (370f - total) / 2f
    items.forEachIndexed { i, (label, bad) ->
        val enter = rememberEnter(320 + i * 90)
        Box(
            Modifier.at(x, 620f, boxW, boxW * 76f / 90f).enterPop(enter)
                .graphicsLayer { alpha = if (bad) 0.55f else 1f }
        ) {
            ArtImage("fg_work_box", Modifier.fillMaxSize(), ContentScale.Fit) {}
        }
        if (bad) {
            DText("✕", x, 632f, boxW, 34f, color = Color(0xFFE0574B), align = androidx.compose.ui.text.style.TextAlign.Center, softWrap = false, mono = false)
        }
        Box(
            Modifier.at(x + 1f, 697f, boxW, 25f).clip(RoundedCornerShape(d(12.5f))).background(Color.White)
                .border(d(1f), Color(0xFFDFF3F5), RoundedCornerShape(d(12.5f))),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = if (bad) Color(0xFFE0574B) else FigmaInk, fontSize = fs(12.5f, false), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (i < n - 1) {
            DText(if (broken > 0) "−" else "+", x + boxW, 640f, gap, 30f, color = FigmaInk, align = androidx.compose.ui.text.style.TextAlign.Center, softWrap = false, mono = false)
        }
        x += boxW + gap
    }
}

/** «1 смену», «2 смены», «5 смен». */
private fun shiftsWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "смен"
        m10 == 1 -> "смену"
        m10 in 2..4 -> "смены"
        else -> "смен"
    }
}
