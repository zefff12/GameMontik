package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Catalog
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Life
import ru.montik.app.game.PeriodResult
import ru.montik.app.game.Progress
import ru.montik.app.game.Tasks
import ru.montik.app.game.VirtualClock

/*
 * Общие детали двух главных экранов — комнаты и кухни — по макету Figma:
 * время, монеты, плитка «Работа», столбик круглых кнопок, а поверх — то, что ТЗ требует видеть сразу:
 * уровень, настроение, квартира, накопления, цель и активное задание.
 */


/** Белая пилюля-подпись на картинке (время, уровень, настроение, квартира). */
@Composable
fun DesignScope.HudChip(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float = 40f,
    tint: Color = Color.White.copy(alpha = 0.9f),
    textColor: Color = MontikColors.Ink,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .at(x, y, w, h)
            .clip(RoundedCornerShape(d(h / 2f)))
            .background(tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = fs(13.5f, false),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = d(8f))
        )
    }
}

/** Всё, что лежит поверх картинки комнаты или кухни. [minutes] — время суток на часах Монтика. */
@Composable
fun DesignScope.HomeHud(vm: GameViewModel, minutes: Int, helpY: Float = 556f, helpX: Float = 346f) {
    val s = vm.state
    // Без картинок макета плашку монет, «Работу» и круглые кнопки рисуем простыми пилюлями.
    val figma = rememberHasArt("fg_room_coins", "fg_room_buttons", "fg_room_work")

    // Три плашки из макета (Group 21): время суток, уровень с шкалой опыта и жильём, настроение.
    StatusPills(vm, minutes)

    // Монеты растут — монетки летят в кошелёк, число «докручивается».
    val fb = rememberFeedback()
    val shownCoins = remember { Animatable(vm.hudCoins.toFloat()) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(s.coins) {
        if (s.coins > vm.hudCoins) {
            burst++
            fb.coin()
            delay(450)
            shownCoins.animateTo(s.coins.toFloat(), tween(700))
        } else {
            shownCoins.snapTo(s.coins.toFloat())
        }
        vm.hudCoins = s.coins
    }
    val coinsText = spaced(shownCoins.value.toInt())

    // Монеты (плашка из макета) — число подставляем своё; «+» — заработать.
    if (!figma) {
        HudChip("🪙 $coinsText  ➕", 226f, 36f, 174f) { vm.goTo(Screen.Work) }
        HudChip("💼 Работа", 8f, 196f, 130f) { vm.goTo(Screen.Work) }
        HudChip("🍔 Кухня", 286f, 206f, 118f) { vm.goTo(Screen.Kitchen) }
        HudChip("💤 Сон", 286f, 256f, 118f) { vm.goTo(Screen.Sleep) }
        HudChip("📱 Телефон", 286f, 306f, 118f) { vm.goTo(Screen.Phone) }
        HudChip("👛 План", 286f, 356f, 118f) { vm.goTo(Screen.Budget) }
    }

    if (figma) Art("fg_room_coins", 217f, 29f, 193f, 64f, ContentScale.FillBounds)
    if (figma) Box(
        Modifier
            .at(281f, 44f, 70f, 30f)
            .background(Brush.verticalGradient(listOf(Color(0xFFFEFEFE), Color(0xFFFCF2DC)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            coinsText,
            color = Color(0xFF1D0B0A),
            fontSize = fs(22f, false),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
    if (figma) {
        Hit(350f, 37f, 48f, 44f) { vm.goTo(Screen.Work) }
        // «Работа» слева.
        Art("fg_room_work", 0f, 176f, 114f, 118f, ContentScale.Fit)
        Hit(6f, 182f, 90f, 106f) { vm.goTo(Screen.Work) }
    }

    // Квартира: отсчёт до оплаты или долг.
    val rent = Life.rentNotice(s)
    if (rent != null) {
        HudChip(
            rent, 8f, 298f, 300f,
            tint = if (s.rentDebt > 0) Color(0xFFFFE1DD) else Color(0xFFFFF4D6)
        ) { vm.goTo(Screen.Housing) }
    }
    // Демо-режим: перемотка к следующему периоду без ожидания.
    if (s.demo) {
        HudChip("⏩ Следующий период", 8f, 344f, 190f, tint = Color(0xFFDDF3FF)) { vm.skipPeriod() }
    }

    // Столбик круглых кнопок справа (новый макет, без «Усталости»): Голод, Сон, Телефон, Кошелёк.
    // Полоски под «Голодом» и «Сном» живые: сытость и силы Монтика.
    if (figma) {
        Art("fg_room_buttons", 242f, 193f, 241f, 361f, ContentScale.FillBounds)
        MeterBar(s.food, 247.6f, Color(0xFFFF8A1F), Color(0xFFF04E00))
        MeterBar(s.energy, 331.6f, Color(0xFF3D7BFF), Color(0xFF0050F0))
        Hit(328f, 195f, 68f, 80f) { vm.goTo(Screen.Kitchen) }
        Hit(328f, 280f, 68f, 80f) { vm.goTo(Screen.Sleep) }
        Hit(328f, 366f, 68f, 82f) { vm.goTo(Screen.Phone) }
        Hit(328f, 452f, 68f, 88f) { vm.goTo(Screen.Budget) }
    }

    CoinBurst(burst, 206f, 560f, 262f, 60f)

    // «?» — подсказка про три решения, доступна в любой момент.
    Box(
        Modifier
            .at(helpX, helpY, 48f, 48f)
            .clip(CircleShape)
            .background(Color.White)
            .border(d(3f), MontikColors.Lime, CircleShape)
            .clickable { vm.openHelp() },
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = MontikColors.Ink, fontSize = fs(24f, false), fontWeight = FontWeight.Bold)
    }
}

/** Полоска на кнопке «Голод» или «Сон» (картинка 1024×1536 → 241×361 в точке 242, 193): тёмная дорожка и заливка. */
@Composable
private fun DesignScope.MeterBar(value: Int, y: Float, from: Color, to: Color) {
    Box(
        Modifier.at(342.7f, y, 38.1f, 7.5f)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF6C605C))
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0, 100) / 100f)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(from, to)))
        )
    }
}

