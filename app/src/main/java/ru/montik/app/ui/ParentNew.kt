package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ru.montik.app.GameViewModel
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medals
import ru.montik.app.game.Praise
import ru.montik.app.game.Skill
import ru.montik.app.game.TaskTheme
import ru.montik.app.game.Tasks

/*
 * «Для родителей» по новому макету: плашка «Для родителей» неподвижна сверху, страницы под ней
 * листаются. Страницы — кадры макета («Настройки», «Настройки, но ниже», Frame 64–71), нарисованные
 * числа с них стёрты, игра пишет настоящие значения на тех же местах. Нажимаются нарисованные кнопки.
 */

private val ParInk = Color(0xFF1E3366)
private val ParGreenInk = Color(0xFF1F5A2A)
private val ParGray = Color(0xFF8C97AE)
private val ParBg = Color(0xFFF6F9EE)

/** Высота неподвижной шапки в единицах макета. */
private const val HEAD_H = 100f

/** Страница-картинка: [y0]…[y1] — какой кусок кадра (в координатах кадра) она показывает. */
private class ParPageScope(val ds: DesignScope, val y0: Float) {
    fun Modifier.p(x: Float, y: Float, w: Float, h: Float): Modifier = with(ds) {
        this@p.absoluteOffset(d(x), d(y - y0)).requiredSize(d(w), d(h))
    }
}

