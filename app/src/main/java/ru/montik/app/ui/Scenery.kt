package ru.montik.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ru.montik.app.game.GameState

/*
 * Какие картинки из макета показывать: комната и кухня зависят от уровня Монтика (жилья)
 * и от времени суток на его виртуальных часах. Если нужной картинки нет в проекте,
 * берётся ближайшая подходящая (в крайнем случае — комната первого уровня).
 *
 *   Уровень 1 — fg_room_morning / fg_room_day (переезд) / fg_room_dusk / fg_room_night (спит)
 *   Уровень 2 — fg_room2_day / _dusk / _evening / _night / _sleep
 *   Уровень 3 — fg_room3_day / _dusk / _evening / _night / _sleep
 *   Кухни     — fg_kitchen1_day / _dusk / _night, fg_kitchen2_…, fg_kitchen3_…
 */

private enum class DayTime { DAY, DUSK, EVENING, NIGHT }

private fun dayTime(hour: Int): DayTime = when (hour) {
    in 5..16 -> DayTime.DAY
    in 17..19 -> DayTime.DUSK
    in 20..22 -> DayTime.EVENING
    else -> DayTime.NIGHT
}

/** Первая картинка из списка, которая есть в проекте (или последняя — тогда покажется запасной фон). */
@Composable
private fun firstExisting(vararg names: String): String {
    val context = LocalContext.current
    val key = names.toList()
    return remember(key) {
        key.firstOrNull { context.resources.getIdentifier(it, "drawable", context.packageName) != 0 } ?: key.last()
    }
}

/** Комната на главном экране: Монтик бодрствует. */
@Composable
fun roomArt(state: GameState, hour: Int): String {
    val t = dayTime(hour)
    val level1 = when {
        t != DayTime.DAY -> "fg_room_dusk"
        state.day <= 1 -> "fg_room_day"
        else -> "fg_room_morning"
    }
    if (state.housing <= 1) return firstExisting(level1, "fg_room_morning")
    val p = state.home.room
    val wanted = when (t) {
        DayTime.DAY -> "${p}_day"
        DayTime.DUSK -> "${p}_dusk"
        DayTime.EVENING -> "${p}_evening"
        DayTime.NIGHT -> "${p}_night"
    }
    return firstExisting(wanted, "${p}_day", level1, "fg_room_morning")
}

/** Комната на экране сна: Монтик спит в своей кровати. */
@Composable
fun sleepArt(state: GameState): String =
    // В путешествии Монтик ночует в гостинице (кадр Android Compact 37).
    if (state.trip != null && rememberHasArt("fg_trip_hotel")) "fg_trip_hotel"
    else if (state.housing <= 1) "fg_room_night"
    else firstExisting("${state.home.room}_sleep", "${state.home.room}_night", "fg_room_night")

/** Утро после сна: Монтик стоит возле кровати. */
@Composable
fun wakeArt(state: GameState): String =
    if (state.trip != null && rememberHasArt("fg_trip_hotel")) "fg_trip_hotel"
    else if (state.housing <= 1) "fg_room_morning"
    else firstExisting("${state.home.room}_day", "fg_room_morning")

/** Кухня по уровню и времени суток. */
@Composable
fun kitchenArt(state: GameState, hour: Int): String {
    val p = state.home.kitchen
    val suffix = when (dayTime(hour)) {
        DayTime.DAY -> "day"
        DayTime.DUSK -> "dusk"
        DayTime.EVENING, DayTime.NIGHT -> "night"
    }
    return firstExisting("${p}_$suffix", "${p}_day", "fg_kitchen1_$suffix", "fg_kitchen1_day")
}
