package ru.montik.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.imageResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.clipPath
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

/**
 * Управление главного экрана по новому макету (кадр «переезд»): пузыри времени, уровня и настроения
 * слева сверху, монеты справа сверху, «Работа» и «Задания» слева, столбик «Телефон / Кошелёк /
 * Холодильник» справа, три круглые шкалы внизу (сон, еда, вода) и плашка «Кухня» (на кухне — «Комната»).
 * [minutes] — время суток на часах Монтика.
 */
@Composable
fun DesignScope.HomeHud(vm: GameViewModel, minutes: Int, kitchen: Boolean = false) {
    val s = vm.state

    // Пузыри: время суток, уровень с жильём, настроение (надписи — живые).
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

    // Плашка монет из макета (нарисованное «1 250» стёрто с картинки), число — настоящее, на том же месте.
    Art("fg3_home_coin_clean", 219f, 29f, 193f, 64f, ContentScale.FillBounds)
    Box(Modifier.at(274f, 44f, 86f, 31f), contentAlignment = Alignment.Center) {
        Text(
            coinsText,
            color = Color(0xFF1D0B0A),
            fontSize = fs(if (coinsText.length > 5) 18f else 22f, false),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
    // «+» — заработать.
    Hit(350f, 34f, 56f, 54f) { vm.goTo(Screen.Work) }

    // «Работа» и круглая кнопка «Задания» слева.
    Art("fg3_home_clock", 0f, 176f, 114f, 118f, ContentScale.FillBounds)
    PressHit(16f, 194f, 70f, 68f, 14f) { vm.goTo(Screen.Work) }
    Art("fg3_home_tasks", 12f, 275f, 78f, 74f, ContentScale.FillBounds)
    PressHit(15f, 277f, 72f, 71f, 36f) { vm.goTo(Screen.Tasks) }

    // Столбик справа: Телефон, Кошелёк, Холодильник.
    Art("fg3_home_right", 235f, 176f, 216f, 335f, ContentScale.FillBounds)
    PressHit(295f, 192f, 97f, 100f, 48f) { vm.goTo(Screen.Phone) }
    PressHit(295f, 297f, 97f, 94f, 46f) { vm.goTo(Screen.Wallet) }
    PressHit(295f, 396f, 97f, 100f, 48f) { vm.goTo(Screen.FridgeDoor) }
    val inFridge = ru.montik.app.game.Grocery.fridgeCount(s)
    if (inFridge > 0) {
        Box(
            Modifier.at(370f, 400f, 22f, 22f).clip(CircleShape).background(Color(0xFFF2A516)),
            contentAlignment = Alignment.Center
        ) { Text("$inFridge", color = Color.White, fontSize = fs(11f, false), fontWeight = FontWeight.Bold) }
    }

    // Приставка, накопленная в копилке, — круглая кнопка под холодильником.
    if (ru.montik.app.game.ConsoleGame.GOAL_ID in s.goalsDone) {
        Art("fg3_w_635", 306f, 512f, 80f, 80f, ContentScale.Fit)
        PressHit(310f, 516f, 72f, 72f, 36f) { vm.goTo(Screen.Console) }
    }

    // Три круглые шкалы: сон (силы), еда (сытость), вода.
    MeterRounds(s.energy, s.food, s.water)
    PressHit(68f, 669f, 88f, 88f, 44f) { vm.goTo(Screen.Sleep) }
    PressHit(162f, 669f, 88f, 88f, 44f) { vm.goTo(Screen.Fridge) }
    PressHit(256f, 669f, 88f, 88f, 44f) { vm.goTo(Screen.Fridge) }

    // В комнате — светлая плашка «Кухня»; на кухне — зелёные кнопки «Магазин» и «Комната» из макета.
    if (kitchen) {
        ArtButton("fg3_p_btn_shop", 6f, 830f, 168f, 56f) { vm.goTo(Screen.Grocery) }
        ArtButton("fg3_p_btn_room", 238f, 830f, 168f, 56f) { vm.goTo(Screen.Home) }
    } else {
        RoomPill("Кухня", 294f, 761f, 106f) { vm.goTo(Screen.Kitchen) }
    }

    // Квартира: если есть долг за жильё — напоминание (остальное время экран чистый, как в макете).
    if (s.rentDebt > 0) {
        val rent = Life.rentNotice(s)
        if (rent != null) HudChip(rent, 8f, 360f, 280f, tint = Color(0xFFFFE1DD)) { vm.goTo(Screen.Housing) }
    }
    // Демо-режим: перемотка к следующему периоду без ожидания.
    if (s.demo) {
        HudChip("⏩ Следующий период", 8f, 406f, 190f, tint = Color(0xFFDDF3FF)) { vm.skipPeriod() }
    }

    CoinBurst(burst, 206f, 700f, 300f, 60f)
}

/** Плашка «Кухня» из макета: #FEF7E7, рамка #B7A985, текст #54424B. */
@Composable
private fun DesignScope.RoomPill(text: String, x: Float, y: Float, w: Float, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .at(x, y, w, 38f)
            .clip(RoundedCornerShape(d(19f)))
            .background(if (pressed) Color(0xFFEFE4CC) else Color(0xFFFEF7E7))
            .border(d(1f), Color(0xFFB7A985), RoundedCornerShape(d(19f)))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF54424B), fontSize = fs(17f, false), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/**
 * Три круглые кнопки внизу (картинка 2172×724 → 281×94 в точке 66, 667). Дуги на картинке живые:
 * под ними — та же картинка без дуг, а полная поверх вырезается сектором по значению шкалы.
 * Дуга идёт от «8 часов» к «12 часам»: 160°…300° в градусах Compose (по часовой от «3 часов»).
 */
@Composable
private fun DesignScope.MeterRounds(energy: Int, food: Int, water: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fullId = remember { context.resources.getIdentifier("fg3_home_bottom", "drawable", context.packageName) }
    val emptyId = remember { context.resources.getIdentifier("fg3_home_bottom_empty", "drawable", context.packageName) }
    if (fullId == 0 || emptyId == 0) return
    val full = androidx.compose.ui.graphics.ImageBitmap.imageResource(fullId)
    val empty = androidx.compose.ui.graphics.ImageBitmap.imageResource(emptyId)
    val e by animateFloatAsState(energy.coerceIn(0, 100) / 100f, tween(600), label = "mE")
    val f by animateFloatAsState(food.coerceIn(0, 100) / 100f, tween(600), label = "mF")
    val w by animateFloatAsState(water.coerceIn(0, 100) / 100f, tween(600), label = "mW")
    androidx.compose.foundation.Canvas(Modifier.at(66f, 667f, 281f, 94f)) {
        val dst = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        drawImage(empty, dstSize = dst, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
        val k = size.width / 2172f
        val centers = listOf(381.5f, 1081f, 1784.5f)
        listOf(e, f, w).forEachIndexed { i, v ->
            if (v <= 0.01f) return@forEachIndexed
            val cx = centers[i] * k
            val cy = 352.5f * k
            val r = 380f * k
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy)
                arcTo(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r), 160f, 140f * v, false)
                close()
            }
            clipPath(path) {
                drawImage(full, dstSize = dst, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
            }
        }
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
    // Старой нижней панели больше нет: всё управление — в HomeHud (новый макет).
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
    HandText(coins, X(305f), Y(240f), 150f * k, when { coins.length > 5 -> 14f; coins.length > 3 -> 17f; else -> 22f }, bold = true)
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
    HudText(VirtualClock.format(minutes), hx(v.time.first), hy(v.time.second), 46f * HK, 14.5f * HK, v.timeColor)
    HudText(v.part, hx(v.partAt.first), hy(v.partAt.second), 30f * HK, 6.4f * HK, v.timeColor)
    // Уровень (опыт Монтика) и достаток жилья.
    val level = Progress.levelOf(s.xp)
    HudText("$level", hx(v.digit.first), hy(v.digit.second), 22f * HK, (if (level >= 10) 12.5f else 16f) * HK, Color.White)
    HudText(s.home.levelTitle.substringBefore(' '), hx(v.house.first), hy(v.house.second), 42f * HK, 5.6f * HK, v.labelColor)
    // Настроение — две строки, как в макете.
    HudText(moodLabel(s.mood), hx(v.mood.first), hy(v.mood.second), 40f * HK, 5.2f * HK, v.labelColor, lines = 2)

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
private const val HUD_X = 12f
private const val HUD_Y = 41f
private const val HUD_W = 156f

/** Надписи пузырей подбирались под ширину 198 — уменьшаем вместе с картинкой. */
private const val HK = HUD_W / 198f

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
        // Монтик отвечает на нажатие: прыжок, звук и реплика. Управление — то же, что в комнате (новый макет).
        TalkingMontik(vm, 96f, 350f, 220f, 230f, heroSize = 210f, bubbleX = 40f, bubbleY = 282f, bubbleW = 240f)
        HomeHud(vm, minutes, kitchen = true)
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
    // Окно «Как играть» — картинка из макета (image 395); кнопка «Понятно!» — нажимаемая зона.
    if (rememberHasArt("fg_figma_help")) {
        FigmaDialogArt(
            "fg_figma_help", 1024f / 1536f,
            listOf(ArtHit(0.256f, 0.822f, 0.558f, 0.108f) { vm.closeHelp() }),
            onDismiss = { vm.closeHelp() }
        )
        return
    }
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
