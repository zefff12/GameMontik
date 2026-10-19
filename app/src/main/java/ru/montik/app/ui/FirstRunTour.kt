package ru.montik.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.montik.app.GameViewModel

/*
 * Знакомство с игрой для нового ребёнка: по шагам рассказываем, что где находится и куда нажать.
 * Показывается на главном экране, сразу после окна «Как играть». Номер шага хранится в состоянии игры
 * (GameState.tourStep): 99 — знакомство уже пройдено.
 */

internal val TOUR_STEPS = listOf(
    "Это комната Монтика. Здесь он живёт, ест и отдыхает. Нажми «Дальше» — я покажу, где что.",
    "Сверху — твои монеты. На них Монтик покупает еду и платит за квартиру.",
    "Круглые кнопки внизу — Голод, Сон и Усталость. Если показатель мал, Монтик слабеет: его надо покормить и уложить спать.",
    "📱 Телефон — здесь всё остальное: Работа, Банк, Магазин, Копилка и Путешествия. Заходи туда, когда нужно заработать.",
    "Чтобы заработать, открой 📱 Телефон и выбери 💼 Работу. Монтик выйдет на смену, а монеты придут в кошелёк.",
    "Кухня — переключатель «Кухня / Комната». Там холодильник: продукты из магазина появляются на полках.",
    "Вот и всё! Дальше Монтик будет подсказывать сам. Удачи!"
)

@Composable
fun FirstRunTour(vm: GameViewModel) {
    // Плашка знакомства из макета — без наложенного текста: «Дальше» и «Пропустить» закрывают её.
    NewTourPopup(onDone = { vm.finishTour() })
}
