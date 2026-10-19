package ru.montik.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Goals
import ru.montik.app.game.Housing
import ru.montik.app.game.Life

/*
 * Экраны по кадрам Figma: «Жильё и уровень» (Frame 47), «Копилка» (Android Compact - 77),
 * «Путешествия» (Frame 48). Кадр — фон один в один; всё, что меняется в игре (монеты, цены, аренда,
 * дни до платежа, выбранная цель и город, закрытые варианты), — настоящие элементы на месте нарисованных.
 * Координаты — в единицах кадра 412×917.
 */

/** «1 день», «2 дня», «5 дней». */
private fun daysWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "дней"
        m10 == 1 -> "день"
        m10 in 2..4 -> "дня"
        else -> "дней"
    }
}

/** Рамка выбора поверх карточки макета: зелёная обводка появляется с пружинкой. */
@Composable
private fun DesignScope.PickedFrame(x: Float, y: Float, w: Float, h: Float, picked: Boolean, radius: Float = 22f) {
    val k by animateFloatAsState(if (picked) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "picked")
    Box(
        Modifier.at(x - 3f, y - 3f, w + 6f, h + 6f)
            .graphicsLayer { alpha = k; scaleX = 0.97f + 0.03f * k; scaleY = 0.97f + 0.03f * k }
            .border(d(4f), Color(0xFF4DB33A), RoundedCornerShape(d(radius + 3f)))
    )
}

/** Жильё и уровень: кадр «Frame 47». Уровень, аренда и дни до платежа — из игры; переезд по нажатию на уровень. */
@Composable
fun FigmaHousingScreen(vm: GameViewModel) {
    val s = vm.state
    val home = s.home
    val coins = rememberCountUp(s.coins)
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFF8CCB5E))) }) {
        FrameArt("fg_figma_housing")
        PressHit(18f, 94f, 44f, 38f, 19f) { vm.back() }
        LiveText("$coins", 338f, 101f, 54f, 26f, Color(0xFFFDFDFD), 22f)

        // Текущее жильё: плашка уровня, название, фото комнаты.
        LiveText("Уровень ${home.level}", 32f, 161f, 104f, 22f, Color(0xFFBDED8F), 14f)
        LiveText(home.title, 28f, 194f, 112f, 48f, Color(0xFFFDFDED), 17f, align = Alignment.CenterStart, maxLines = 2)
        if (home.level != 1) {
            Box(Modifier.at(142f, 150f, 250f, 214f).clip(RoundedCornerShape(d(18f)))) {
                ArtImage(if (home.level == 2) "fg_room2_day" else "fg_room3_day", Modifier.fillMaxSize(), ContentScale.Crop) {}
            }
        }

        // Аренда и дни до платежа.
        LiveText("${home.rent}", 98f, 386f, 56f, 26f, Color(0xFFFDFDF3), 19f, align = Alignment.CenterStart)
        val days = Life.daysUntilRent(s)
        LiveText("$days ${daysWord(days)}", 278f, 386f, 96f, 26f, Color(0xFFFDFDF3), 17f, align = Alignment.CenterStart)

        // Действие с арендой — если нужно: погасить долг или оплатить заранее.
        when {
            s.rentDebt > 0 -> Badge("Погасить долг ${s.rentDebt}", 150f, 326f, 236f, 34f, Color(0xFFE0574B)) { vm.payRentDebt() }
            !s.rentPaidAhead && Life.rentCountdown(s) && s.coins >= home.rent ->
                Badge("Оплатить заранее −${home.rent}", 150f, 326f, 236f, 34f, Color(0xFF4DB33A)) { vm.payRentAhead() }
            s.rentPaidAhead -> Badge("✓ Аренда внесена", 200f, 326f, 186f, 34f, Color(0xFF4DB33A))
        }

        // Уровни: текущий — зелёная рамка, открытые — «Переехать», закрытые — замок и цена переезда.
        val cards = mapOf(1 to Triple(16f, 126f, 204f), 2 to Triple(148f, 124f, 184f), 3 to Triple(276f, 122f, 184f))
        for (h in Housing.values()) {
            val (x, w, hgt) = cards[h.level] ?: continue
            val current = h.level == s.housing
            val blocker = Life.moveBlocker(s, h)
            PickedFrame(x, 466f, w, hgt, current, 16f)
            if (h.level != 1) LiveText("${h.moveCost}", x + 42f, 619f, 40f, 22f, Color(0xFFA19E93), 15f, Color.White)
            if (current && h.level != 1) Badge("✓ Здесь живёт", x + 6f, 566f, w - 12f, 26f, Color(0xFF4DB33A), size = 11.5f)
            if (!current && blocker == null) Badge("Переехать", x + 10f, 566f, w - 20f, 26f, Color(0xFF4DB33A), size = 12f) { vm.moveTo(h.level) }
            if (!current) {
                PressHit(x, 466f, w, hgt, 16f) { if (blocker == null) vm.moveTo(h.level) else vm.say(blocker) }
            }
        }
    }
}

