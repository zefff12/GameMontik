package ru.montik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.game.Life

/*
 * «План бюджета» из макета: распредели монеты кошелька на три части — обязательное, желаемое и копилку.
 * Кнопки «−» и «+» меняют часть на 10 монет; «Сохранить план» подтверждает план на период.
 * Числа с картинки стёрты и пишутся игрой.
 */

private const val PLAN_STEP = 10

@Composable
fun NewBudgetScreen(vm: GameViewModel) {
    val s = vm.state
    val plan = Life.currentPlan(s)
    val locked = plan?.confirmed == true
    val available = if (locked) plan!!.available else s.coins
    // Черновик: из плана периода или 50 / 30 / 20 от кошелька.
    fun round(v: Int) = v / PLAN_STEP * PLAN_STEP
    var needs by rememberSaveable { mutableIntStateOf(plan?.needs ?: round(available / 2)) }
    var wants by rememberSaveable { mutableIntStateOf(plan?.wants ?: round(available * 3 / 10)) }
    var savings by rememberSaveable { mutableIntStateOf(plan?.savings ?: round(available / 5)) }
    val total = needs + wants + savings
    val fb = rememberFeedback()
    val coins = rememberCountUp(s.coins)

    DesignCanvas {
        FrameArt("fg4_budget_clean")
        PressHit(22f, 46f, 52f, 52f, 26f) { vm.back() }
        // Монеты в шапке и «Распредели N монет».
        Box(Modifier.at(330f, 58f, 62f, 32f), contentAlignment = Alignment.Center) {
            Text(spaced(coins), color = Color(0xFF14246B), fontSize = fs(if (coins >= 10000) 15f else 19f, false), fontWeight = FontWeight.Black, maxLines = 1)
        }
        Box(Modifier.at(106f, 172f, 74f, 42f), contentAlignment = Alignment.Center) {
            Text(spaced(available), color = Color(0xFF1E7A2E), fontSize = fs(if (available >= 10000) 18f else 25f, false), fontWeight = FontWeight.Black, maxLines = 1)
        }
        fun change(delta: Int, get: () -> Int, set: (Int) -> Unit) {
            if (locked) {
                vm.say("План на этот период уже сохранён. Новый план — в следующем периоде.")
                return
            }
            val next = get() + delta
            when {
                next < 0 -> fb.bad()
                delta > 0 && total + delta > available -> {
                    fb.bad()
                    vm.say("Больше распределить нельзя: в кошельке $available монет.")
                }
                else -> { fb.tap(); set(next) }
            }
        }
        val rows = listOf(
            Triple(355f, { needs }, { v: Int -> needs = v }),
            Triple(454f, { wants }, { v: Int -> wants = v }),
            Triple(553f, { savings }, { v: Int -> savings = v })
        )
        for ((cy, get, set) in rows) {
            Box(Modifier.at(230f, cy - 15f, 52f, 30f), contentAlignment = Alignment.Center) {
                Text("${get()}", color = Color(0xFF14246B), fontSize = fs(17f, false), fontWeight = FontWeight.Black, maxLines = 1)
            }
            PressHit(291f, cy - 22f, 44f, 44f, 22f) { change(-PLAN_STEP, get, set) }
            PressHit(342f, cy - 22f, 44f, 44f, 22f) { change(PLAN_STEP, get, set) }
        }
        // «Распределено: N из M».
        Box(Modifier.at(108f, 656f, 80f, 40f), contentAlignment = Alignment.Center) {
            Text(
                spaced(total), color = if (total > available) Color(0xFFD84A3C) else Color(0xFF1E7A2E),
                fontSize = fs(if (total >= 10000) 19f else 26f, false), fontWeight = FontWeight.Black, maxLines = 1
            )
        }
        Box(Modifier.at(108f, 698f, 80f, 26f), contentAlignment = Alignment.Center) {
            Text("из ${spaced(available)}", color = Color(0xFF3B5578), fontSize = fs(13f, false), fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
        }
        PressHit(52f, 752f, 308f, 56f, 28f) {
            if (locked) vm.say("План на этот период уже сохранён.")
            else if (vm.setPlan(needs, wants, savings, true)) fb.coin() else fb.bad()
        }
    }
}