/**
 * Нижняя панель главного экрана — слой Group 20 из макета (743×562, здесь в масштабе 0,53):
 * монеты · накоплено · план, строка цели и активного задания, кнопки «План», «Копилка», «Задания»
 * и зелёная «Кухня >». На кухне остаётся только зелёная кнопка «Комната».
 */
@Composable
fun DesignScope.InfoPanel(vm: GameViewModel, kitchen: Boolean) {
    if (kitchen) {
        GreenPanelButton("Комната", 294f, 856f, 106f, 40f) { vm.goTo(Screen.Home) }
        return
    }
    if (rememberHasArt("fg_panel_main")) MainPanel(vm) else InfoPanelPlain(vm)
}

/**
 * Нижняя панель из нового макета («image 274» в кадре «Android Compact - 7», x 6, y 585, 406×306).
 * Рамка, иконки, «накоплено», плитки «План / Копилка / Задания» и «Кухня >» — картинка fg_panel_main
 * (исходник 1443×1090); число монет, накопленное, «план есть» и две строки сообщений с неё стёрты
 * и пишутся игрой рукописным шрифтом Pangolin — как в макете.
 */
@Composable
private fun DesignScope.MainPanel(vm: GameViewModel) {
    val s = vm.state
    val k = PANEL_W / 1443f
    fun X(px: Float) = PANEL_X + px * k
    fun Y(px: Float) = PANEL_Y + px * k
    Art("fg_panel_main", PANEL_X, PANEL_Y, PANEL_W, 1090f * k, ContentScale.FillBounds)

    // Верхняя строка: монеты · накоплено · план.
    val coins = spaced(s.coins)
    HandText(coins, X(305f), Y(240f), 150f * k, if (coins.length > 5) 15.5f else 22f, bold = true)
    HandText(spaced(s.savings), X(690f), Y(258f), 180f * k, if (s.savings >= 10000) 17f else 22f, bold = true, align = TextAlign.End)
    val noPlan = Life.planMissing(s)
    HandText(if (noPlan) "нет плана!" else "план есть", X(1103f), Y(238f), 205f * k, 13.5f,
        color = if (noPlan) MontikColors.Bad else PanelInk)
    Hit(X(120f), Y(150f), 380f * k, 180f * k) { vm.goTo(Screen.Work) }
    Hit(X(515f), Y(150f), 405f * k, 180f * k) { vm.goTo(Screen.Goals) }
    Hit(X(935f), Y(150f), 400f * k, 180f * k) { vm.goTo(Screen.Budget) }

    // Сообщения: цель (кубок) и задание (молния).
    HandText(GameEngine.currentGoal(s), X(322f), Y(465f), 1000f * k, 13.5f, lines = 2)
    val task = Tasks.active(s)
    HandText(if (task != null) "Задание: ${task.title}" else "Все открытые задания пройдены!",
        X(322f), Y(603f), 1000f * k, 13.5f, lines = 2)
    Hit(X(100f), Y(540f), 1250f * k, 130f * k) { vm.goTo(Screen.Tasks) }

    // Плитки и «Кухня >» нарисованы на картинке — здесь только нажатия.
    Hit(X(105f), Y(725f), 252f * k, 232f * k) { vm.goTo(Screen.Budget) }
    Hit(X(378f), Y(725f), 258f * k, 232f * k) { vm.goTo(Screen.Goals) }
    Hit(X(655f), Y(725f), 252f * k, 232f * k) { vm.goTo(Screen.Tasks) }
    Hit(X(960f), Y(755f), 372f * k, 185f * k) { vm.goTo(Screen.Kitchen) }
}