/**
 * Копилка: кадр «Android Compact - 77». Пополнение, снятие и выбор цели работают как раньше;
 * «Снять из копилки» открывает прежний диалог снятия — его показывает [onWithdraw].
 */
@Composable
fun FigmaPiggyScreen(vm: GameViewModel, onWithdraw: () -> Unit) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val piggy = rememberCountUp(s.piggy)
    val goal = Goals.current(s)
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFF2D9B8))) }) {
        FrameArt("fg_figma_piggy")
        PressHit(22f, 38f, 48f, 40f, 20f) { vm.back() }
        LiveText("$coins", 338f, 45f, 54f, 27f, Color(0xFFFEFEFE), 22f)

        // Сколько уже в копилке — под свинкой.
        Badge(
            if (goal != null) "В копилке $piggy из ${goal.cost}" else "В копилке $piggy",
            62f, 222f, 200f, 28f, Color.White, FigmaInk, 13.5f
        )

        // Пополнить: +10, +20, +50, всё. Неактивные кнопки бледнеют.
        val adds = listOf(Triple(26f, 89f, 10), Triple(118f, 89f, 20), Triple(210f, 88f, 50))
        for ((x, w, amount) in adds) {
            val ok = s.coins >= amount
            if (!ok) Box(Modifier.at(x, 257f, w, 38f).clip(RoundedCornerShape(d(19f))).background(Color(0x99FFFFFF)))
            PressHit(x, 257f, w, 38f, 19f, enabled = ok) { vm.piggyIn(amount) }
        }
        if (s.coins <= 0) Box(Modifier.at(302f, 257f, 84f, 38f).clip(RoundedCornerShape(d(19f))).background(Color(0x99FFFFFF)))
        PressHit(302f, 257f, 84f, 38f, 19f, enabled = s.coins > 0) { vm.piggyIn(s.coins) }
        if (s.piggy <= 0) Box(Modifier.at(26f, 312f, 360f, 45f).clip(RoundedCornerShape(d(22f))).background(Color(0x99FFFFFF)))
        PressHit(26f, 312f, 360f, 45f, 22f, enabled = s.piggy > 0) { onWithdraw() }

        // Цели: цена из игры, выбранная — в рамке, накоплено достаточно — «Купить».
        val rows = listOf("moscow" to 422f, "kazan" to 522f, "spb" to 624f, "sochi" to 726f)
        for ((trip, y) in rows) {
            val g = Goals.all.firstOrNull { it.tripId == trip } ?: continue
            val blocker = Goals.blocker(s, g)
            val chosen = g.id == s.goalId
            LiveText("${g.cost}", 316f, y + 36f, 46f, 26f, Color(0xFFFEF8EE), 18f, align = Alignment.CenterStart)
            PickedFrame(18f, y, 380f, 92f, chosen, 20f)
            when {
                chosen && s.piggy >= g.cost -> Badge("Купить билет ›", 196f, y + 62f, 128f, 26f, Color(0xFF4DB33A)) { vm.reachGoal() }
                chosen -> Badge("✓ Копим", 236f, y + 62f, 86f, 26f, Color(0xFF4DB33A))
                blocker != null -> Badge("🔒 закрыто", 222f, y + 62f, 100f, 26f, Color(0xE6202228), size = 12f)
            }
            PressHit(18f, y, 380f, 92f, 20f) {
                when {
                    chosen && s.piggy >= g.cost -> vm.reachGoal()
                    chosen -> Unit
                    blocker == null -> vm.chooseGoal(g.id)
                    else -> vm.say(blocker)
                }
            }
        }
    }
}

