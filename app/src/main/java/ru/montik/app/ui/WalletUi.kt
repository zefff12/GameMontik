package ru.montik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.GameEngine

/*
 * «Кошелёк» и «Подушка безопасности» из нового макета. Кошелёк — три большие кнопки:
 * «Копилка», «Подушка безопасности», «План бюджета». Подушка — отложить и взять монеты.
 */

@Composable
fun NewWalletScreen(vm: GameViewModel) {
    DesignCanvas {
        FrameArt("fg3_wallet_bg")
        val p0 = rememberSlide(0)
        val p1 = rememberSlide(1)
        val p2 = rememberSlide(2)
        ArtPart("fg3_wallet_i634", 27f, 13f, 360f, 240f)
        ArtPart("fg3_wallet_i631", 27f, 240f, 358f, 154f, Modifier.slide(p0))
        ArtPart("fg3_wallet_i632", 19f, 381f, 366f, 156f, Modifier.slide(p1))
        ArtPart("fg3_wallet_i633", 24f, 537f, 361f, 134f, Modifier.slide(p2))
        // В кадре нет стрелки «назад» — выход системной кнопкой «назад» или нажатием на заголовок.
        Hit(60f, 30f, 290f, 200f) { vm.back() }
        PressHit(60f, 280f, 310f, 80f, 40f) { vm.goTo(Screen.Goals) }
        PressHit(55f, 425f, 315f, 80f, 40f) { vm.goTo(Screen.Cushion) }
        PressHit(55f, 570f, 315f, 80f, 40f) { vm.goTo(Screen.Budget) }
    }
}

@Composable
fun NewCushionScreen(vm: GameViewModel) {
    val s = vm.state
    val cushion = rememberCountUp(s.cushion)
    val coins = rememberCountUp(s.coins)
    val fb = rememberFeedback()
    DesignCanvas {
        FrameArt("fg3_cushion_bg", 0f, 0f, 529f, 940f)
        ArtPart("fg3_cushion_i637_clean", 7f, 25f, 399f, 133f)
        ArtPart("fg3_cushion_i638", 18f, 158f, 376f, 175f)
        ArtPart("fg3_cushion_i640_clean", 16f, 333f, 380f, 202f)
        ArtPart("fg3_cushion_i642", 8f, 535f, 396f, 148f)
        PressHit(14f, 62f, 56f, 54f, 27f) { vm.back() }
        // В шапке — сколько уже в подушке; в карточке «Отложить» — сколько в кошельке.
        Box(Modifier.at(347f, 79f, 44f, 28f), contentAlignment = Alignment.Center) {
            Text("$cushion", color = Color(0xFF4A1C0E), fontSize = fs(if (cushion >= 1000) 15f else 19f, false), fontWeight = FontWeight.Black, maxLines = 1)
        }
        Box(Modifier.at(219f, 389f, 110f, 24f), contentAlignment = Alignment.CenterStart) {
            Text(": $coins ${coinWord(coins)}", color = Color(0xFF14246B), fontSize = fs(12.5f, false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        fun put(amount: Int) {
            if (amount <= 0 || s.coins < amount) vm.say("В кошельке ${s.coins} ${coinWord(s.coins)} — столько не отложить.")
            else { fb.coin(); vm.cushionIn(amount) }
        }
        PressHit(46f, 431f, 104f, 37f, 18f) { put(10) }
        PressHit(156f, 431f, 104f, 37f, 18f) { put(20) }
        PressHit(263f, 431f, 102f, 37f, 18f) { put(50) }
        PressHit(36f, 474f, 324f, 52f, 26f) { put(s.coins) }
        fun take(amount: Int) {
            if (amount <= 0 || s.cushion <= 0) vm.say("В подушке пока пусто.")
            else { fb.tap(); vm.cushionOut(minOf(amount, s.cushion)) }
        }
        PressHit(22f, 643f, 165f, 41f, 20f) { take(10) }
        PressHit(196f, 640f, 194f, 46f, 23f) { take(s.cushion) }
    }
}

@Composable
private fun DesignScope.ArtPart(name: String, x: Float, y: Float, w: Float, h: Float, modifier: Modifier = Modifier) {
    Box(Modifier.at(x, y, w, h).then(modifier)) {
        ArtImage(name, androidx.compose.ui.Modifier.matchParentSize(), androidx.compose.ui.layout.ContentScale.FillBounds) {}
    }
}

@Suppress("unused")
private val keepEngine = GameEngine