@Composable
private fun DesignScope.ParPage(art: String, y0: Float, y1: Float, content: @Composable ParPageScope.() -> Unit = {}) {
    val scope = remember(this, y0) { ParPageScope(this, y0) }
    Box(Modifier.requiredSize(d(DESIGN_W), d(y1 - y0))) {
        ArtImage(art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
        scope.content()
    }
}

/** Надпись на странице: [x] — левый край (или правый при [end]), [cy] — середина строки. */
@Composable
private fun ParPageScope.PText(
    text: String,
    x: Float,
    cy: Float,
    w: Float,
    size: Float,
    color: Color = ParInk,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    lines: Int = 1,
    h: Float = size * 1.5f * lines
) {
    val ds = ds
    Box(Modifier.p(x, cy - h / 2f, w, h), contentAlignment = when (align) {
        TextAlign.End -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }) {
        Text(
            text,
            color = color,
            fontSize = ds.fs(size, false),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            lineHeight = ds.fs(size * 1.2f, false)
        )
    }
}

/** Нажимаемое место на странице (без своей графики — кнопка нарисована в кадре). */
@Composable
private fun ParPageScope.PHit(x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    Box(
        Modifier.p(x, y, w, h).clip(RoundedCornerShape(ds.d(h / 2f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    )
}

/** Поле ввода поверх нарисованного поля: [hint] показываем, только если надпись-подсказку стёрли. */
@Composable
private fun ParPageScope.PField(
    value: String,
    onValue: (String) -> Unit,
    x: Float, y: Float, w: Float, h: Float,
    cover: Color?,
    size: Float = 16f,
    digits: Int = 0,
    hint: String? = null,
    secret: Boolean = false
) {
    val ds = ds
    Box(Modifier.p(x, y, w, h), contentAlignment = Alignment.CenterStart) {
        if (cover != null && value.isNotEmpty()) {
            Box(Modifier.matchParentSize().clip(RoundedCornerShape(ds.d(10f))).background(cover))
        }
        if (value.isEmpty() && hint != null) {
            Text(hint, color = ParGray, fontSize = ds.fs(size, false), modifier = Modifier.padding(start = ds.d(16f)))
        }
        BasicTextField(
            value = value,
            onValueChange = { v ->
                val clean = if (digits > 0) v.filter { it.isDigit() }.take(digits) else v.take(Praise.MAX_LENGTH)
                onValue(clean)
            },
            singleLine = true,
            textStyle = TextStyle(color = ParInk, fontSize = ds.fs(size, false)),
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (digits > 0) KeyboardType.NumberPassword else KeyboardType.Text),
            modifier = Modifier.matchParentSize().absoluteOffset(ds.d(16f), ds.d(h / 2f - size * 0.7f))
        )
    }
}

@Composable
fun NewParentScreen(vm: GameViewModel) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmTest by rememberSaveable { mutableStateOf(false) }
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(ParBg)) }) {
        // Листаемые страницы — под шапкой.
        Box(
            Modifier.absoluteOffset(d(-bleed), d(HEAD_H))
                .requiredSize(d(DESIGN_W + 2f * bleed), d(DESIGN_H - HEAD_H + padBottom))
        ) {
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    !vm.hasPin -> SetupPage(vm, onTest = { confirmTest = true })
                    !vm.parentUnlocked -> LoginPage(vm)
                    else -> Dashboard(vm, onReset = { confirmReset = true }, onTest = { confirmTest = true })
                }
            }
        }
        // Неподвижная плашка «Для родителей» (над ней — тот же фон до края экрана).
        Box(
            Modifier.absoluteOffset(d(-bleed), d(-padTop))
                .requiredSize(d(DESIGN_W + 2f * bleed), d(padTop + 6f))
                .background(ParBg)
        )
        Box(Modifier.absoluteOffset(d(0f), d(0f)).requiredSize(d(DESIGN_W), d(HEAD_H))) {
            ArtImage("fg3_par_head", Modifier.fillMaxSize(), ContentScale.FillBounds) {}
        }
        PressHit(16f, 36f, 50f, 50f, 25f) { vm.back() }
    }
    if (confirmTest) {
        AlertDialog(
            onDismissRequest = { confirmTest = false },
            title = { Text("Сбросить тестовый профиль?") },
            text = { Text("Прогресс удалится, игра начнётся заново с готовым героем, 300 монетами и включённым демо-режимом.") },
            confirmButton = { TextButton(onClick = { confirmTest = false; vm.resetTestProfile() }) { Text("Сбросить", color = MontikColors.Bad) } },
            dismissButton = { TextButton(onClick = { confirmTest = false }) { Text("Отмена") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Удалить весь прогресс?") },
            text = { Text("Монтик, деньги, навыки и рисунок будут удалены. Это нельзя отменить.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetProgress() }) { Text("Удалить", color = MontikColors.Bad) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }
}

/** «Настройки, но ниже»: своё сообщение ребёнку, звуки, демо-режим и сброс тестового профиля (Frame 66). */
@Composable
private fun DesignScope.SettingsPage(vm: GameViewModel, onTest: () -> Unit) {
    val s = vm.state
    ParPage("fg3_par_p3", 130f, 860f) {
        Praise.quick.getOrNull(5)?.let { text -> PHit(52f, 140f, 308f, 72f) { vm.sendPraise(text) } }
        var msg by rememberSaveable { mutableStateOf("") }
        PField(msg, { msg = it }, 30f, 225f, 352f, 50f, cover = Color(0xFFFBFDF8), size = 15f)
        PHit(52f, 287f, 306f, 53f) {
            if (msg.isNotBlank()) {
                vm.sendPraise(msg)
                msg = ""
            }
        }
        PText(if (s.soundOn) "🔇  Выключить звуки" else "🔊  Включить звуки", 47f, 515f, 320f, 17f, ParGreenInk, bold = true, align = TextAlign.Center)
        PHit(47f, 490f, 320f, 50f) { vm.setSound(!s.soundOn) }
        PText(if (s.demo) "Выключить демо-режим" else "Включить демо-режим", 50f, 763f, 312f, 17f, ParGreenInk, bold = true, align = TextAlign.Center)
        PHit(50f, 740f, 312f, 48f) { vm.setDemo(!s.demo) }
        PText("Сбросить тестовый профиль", 50f, 820f, 312f, 16f, ParInk, bold = true, align = TextAlign.Center)
        PHit(50f, 797f, 312f, 48f, onTest)
    }
}

// ───────────────────────── Первый вход: придумать PIN ─────────────────────────

@Composable
private fun DesignScope.SetupPage(vm: GameViewModel, onTest: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var show by rememberSaveable { mutableStateOf(false) }
    ParPage("fg3_par_setup", 100f, 785f) {
        PinBoxes(pin, { pin = it }, 505f, show)
        PinBoxes(pin2, { pin2 = it }, 610f, show)
        PHit(336f, 505f, 34f, 34f) { show = !show }
        PHit(336f, 612f, 34f, 34f) { show = !show }
        PHit(57f, 697f, 300f, 57f) {
            when {
                pin.length != 4 -> vm.say("PIN должен состоять из 4 цифр.")
                pin != pin2 -> vm.say("PIN-коды не совпадают.")
                else -> vm.createPin(pin)
            }
        }
    }
    // Ниже — второй экран настроек.
    SettingsPage(vm, onTest)
}

/** Четыре клеточки PIN из макета (x 111…301, высота 47): цифра или точка в каждой. */
@Composable
private fun ParPageScope.PinBoxes(value: String, onValue: (String) -> Unit, y: Float, show: Boolean) {
    val ds = ds
    val xs = floatArrayOf(111f, 160f, 208f, 257f)
    for (i in 0 until 4) {
        val ch = value.getOrNull(i)
        if (ch != null) {
            Box(Modifier.p(xs[i] + 2f, y + 2f, 40f, 43f).background(Color.White), contentAlignment = Alignment.Center) {
                if (show) Text("$ch", color = ParInk, fontSize = ds.fs(20f, false), fontWeight = FontWeight.Bold)
                else Box(Modifier.size(ds.d(11f)).clip(CircleShape).background(ParInk))
            }
        }
    }
    // Невидимое поле ввода над клеточками: нажатие открывает цифровую клавиатуру.
    BasicTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() }.take(4)) },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.p(100f, y - 30f, 216f, 80f)
    )
}

// ───────────────────────── Вход по PIN ─────────────────────────

@Composable
private fun DesignScope.LoginPage(vm: GameViewModel) {
    var pin by rememberSaveable { mutableStateOf("") }
    ParPage("fg3_par_login", 100f, 890f) {
        PField(pin, { pin = it }, 36f, 165f, 339f, 57f, cover = null, size = 18f, digits = 4, hint = "PIN", secret = true)
        PHit(55f, 238f, 305f, 58f) {
            if (!vm.unlockParent(pin)) {
                vm.say("Неверный PIN.")
                pin = ""
            }
        }
    }
}

// ───────────────────────── Панель родителя ─────────────────────────

@Composable
private fun DesignScope.Dashboard(vm: GameViewModel, onReset: () -> Unit, onTest: () -> Unit) {
    val s = vm.state
    // Общий прогресс и финансовые привычки (Frame 64).
    ParPage("fg3_par_p1", 120f, 760f) {
        fun v(text: String, cy: Float, coin: Boolean) =
            Triple(text, cy, coin)
        val rows = listOf(
            v("${s.day}", 177f, false), v("${s.shifts}", 209f, false), v("${s.coins}", 241f, true),
            v("${s.cushion}", 273f, true), v("${s.deposit}", 304f, true),
            v("${Medals.earned(s).size} из ${Medals.all.size}", 337f, false), v("${s.tasksCorrect}", 372f, false),
            v("${s.totalEarned}", 405f, true), v("${s.totalTax}", 440f, true), v("${s.totalSpent}", 474f, true),
            v("${s.stars}", 681f, false), v("${s.periods.size}", 713f, false), v("${s.savings}", 745f, true)
        )
        for ((text, cy, coin) in rows) {
            PText(text, 200f, cy, if (coin) 157f else 180f, 15f, align = TextAlign.End)
        }
        PText(GameEngine.currentGoal(s).dropWhile { !it.isLetterOrDigit() }, 112f, 527f, 268f, 13.5f, bold = true, lines = 2)
        val visited = s.completedTrips.mapNotNull { Destinations.byId(it)?.name }
        PText(if (visited.isEmpty()) "Путешествий пока не было." else "Побывали: ${visited.joinToString(", ")}", 112f, 556f, 268f, 12f, ParGray)
        PText("Уровень ${s.housing}  ·  ${s.home.levelTitle}", 81f, 651f, 300f, 15f)
    }
    // Задания по темам и «Похвалить ребёнка» (Frame 65).
    ParPage("fg3_par_p2", 128f, 795f) {
        val themes = TaskTheme.values().map { t -> Tasks.all.filter { it.theme == t } }
        val values = listOf("${Tasks.doneCount(s)} из ${Tasks.all.size}", "${s.adsDeclined} / ${s.adsBought}") +
            themes.take(3).map { list -> "${list.count { it.id in s.taskResults }} из ${list.size}" }
        val ys = floatArrayOf(151f, 192f, 233f, 276f, 319f)
        values.forEachIndexed { i, t -> if (i < ys.size) PText(t, 200f, ys[i], 180f, 15f, bold = true, align = TextAlign.End) }
        val praise = listOf(525f to 45f, 577f to 43f, 627f to 43f, 677f to 60f, 745f to 43f)
        praise.forEachIndexed { i, (y, h) ->
            Praise.quick.getOrNull(i)?.let { text -> PHit(37f, y, 338f, h) { vm.sendPraise(text) } }
        }
    }
    SettingsPage(vm, onTest)
    // Чит-панель (Frame 70).
    ParPage("fg3_par_p4", 200f, 800f) {
        val ds = ds
        Box(Modifier.p(41f, 300f, 300f, 32f), contentAlignment = Alignment.CenterStart) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Монет в кошельке: ${s.coins}", color = ParInk, fontSize = ds.fs(17f, false), fontWeight = FontWeight.Bold)
                Box(Modifier.size(ds.d(6f)))
                ArtImage("fg2_coin", Modifier.size(ds.d(24f)), ContentScale.Fit) {}
            }
        }
        PHit(46f, 335f, 102f, 50f) { vm.cheatCoins(100) }
        PHit(155f, 335f, 102f, 50f) { vm.cheatCoins(500) }
        PHit(264f, 335f, 102f, 50f) { vm.cheatCoins(1000) }
        var amount by rememberSaveable { mutableStateOf("") }
        PField(amount, { amount = it }, 33f, 403f, 186f, 69f, cover = Color(0xFFFBFDF8), size = 17f, digits = 6)
        PHit(231f, 405f, 151f, 51f) {
            val n = amount.toIntOrNull() ?: 0
            if (n > 0) {
                vm.cheatCoins(n)
                amount = ""
            }
        }
        PHit(48f, 488f, 156f, 52f) { vm.cheatSkipDays(1) }
        PHit(210f, 488f, 156f, 52f) { vm.cheatSkipDays(10) }
        PHit(44f, 552f, 324f, 70f) { vm.cheatBarterNow() }
        PHit(44f, 632f, 324f, 71f) { vm.cheatUnlockSkins() }
        PHit(44f, 713f, 324f, 72f) { vm.cheatClearCredits() }
    }
    // Цели обучения (Frame 67 и 71).
    val lessons = Lesson.values()
    val done = lessons.count { it.name in s.seenLessons }
    ParPage("fg3_par_p5", 90f, 880f) {
        PText("Цели обучения: $done из ${lessons.size}", 39f, 118f, 320f, 23f, bold = true)
        val ys = floatArrayOf(163f, 230f, 285f, 337f, 389f, 443f, 515f, 567f, 608f, 663f, 733f, 785f, 842f)
        ys.forEachIndexed { i, cy -> lessons.getOrNull(i)?.let { GoalBox(44f, cy, it.name in s.seenLessons) } }
    }
    ParPage("fg3_par_p6", 110f, 880f) {
        val ys = floatArrayOf(137f, 195f, 253f, 326f, 403f, 461f, 521f, 579f, 657f, 733f, 793f, 850f)
        ys.forEachIndexed { i, cy -> lessons.getOrNull(13 + i)?.let { GoalBox(43f, cy, it.name in s.seenLessons) } }
    }
    // Навыки, последние события, PIN и удаление (Frame 69).
    ParPage("fg3_par_p7", 110f, 900f) {
        val ys = floatArrayOf(177f, 229f, 283f, 335f, 389f)
        Skill.values().forEachIndexed { i, skill ->
            if (i < ys.size) PText("уровень ${s.skillLevel(skill)} · очков ${s.skillPoints(skill)}", 196f, ys[i], 180f, 14.5f, bold = true, align = TextAlign.End)
        }
        val events = s.log.takeLast(2).reversed()
        PText(
            if (events.isEmpty()) "Пока событий нет." else events.joinToString("\n") { "•  $it" },
            42f, 523f, 338f, 13f, if (events.isEmpty()) ParGray else ParInk, lines = 2
        )
        var newPin by rememberSaveable { mutableStateOf("") }
        PField(newPin, { newPin = it }, 33f, 632f, 346f, 51f, cover = Color(0xFFFBFDF8), size = 17f, digits = 4, secret = true)
        PHit(33f, 692f, 346f, 46f) {
            if (newPin.length == 4) {
                vm.changePin(newPin)
                newPin = ""
            } else vm.say("Новый PIN — 4 цифры.")
        }
        PHit(33f, 748f, 346f, 47f, onReset)
    }
}

/** Клеточка цели обучения поверх нарисованной (24×24, левый край [x], середина [cy]): зелёная с галочкой или пустая. */
@Composable
private fun ParPageScope.GoalBox(x: Float, cy: Float, checked: Boolean) {
    val ds = ds
    val shape = RoundedCornerShape(ds.d(6f))
    Box(
        Modifier.p(x - 2f, cy - 13f, 26f, 26f).clip(shape)
            .background(if (checked) Brush.verticalGradient(listOf(Color(0xFF4CD34F), Color(0xFF22A12D))) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F2F1))))
            .border(ds.d(1.5f), if (checked) Color(0xFF1E8E2A) else Color(0xFFC9CDCB), shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = ds.fs(16f, false), fontWeight = FontWeight.Black)
    }
}