/**
 * Путешествия: кадр «Frame 48» — карта, список городов и «Купить билет». Выбор города — [picked].
 */
@Composable
fun FigmaTravelList(vm: GameViewModel, picked: String?, onPick: (String) -> Unit) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val chosen = picked?.let { Destinations.byId(it) }
    val status = chosen?.let { GameEngine.destinationStatus(s, it) }
    val canBuy = chosen != null && status == null && s.coins >= chosen.ticket
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFF9ED483))) }) {
        FrameArt("fg_figma_travel")

        // В кадре нет стрелки «назад» — добавляем круглую, в стиле игры.
        Box(
            Modifier.at(14f, 22f, 44f, 44f).shadow(d(3f), CircleShape).clip(CircleShape).background(Color.White)
                .border(d(2f), Color(0xFFBFE3F7), CircleShape),
            contentAlignment = Alignment.Center
        ) { ChildBackArrow() }
        PressHit(14f, 22f, 44f, 44f, 22f) { vm.goTo(Screen.Home) }
        LiveText("$coins", 331f, 79f, 50f, 26f, Color(0xFFFDFDFE), 22f)

        // Города: строка в списке и булавка на карте.
        val rows = listOf("spb" to 490f, "kazan" to 556f, "moscow" to 620f, "nn" to 684f, "sochi" to 748f)
        val pins = mapOf("spb" to (103f to 302f), "moscow" to (140f to 370f), "nn" to (248f to 362f), "kazan" to (298f to 436f), "sochi" to (117f to 460f))
        for ((id, y) in rows) {
            val dest = Destinations.byId(id) ?: continue
            val st = GameEngine.destinationStatus(s, dest)
            val done = id in s.completedTrips
            LiveText("${dest.ticket}", 220f, y + 25f, 58f, 22f, Color(0xFFFEFEFB), 16f, align = Alignment.Center)
            PickedFrame(18f, y, 380f, 60f, id == picked, 14f)
            when {
                done -> Badge("✓ Был", 318f, y + 18f, 70f, 24f, Color(0xFF4DB33A), size = 12f)
                st != null -> Badge("🔒", 340f, y + 18f, 44f, 24f, Color(0xE6202228), size = 12f)
            }
            PressHit(18f, y, 380f, 60f, 14f) { onPick(id) }
            pins[id]?.let { (px, py) ->
                // Выбранная булавка на карте подпрыгивает.
                val k by animateFloatAsState(if (id == picked) 1f else 0f, spring(dampingRatio = Spring.DampingRatioHighBouncy), label = "pin")
                Box(
                    Modifier.at(px - 13f, py - 13f, 26f, 26f)
                        .graphicsLayer { alpha = k; scaleX = 0.4f + 0.6f * k; scaleY = 0.4f + 0.6f * k }
                        .border(d(3f), Color(0xFFFFD43B), CircleShape)
                )
                PressHit(px - 16f, py - 16f, 70f, 32f, 16f) { onPick(id) }
            }
        }

        // Кнопка «Купить билет»: подпись и цена — по выбранному городу.
        LiveText(
            when {
                chosen == null -> "Выбери город"
                status != null -> "🔒 Пока закрыто"
                s.coins < chosen.ticket -> "Не хватает ${chosen.ticket - s.coins}"
                else -> "Купить билет −${chosen.ticket}"
            },
            158f, 824f, 184f, 30f, Color(0xFF3FA530), 15f, Color.White, radius = 15f
        )
        if (!canBuy) Box(Modifier.at(150f, 818f, 198f, 40f).clip(RoundedCornerShape(d(20f))).background(Color(0x66FFFFFF)))
        PressHit(150f, 818f, 198f, 40f, 20f) {
            when {
                chosen == null -> vm.say("Сначала выбери город на карте или в списке.")
                status != null -> vm.say(status)
                s.coins < chosen.ticket -> vm.say("Не хватает ${chosen.ticket - s.coins} монет на билет.")
                else -> vm.startTrip(chosen.id)
            }
        }
    }
}