private const val PANEL_X = 6f
private const val PANEL_Y = 585f
private const val PANEL_W = 406f
private val PanelInk = Color(0xFF14261A)


/** Надпись панели: левый край [x], середина строки по [cy], ширина [w]. */
@Composable
private fun DesignScope.HandText(
    text: String,
    x: Float,
    cy: Float,
    w: Float,
    size: Float,
    color: Color = PanelInk,
    align: TextAlign = TextAlign.Start,
    lines: Int = 1,
    bold: Boolean = false
) {
    val h = size * 1.5f * lines
    Box(Modifier.at(x, cy - h / 2f, w, h), contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            fontSize = fs(size, false),
            lineHeight = fs(size * 1.2f, false),
            fontFamily = MontikFont,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            maxLines = lines,
            softWrap = lines > 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Прежняя панель Group 20 — если картинки новой панели нет. */
@Composable
private fun DesignScope.InfoPanelPlain(vm: GameViewModel) {
    val s = vm.state
    val k = 392f / 743f
    val ox = 10f
    val oy = DESIGN_H - 562f * k - 4f
    fun X(v: Float) = ox + v * k
    fun Y(v: Float) = oy + v * k
    val cream = Color(0xFFFAF9EA)
    val green = Color(0xFF62B140)

    Box(Modifier.at(X(0f), Y(17f), 743f * k, 545f * k).clip(RoundedCornerShape(d(50f * k))).background(green))
    Box(Modifier.at(X(23f), Y(0f), 697f * k, 545f * k).clip(RoundedCornerShape(d(50f * k))).background(cream))

    // Верхняя пилюля: монеты · накоплено · план.
    Box(
        Modifier.at(X(43f), Y(44f), 657f * k, 132f * k)
            .shadow(d(2f), RoundedCornerShape(d(70f * k)))
            .clip(RoundedCornerShape(d(70f * k)))
            .background(Color(0xFFF5F4E0))
    )
    Box(Modifier.at(X(76f), Y(81f), 59f * k, 59f * k)) { CoinIcon(Modifier.fillMaxSize()) }
    DText(spaced(s.coins), X(152f), Y(88f), 110f * k, 30f * k, softWrap = false)
    Box(Modifier.at(X(264f), Y(66f), 2f * k, 87f * k).background(Color(0xFFC8B99A)))
    Art("fg_panel_piggy", X(292f), Y(81f), 59f * k, 59f * k, ContentScale.Fit)
    DText("накоплено", X(354f), Y(72f), 110f * k, 19f * k, bold = false, softWrap = false)
    DText(spaced(s.savings), X(354f), Y(104f), 92f * k, 30f * k, align = TextAlign.End, softWrap = false)
    Box(Modifier.at(X(477f), Y(66f), 2f * k, 87f * k).background(Color(0xFFC8B99A)))
    Art("fg_panel_list", X(496f), Y(88f), 44f * k, 44f * k, ContentScale.Fit)
    DText(if (Life.planMissing(s)) "составь план!" else "план есть", X(558f), Y(98f), 140f * k, 19f * k, bold = false, softWrap = false,
        color = if (Life.planMissing(s)) MontikColors.Bad else MontikColors.Ink)
    Hit(X(43f), Y(44f), 222f * k, 132f * k) { vm.goTo(Screen.Work) }
    Hit(X(266f), Y(44f), 212f * k, 132f * k) { vm.goTo(Screen.Goals) }
    Hit(X(478f), Y(44f), 222f * k, 132f * k) { vm.goTo(Screen.Budget) }

    // Средний блок: цель и активное задание.
    Box(
        Modifier.at(X(43f), Y(197f), 657f * k, 184f * k)
            .shadow(d(2f), RoundedCornerShape(d(40f * k)))
            .clip(RoundedCornerShape(d(40f * k)))
            .background(Color(0xFFF5F4EF))
    )
    Art("fg_panel_cup", X(60f), Y(225f), 59f * k, 59f * k, ContentScale.Fit)
    PanelText(GameEngine.currentGoal(s), X(131f), Y(222f), 540f * k)
    Art("fg_panel_energy", X(60f), Y(309f), 59f * k, 59f * k, ContentScale.Fit)
    val task = Tasks.active(s)
    PanelText(if (task != null) "Задание: ${task.title}" else "Все открытые задания пройдены!", X(131f), Y(312f), 540f * k)
    Hit(X(43f), Y(290f), 657f * k, 90f * k) { vm.goTo(Screen.Tasks) }

    // Кнопки «План», «Копилка», «Задания» и зелёная «Кухня >».
    PanelTile("План", "fg_panel_list", 44f, X(43f), Y(406f), k) { vm.goTo(Screen.Budget) }
    PanelTile("Копилка", "fg_panel_piggy", 59f, X(210f), Y(406f), k) { vm.goTo(Screen.Goals) }
    PanelTile("Задания", "fg_panel_tasks", 59f, X(377f), Y(406f), k) { vm.goTo(Screen.Tasks) }
    GreenPanelButton("Кухня", X(544f), Y(430f), 156f * k, 68f * k) { vm.goTo(Screen.Kitchen) }
}

@Composable
private fun DesignScope.PanelText(text: String, x: Float, y: Float, w: Float) {
    Text(
        text,
        modifier = Modifier.at(x, y).width(d(w)),
        color = MontikColors.Ink,
        fontSize = fs(12.5f),
        lineHeight = fs(15f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DesignScope.PanelTile(label: String, icon: String, iconSize: Float, x: Float, y: Float, k: Float, onClick: () -> Unit) {
    val w = 122f * k
    val h = 116f * k
    Box(
        Modifier.at(x, y, w, h)
            .shadow(d(2f), RoundedCornerShape(d(40f * k)))
            .clip(RoundedCornerShape(d(40f * k)))
            .background(Color(0xFFECF2DE))
            .clickable(onClick = onClick)
    )
    Art(icon, x + (w - iconSize * k) / 2f, y + (if (iconSize < 50f) 24f else 12f) * k, iconSize * k, iconSize * k, ContentScale.Fit)
    DText(label, x, y + 78f * k, w, 19f * k, bold = false, align = TextAlign.Center, softWrap = false)
}

/** Зелёная кнопка макета «Кухня >» (#62B140, белый текст). */
@Composable
fun DesignScope.GreenPanelButton(text: String, x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, w, h)
            .shadow(d(3f), RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF62B140))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$text  ›", color = Color.White, fontSize = fs(15f), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * Запасной вариант (если картинок пузырей нет): три плашки Group 21, масштаб 0,42: часы и время суток; уровень опыта, достаток и шкала;
 * настроение со смайликом. Уровень — к разделу «Жильё», настроение — объяснение причины.
 */
@Composable
private fun DesignScope.StatusPillsPlain(vm: GameViewModel, minutes: Int) {
    val s = vm.state
    val k = STATUS_K
    val hour = minutes / 60
    val part = when (hour) {
        in 5..11 -> "Утро"
        in 12..16 -> "День"
        in 17..21 -> "Вечер"
        else -> "Ночь"
    }
    // 1. Время.
    var y = 18f
    StatusPillBg(y, 421f) { vm.say("Сейчас ${VirtualClock.format(minutes)}, день ${s.day}. Игровые сутки идут примерно 10 минут.") }
    Art("fg_hud_clock", 8f + 20f * k, y + 31f * k, 49f * k, 49f * k, ContentScale.Fit)
    DText(VirtualClock.format(minutes), 8f + 84f * k, y + 34f * k, 120f * k, 30f * k, softWrap = false)
    Box(Modifier.at(8f + 218f * k, y + 25f * k, 2f * k, 60f * k).background(Color(0xFFC8B99A)))
    DText(part, 8f + 250f * k, y + 34f * k, 110f * k, 30f * k, softWrap = false)
    DText("›", 8f + 372f * k, y + 30f * k, 24f * k, 30f * k, bold = false, softWrap = false)
    // 2. Уровень, достаток и шкала опыта.
    y += 124f * k
    val level = Progress.levelOf(s.xp)
    StatusPillBg(y, 482f) { vm.goTo(Screen.Housing) }
    Art("fg_hud_signal", 8f + 20f * k, y + 25f * k, 49f * k, 49f * k, ContentScale.Fit)
    DText("Уровень $level", 8f + 81f * k, y + 22f * k, 220f * k, 30f * k, softWrap = false)
    DText(s.home.levelTitle.substringBefore(' '), 8f + 290f * k, y + 22f * k, 150f * k, 30f * k, align = TextAlign.End, softWrap = false)
    DText("›", 8f + 446f * k, y + 18f * k, 24f * k, 30f * k, bold = false, softWrap = false)
    Box(Modifier.at(8f + 82f * k, y + 74f * k, 349f * k, 13f * k).clip(RoundedCornerShape(50)).background(Color(0xFFD8F1D9))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(Progress.fraction(s.xp).coerceIn(0.08f, 1f))
                .clip(RoundedCornerShape(50)).background(Color(0xFF62B140))
        )
    }
    // 3. Настроение.
    y += 124f * k
    StatusPillBg(y, 482f) { vm.say(Life.moodReason(s)) }
    val face = when {
        s.mood >= 60 -> "fg_hud_smile"
        s.mood >= 30 -> "fg_hud_neutral"
        else -> "fg_hud_angry"
    }
    Art(face, 8f + 20f * k, y + 31f * k, 49f * k, 49f * k, ContentScale.Fit)
    DText("${Life.moodTitle(s.mood)} настроение", 8f + 81f * k, y + 34f * k, 390f * k, 30f * k, softWrap = false)
}

// ───────────────────────── Пузыри в левом верхнем углу ─────────────────────────

/**
 * Левый верхний угол главного экрана — три круглых пузыря из макета («image 243», x 0, y 21, 198×132):
 * часы с временем суток, уровень с домиком и достатком жилья, настроение с росточком и смайликом.
 * Для утра, дня, вечера и ночи — своя картинка (fg_hud_morning/day/evening/night, 1536×1024),
 * надписи с неё стёрты: время, уровень и слова рисуем сами поверх, по местам надписей макета.
 * Нажатия: часы — сколько времени, уровень — раздел «Жильё», настроение — почему оно такое.
 */
@Composable
fun DesignScope.StatusPills(vm: GameViewModel, minutes: Int) {
    if (!rememberHasArt("fg_hud_morning", "fg_hud_day", "fg_hud_evening", "fg_hud_night")) {
        StatusPillsPlain(vm, minutes)
        return
    }
    val s = vm.state
    val v = hudVariant(minutes / 60)
    val k = HUD_W / 1536f
    fun hx(px: Float) = HUD_X + px * k
    fun hy(px: Float) = HUD_Y + px * k

    Art(v.art, HUD_X, HUD_Y, HUD_W, HUD_W * 1024f / 1536f, ContentScale.FillBounds)

    // Смайлик: хорошее настроение уже нарисовано, для спокойного и грустного — свой кружок поверх.
    val face = when {
        s.mood >= 45 -> null
        s.mood >= 20 -> "${v.art}_neutral"
        else -> "${v.art}_sad"
    }
    if (face != null) {
        val (fx, fy, fr) = v.face
        Art(face, hx(fx - fr), hy(fy - fr), 2f * fr * k, 2f * fr * k, ContentScale.FillBounds)
    }

    // Часы: время и часть суток.
    HudText(VirtualClock.format(minutes), hx(v.time.first), hy(v.time.second), 46f, 14.5f, v.timeColor)
    HudText(v.part, hx(v.partAt.first), hy(v.partAt.second), 30f, 6.4f, v.timeColor)
    // Уровень (опыт Монтика) и достаток жилья.
    val level = Progress.levelOf(s.xp)
    HudText("$level", hx(v.digit.first), hy(v.digit.second), 22f, if (level >= 10) 12.5f else 16f, Color.White)
    HudText(s.home.levelTitle.substringBefore(' '), hx(v.house.first), hy(v.house.second), 42f, 5.6f, v.labelColor)
    // Настроение — две строки, как в макете.
    HudText(moodLabel(s.mood), hx(v.mood.first), hy(v.mood.second), 40f, 5.2f, v.labelColor, lines = 2)

    // Нажатия по пузырям.
    Hit(hx(500f), hy(70f), 560f * k, 480f * k) {
        vm.say("Сейчас ${VirtualClock.format(minutes)}, ${v.part.lowercase()}, день ${s.day}. Игровые сутки идут примерно 10 минут.")
    }
    Hit(hx(140f), hy(560f), 560f * k, 450f * k) { vm.goTo(Screen.Housing) }
    Hit(hx(780f), hy(560f), 640f * k, 450f * k) { vm.say(Life.moodReason(s)) }
}

/** Где на картинке пузырей стояли надписи (в пикселях картинки 1536×1024) и каким цветом их писать. */
private class HudVariant(
    val art: String,
    val part: String,
    val time: Pair<Float, Float>,
    val partAt: Pair<Float, Float>,
    val digit: Pair<Float, Float>,
    val house: Pair<Float, Float>,
    val mood: Pair<Float, Float>,
    /** Центр и половина стороны кружка-смайлика. */
    val face: Triple<Float, Float, Float>,
    val timeColor: Color,
    val labelColor: Color
)

private val HudInk = Color(0xFF1B2A33)
private val HudInkGreen = Color(0xFF10301F)

private val HUD_MORNING = HudVariant(
    "fg_hud_morning", "Утро", 765f to 325f, 764f to 413f, 580f to 632f, 424f to 870f, 1100f to 883f,
    Triple(1300f, 828f, 88f), HudInk, HudInk
)
private val HUD_DAY = HudVariant(
    "fg_hud_day", "День", 772f to 283f, 767f to 370f, 557f to 622f, 417f to 846f, 1102f to 848f,
    Triple(1310f, 800f, 93f), HudInkGreen, HudInkGreen
)
private val HUD_EVENING = HudVariant(
    "fg_hud_evening", "Вечер", 785f to 303f, 785f to 387f, 600f to 602f, 430f to 860f, 1120f to 868f,
    Triple(1337f, 805f, 91f), HudInk, Color.White
)
private val HUD_NIGHT = HudVariant(
    "fg_hud_night", "Ночь", 775f to 300f, 772f to 387f, 560f to 617f, 432f to 850f, 1102f to 862f,
    Triple(1310f, 808f, 91f), Color.White, Color.White
)

private fun hudVariant(hour: Int): HudVariant = when (hour) {
    in 5..11 -> HUD_MORNING
    in 12..16 -> HUD_DAY
    in 17..21 -> HUD_EVENING
    else -> HUD_NIGHT
}

/** Подпись пузыря настроения — как «Хорошее настроение» в макете, без эмодзи. */
private fun moodLabel(mood: Int): String = when {
    mood >= 75 -> "Отличное\nнастроение"
    mood >= 45 -> "Хорошее\nнастроение"
    mood >= 20 -> "Спокойное\nнастроение"
    else -> "Монтику\nскучно"
}

/** Место картинки пузырей в макете. */
private const val HUD_X = 0f
private const val HUD_Y = 21f
private const val HUD_W = 198f

/** Надпись по центру точки (cx, cy) шириной w — жирный округлый шрифт, как в макете. */
@Composable
private fun DesignScope.HudText(
    text: String,
    cx: Float,
    cy: Float,
    w: Float,
    size: Float,
    color: Color,
    lines: Int = 1
) {
    val h = size * 1.6f * lines
    Box(Modifier.at(cx - w / 2f, cy - h / 2f, w, h), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = color,
            fontSize = fs(size, false),
            lineHeight = fs(size * 1.12f, false),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = lines,
            softWrap = lines > 1,
            overflow = TextOverflow.Clip
        )
    }
}

/** Масштаб плашек Group 21. */
private const val STATUS_K = 0.42f

/** Кремовая подложка одной плашки (x 8, высота 110 в масштабе [STATUS_K]). */
@Composable
private fun DesignScope.StatusPillBg(y: Float, w: Float, onClick: (() -> Unit)?) {
    val k = STATUS_K
    Box(
        Modifier.at(8f, y, w * k, 110f * k)
            .shadow(d(3f), RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFAF9EA))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

// ───────────────────────── Кухня ─────────────────────────

/**
 * Второй главный экран — кухня (кадры макета «Android Compact 19–27»). Кухня тоже зависит от уровня
 * и времени суток. Панель еды из макета: бургер, яблоко, «не есть», «+» (магазин) и шкала сытости.
 */
@Composable
fun KitchenScreen(vm: GameViewModel) {
    val s = vm.state
    val nowMs = rememberNowMs()
    val minutes = VirtualClock.timeOfDay(VirtualClock.now(s, nowMs))
    val bgName = kitchenArt(s, minutes / 60)

    DesignCanvas(background = {
        ArtImage(bgName, Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF6E7CF), Color(0xFFD9B98E)))))
        }
    }) {
        // Магниты из поездок теперь висят на холодильнике (кадр Frame 38): кнопка «Холодильник» слева.
        HomeHud(vm, minutes, helpY = 528f, helpX = 8f)
        // Монтик отвечает на нажатие: прыжок, звук и реплика.
        TalkingMontik(vm, 96f, 350f, 220f, 230f, heroSize = 210f, bubbleX = 40f, bubbleY = 282f, bubbleW = 240f)

        // Панели еды на кухне больше нет: Монтик ест то, что лежит в холодильнике (кнопка слева),
        // а продукты покупает в магазине (кнопка «🛒 Магазин» ниже или приложение в телефоне).
        GreenPanelButton("🛒 Магазин", 12f, 856f, 130f, 40f) { vm.goTo(Screen.Grocery) }

        // Кнопка «Холодильник» (картинка из макета 99×148): слева, чтобы не заходить на кнопки справа.
        // Сначала — закрытый холодильник с магнитами из поездок, потом — продукты внутри.
        val fx = 6f
        val fy = 384f
        if (rememberHasArt("fg_fridge_btn")) {
            Art("fg_fridge_btn", fx, fy, 84f, 126f, ContentScale.FillBounds)
        } else {
            HudChip("🧊 Холодильник", fx, fy + 40f, 110f) { vm.goTo(Screen.FridgeDoor) }
        }
        val inFridge = ru.montik.app.game.Grocery.fridgeCount(s)
        if (inFridge > 0) {
            Box(
                Modifier.at(fx + 62f, fy + 22f, 24f, 24f).clip(CircleShape).background(Color(0xFFF2A516)),
                contentAlignment = Alignment.Center
            ) { Text("$inFridge", color = Color.White, fontSize = fs(12f, false), fontWeight = FontWeight.Bold) }
        }
        Hit(fx + 6f, fy + 20f, 72f, 100f) { vm.goTo(Screen.FridgeDoor) }

        InfoPanel(vm, kitchen = true)
    }
}

@Composable
private fun DesignScope.FoodCard(emoji: String, gain: String, x: Float) {
    Box(
        Modifier
            .at(x, 628f, 88f, 124f)
            .clip(RoundedCornerShape(d(18f)))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = fs(44f, false))
    }
    HudChip(gain, x + 50f, 616f, 46f, 26f, tint = Color(0xFF6DBE45), textColor = Color.White)
}

/** Ценник-монетка на карточке еды. */
@Composable
private fun DesignScope.PriceTag(price: Int, x: Float, y: Float) {
    Row(
        Modifier
            .at(x, y, 56f, 24f)
            .clip(RoundedCornerShape(d(12f)))
            .background(Color(0xFFFFF4D6))
            .border(d(1.5f), MontikColors.Coin, RoundedCornerShape(d(12f))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("🪙$price", color = MontikColors.Ink, fontSize = fs(12.5f, false), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ───────────────────────── Подсказка «три решения» ─────────────────────────

/** Знакомство с целью игры и тремя типами решений. Показывается один раз и по кнопке «?». */
@Composable
fun HelpDialog(vm: GameViewModel) {
    Dialog(onDismissRequest = { vm.closeHelp() }) {
        MontikCard(Modifier.verticalScroll(rememberScrollState())) {
            Text("Как играть", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Цель — научить Монтика жить самостоятельно: зарабатывать, тратить с умом и копить на мечту. " +
                    "Каждый раз, когда появляются деньги, ты решаешь одно из трёх:",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            HelpRow("🧾", "Потратить на обязательное", "Еда, вода и квартира. Без этого Монтику не обойтись — оплачивай первым.")
            HelpRow("🎁", "Потратить на желаемое", "Кино, игрушки, сладости. Поднимают настроение, но их можно отложить.")
            HelpRow("🐷", "Отложить", "В копилку на цель или в подушку безопасности. Так мечта становится ближе.")
            Spacer(Modifier.height(6.dp))
            Text(
                "Каждые ${ru.montik.app.game.Rules.PERIOD_DAYS} дней — новый период: составь план, а в конце получи " +
                    "до трёх звёзд привычек. Звёзды открывают переезд в квартиру получше.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            Spacer(Modifier.height(12.dp))
            BigButton("Понятно!", onClick = { vm.closeHelp() })
        }
    }
}

@Composable
private fun HelpRow(emoji: String, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ───────────────────────── Итоги периода ─────────────────────────

/** Итоги прошедшего периода: план и факт, звёзды и что делать дальше. */
@Composable
fun PeriodSummaryDialog(vm: GameViewModel, result: PeriodResult) {
    Dialog(onDismissRequest = { vm.markPeriodSeen() }) {
        MontikCard(Modifier.verticalScroll(rememberScrollState())) {
            Text("📊 Итоги периода ${result.period}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "⭐".repeat(result.stars) + "☆".repeat(3 - result.stars) + "  звёзд: ${result.stars} из 3",
                style = MaterialTheme.typography.titleMedium,
                color = MontikColors.CoinDark
            )
            Spacer(Modifier.height(8.dp))
            PlanFactTable(result)
            Spacer(Modifier.height(8.dp))
            for (line in Life.explain(result)) {
                Text(line, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
            val s = vm.state
            val next = Life.nextHousing(s)
            if (next != null) {
                Text(
                    "Всего звёзд: ${s.stars}. До переезда «${next.title}» нужно ${next.starsNeeded}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
            }
            Spacer(Modifier.height(12.dp))
            BigButton("Составить новый план", onClick = {
                vm.markPeriodSeen()
                vm.goTo(Screen.Budget)
            })
            Spacer(Modifier.height(6.dp))
            BigButton("Позже", onClick = { vm.markPeriodSeen() }, primary = false)
        }
    }
}

/** Таблица «план — факт» по трём направлениям. */
@Composable
fun PlanFactTable(r: PeriodResult) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1.4f))
            Text("План", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            Text("Факт", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
        PlanFactRow("🧾 Обязательное", if (r.planned) r.planNeeds else null, r.needs)
        PlanFactRow("🎁 Желаемое", if (r.planned) r.planWants else null, r.wants)
        PlanFactRow("🐷 Отложено", if (r.planned) r.planSavings else null, r.saved)
        PlanFactRow("💰 Заработано", null, r.earned)
    }
}

@Composable
private fun PlanFactRow(label: String, plan: Int?, fact: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
        Text(plan?.let { spaced(it) } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        Text(spaced(fact), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End)
    }
}
