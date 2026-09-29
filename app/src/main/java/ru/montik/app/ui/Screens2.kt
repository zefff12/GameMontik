package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Lesson
import ru.montik.app.game.Life
import ru.montik.app.game.Medals
import ru.montik.app.game.Rules
import ru.montik.app.game.VirtualClock
import ru.montik.app.game.Skill
import ru.montik.app.game.TaskTheme
import ru.montik.app.game.Tasks
import ru.montik.app.game.SleepPlace

// ───────────────────────── Сон ─────────────────────────

@Composable
fun SleepScreen(vm: GameViewModel) {
    val s = vm.state
    // Виртуальные часы идут сами: план ночи обновляется на глазах.
    val nowMs = rememberNowMs()
    val plan = vm.sleepPlan(nowMs)
    val nowV = VirtualClock.now(s, nowMs)
    val laterBed = plan.bedV > nowV
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MontikColors.PhoneTop, MontikColors.PhoneBottom))),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackCircle({ vm.back() })
                Spacer(Modifier.weight(1f))
                CoinPill(s.coins)
            }
            Spacer(Modifier.height(16.dp))
            Text("Пора спать!", style = MaterialTheme.typography.displayLarge, color = MontikColors.Ink)
            Text(
                "Завтра будет отличный день ☺",
                style = MaterialTheme.typography.bodyLarge,
                color = MontikColors.Ink.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(18.dp))

            // Карточка с будильником, как в макете.
            Surface(
                shape = MontikShapes.Card,
                color = MontikColors.Surface,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlarmClock(Modifier.size(120.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Будильник", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
                    Text(VirtualClock.formatV(plan.alarmV), style = MaterialTheme.typography.displayLarge, color = MontikColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сейчас ${VirtualClock.formatV(nowV)}, день ${s.day}. " +
                            (if (laterBed) "Монтик ляжет вечером, в ${VirtualClock.formatV(plan.bedV)}" else "Монтик ложится сейчас") +
                            " и проспит ${VirtualClock.duration(plan.minutes)} — это ${plan.minutes / Rules.VIRTUAL_PER_REAL} мин настоящего времени.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Проспит 8 часов и больше — выспится, и смены будут отнимать меньше сил. Мало сна — Монтик быстрее устанет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            MontikCard(color = MontikColors.Surface) {
                Text("${s.home.emoji} Спать дома", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Монтик снимает квартиру, поэтому ночь дома бесплатная: за жильё он платит дважды в месяц. " +
                        "За целую ночь силы восстановятся полностью.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Life.rentNotice(s)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MontikColors.Warn)
                }
                Spacer(Modifier.height(10.dp))
                BigButton("Лечь спать", onClick = { vm.goToBed(SleepPlace.CABIN) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Зелёный будильник из макета, нарисованный кодом. */
@Composable
internal fun AlarmClock(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val c = Offset(w / 2f, w * 0.56f)
        val r = w * 0.34f
        // Звонки сверху.
        for (dx in listOf(-1f, 1f)) {
            drawCircle(MontikColors.PhoneDeep, radius = w * 0.11f, center = Offset(c.x + dx * r * 0.78f, c.y - r * 0.82f))
        }
        // Ножки.
        for (dx in listOf(-1f, 1f)) {
            drawRoundRect(
                color = MontikColors.PhoneDeep,
                topLeft = Offset(c.x + dx * r * 0.6f - w * 0.04f, c.y + r * 0.72f),
                size = Size(w * 0.08f, w * 0.14f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
        drawCircle(MontikColors.Phone, radius = r, center = c)
        drawCircle(Color.White, radius = r * 0.82f, center = c)
        drawCircle(MontikColors.PhoneDeep, radius = r, center = c, style = Stroke(width = w * 0.03f))
        // Стрелки на 07:00.
        drawLine(
            color = MontikColors.Ink,
            start = c,
            end = Offset(c.x - r * 0.42f, c.y - r * 0.24f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = MontikColors.Ink,
            start = c,
            end = Offset(c.x, c.y - r * 0.6f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawCircle(MontikColors.Ink, radius = w * 0.02f, center = c)
    }
}

// ───────────────────────── Подушка безопасности ─────────────────────────

@Composable
fun CushionScreen(vm: GameViewModel) {
    ScreenScaffold("Подушка безопасности", vm, icon = "fg_ui_h_cushion", footer = "fg_ui_foot_cushion") {
        val s = vm.state
        IconCard("fg_ui_c_montik", "🛟", color = Page.Mint, iconSize = 96.dp) {
            Text(
                "Подушка безопасности — это запас денег на трудные дни: поломку, потерю вещи, непредвиденные траты. " +
                    "Хорошее правило: откладывать пятую часть (20%) от заработка.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        IconCard(
            "fg_ui_c_coins", "🪙", iconSize = 64.dp,
            below = {
                LinearProgressIndicator(
                    progress = { (s.cushion / Rules.CUSHION_GOAL.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(14.dp).clip(MontikShapes.Chip),
                    color = MontikColors.Good,
                    trackColor = MontikColors.Track
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (s.cushion >= Rules.CUSHION_GOAL) "🎉 Подушка готова! Теперь Монтик спокойнее переживёт трудные дни."
                    else "Цель: накопить ${Rules.CUSHION_GOAL} монет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
            }
        ) {
            Text("В подушке", style = MaterialTheme.typography.titleLarge)
            Text("${s.cushion} из ${Rules.CUSHION_GOAL} монет", style = MaterialTheme.typography.bodyLarge, color = MontikColors.InkSoft)
        }
        IconCard(
            "fg_ui_c_pig", "🐷", color = Page.Sky, iconSize = 64.dp,
            below = {
                PillRow {
                    for (amount in listOf(10, 20, 50)) {
                        PagePill("+$amount", Modifier.weight(1f), filled = false, enabled = s.coins >= amount) { vm.deposit(amount) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PagePill("Отложить всё, что в кошельке", Modifier.fillMaxWidth(), enabled = s.coins > 0, icon = "fg_ui_c_coin") {
                    vm.deposit(s.coins)
                }
            }
        ) {
            Text("Отложить в подушку", style = MaterialTheme.typography.titleLarge)
            Text("В кошельке: ${s.coins} монет", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        }
        IconCard(
            "fg_ui_c_doc", "📄", color = Page.Lilac, iconSize = 64.dp,
            below = {
                PillRow {
                    PagePill("−10", Modifier.weight(1f), filled = false, enabled = s.cushion >= 10) { vm.withdraw(10) }
                    PagePill("Забрать всё", Modifier.weight(1f), filled = false, enabled = s.cushion > 0) { vm.withdraw(s.cushion) }
                }
            }
        ) {
            Text("Взять из подушки", style = MaterialTheme.typography.titleLarge)
            Text("Подушка нужна для трудных дней, а не для игрушек.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        }
    }
}

// ───────────────────────── Банк ─────────────────────────

@Composable
fun BankScreen(vm: GameViewModel) {
    ScreenScaffold("🏦 Банк", vm) {
        val s = vm.state
        MontikCard(color = MontikColors.SurfaceTint) {
            Text(
                "Банк хранит деньги и платит за это проценты: каждый день он добавляет " +
                    "${Rules.DEPOSIT_PERCENT} монет на каждые 100, которые пролежали на вкладе целый день. " +
                    "Так деньги начинают работать сами.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Важно: деньги на вкладе не выручат прямо сегодня. Для неожиданных трат нужна подушка безопасности.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
        }

        MontikCard {
            Text("Вклад: ${coinsText(s.deposit)}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (s.deposit > 0) "Завтра банк добавит ${coinsText(GameEngine.dailyInterest(s))}."
                else "Пока на вкладе пусто. Положи от ${Rules.MIN_DEPOSIT} монет — и завтра их станет больше.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("В кошельке: ${coinsText(s.coins)}", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            Spacer(Modifier.height(10.dp))
            if (s.debt > 0 || s.credits.isNotEmpty()) {
                Text(
                    "Пока есть долг, вклад открыть нельзя: сначала верни кредит. " +
                        "Банк берёт за кредит больше, чем платит по вкладу, поэтому копить с долгом невыгодно.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.Bad
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (amount in listOf(10, 50)) {
                        OutlinedButton(
                            onClick = { vm.bankDeposit(amount) },
                            enabled = s.coins >= amount,
                            shape = MontikShapes.Chip,
                            modifier = Modifier.weight(1f)
                        ) { Text("+$amount") }
                    }
                    OutlinedButton(
                        onClick = { vm.bankDeposit(s.coins) },
                        enabled = s.coins >= Rules.MIN_DEPOSIT,
                        shape = MontikShapes.Chip,
                        modifier = Modifier.weight(1f)
                    ) { Text("Всё") }
                }
                Spacer(Modifier.height(8.dp))
                BigButton(
                    "Забрать вклад целиком",
                    onClick = { vm.bankWithdraw(s.deposit) },
                    enabled = s.deposit > 0,
                    primary = false
                )
            }
        }

        // Старый долг из прежней версии игры (если был) — его можно вернуть здесь.
        if (s.debt > 0) {
            MontikCard {
                Text("Старый долг банку: ${coinsText(s.debt)}", style = MaterialTheme.typography.titleMedium, color = MontikColors.Bad)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.repayLoan(20) },
                        enabled = s.coins > 0,
                        shape = MontikShapes.Chip,
                        modifier = Modifier.weight(1f)
                    ) { Text("Вернуть 20") }
                    OutlinedButton(
                        onClick = { vm.repayLoan(s.debt) },
                        enabled = s.coins >= s.debt,
                        shape = MontikShapes.Chip,
                        modifier = Modifier.weight(1f)
                    ) { Text("Вернуть всё") }
                }
            }
        }

        CreditsSection(vm)
    }
}

// ───────────────────────── Дневник и награды ─────────────────────────

@Composable
fun DiaryScreen(vm: GameViewModel) {
    ScreenScaffold("📒 Дневник и награды", vm) {
        val s = vm.state
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("Учебный прогресс", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            DiaryRow("Задания", "${Tasks.doneCount(s)} из ${Tasks.all.size}")
            DiaryRow("Звёзды привычек", "${s.stars}")
            DiaryRow("Уровень", "${s.housing} · ${s.home.levelTitle}")
            Text(GameEngine.currentGoal(s), style = MaterialTheme.typography.bodyMedium)
            val last = s.periods.lastOrNull()
            if (last != null) {
                Spacer(Modifier.height(6.dp))
                Text("Итоги периода ${last.period}: звёзд ${last.stars} из 3", style = MaterialTheme.typography.titleMedium)
                PlanFactTable(last)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill("📝 Задания") { vm.goTo(ru.montik.app.Screen.Tasks) }
                SmallPill("📖 Справка", filled = false) { vm.goTo(ru.montik.app.Screen.Glossary) }
            }
        }
        MontikCard {
            Text("Сегодня, день ${s.day}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            DiaryRow("Заработал", "+${coinsText(s.dayEarned)}")
            DiaryRow("Потратил", "−${coinsText(s.daySpent)}")
            DiaryRow("Отложил", coinsText(maxOf(0, s.daySaved)))
        }
        val diary = s.diary
        if (diary != null) {
            MontikCard {
                Text("Вчера, день ${diary.day}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                DiaryRow("Заработал", "+${coinsText(diary.earned)}")
                DiaryRow("Потратил", "−${coinsText(diary.spent)}")
                DiaryRow("Отложил", coinsText(diary.saved))
            }
        }
        MontikCard {
            val earned = Medals.earned(s).map { it.id }.toSet()
            Text("Медали: ${earned.size} из ${Medals.all.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            for (row in Medals.all.chunked(4)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in row) {
                        val has = m.id in earned
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                if (has) m.emoji else "🔒",
                                fontSize = 30.sp,
                                modifier = Modifier.alpha(if (has) 1f else 0.35f)
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            for (m in Medals.all) {
                val has = m.id in earned
                Text(
                    "${if (has) "✅" else "⬜"} ${m.emoji} ${m.title} — ${m.hint}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (has) MontikColors.Ink else MontikColors.InkSoft
                )
                Spacer(Modifier.height(2.dp))
            }
        }
        MontikCard {
            Text("Навыки Монтика", style = MaterialTheme.typography.titleMedium)
            for (skill in Skill.values()) {
                Spacer(Modifier.height(10.dp))
                val points = s.skillPoints(skill)
                Text(
                    "${skill.emoji} ${skill.title} · уровень ${s.skillLevel(skill)}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(skill.meaning, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (points % Skill.POINTS_PER_LEVEL) / Skill.POINTS_PER_LEVEL.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = MontikColors.Ink,
                    trackColor = MontikColors.Track
                )
            }
        }
        MontikCard {
            Text("Что уже случилось", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (s.log.isEmpty()) {
                Text("Пока записей нет.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            }
            for (line in s.log.takeLast(12).reversed()) {
                Text("• $line", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun DiaryRow(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, style = MaterialTheme.typography.bodyLarge)
        Text(right, style = MaterialTheme.typography.bodyLarge)
    }
}

// ───────────────────────── Режим родителя ─────────────────────────

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ParentScreen(vm: GameViewModel) {
    Box(Modifier.fillMaxSize().background(MontikColors.Cream), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp)
        ) {
            ScreenHeader("🔒 Для родителей", onBack = { vm.back() })
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    !vm.hasPin -> ParentSetup(vm)
                    !vm.parentUnlocked -> ParentLogin(vm)
                    else -> ParentDashboard(vm)
                }
            }
        }
    }
}

/** Первый вход: «взрослая проверка» и создание PIN. */
@Composable
private fun ParentSetup(vm: GameViewModel) {
    var answer by rememberSaveable { mutableStateOf("") }
    var passed by rememberSaveable { mutableStateOf(false) }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    MontikCard(color = MontikColors.SurfaceTint) {
        Text(
            "Здесь родитель видит цели обучения и прогресс ребёнка. Все данные хранятся только на этом устройстве: " +
                "аккаунт не нужен, ничего не отправляется в интернет.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
    if (!passed) {
        MontikCard {
            Text("Проверка для взрослых", style = MaterialTheme.typography.titleMedium)
            Text("Сколько будет 7 × 8?", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { if (it.length <= 3 && it.all { ch -> ch.isDigit() }) answer = it },
                label = { Text("Ответ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (error != null) Text(error ?: "", color = MontikColors.Bad, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            BigButton("Проверить", onClick = {
                if (answer == "56") {
                    passed = true
                    error = null
                } else {
                    error = "Неверно. Этот раздел для взрослых."
                }
            })
        }
    } else {
        MontikCard {
            Text("Придумайте PIN из 4 цифр", style = MaterialTheme.typography.titleMedium)
            Text(
                "PIN защищает раздел от случайного входа ребёнка.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            PinField(pin, "PIN") { pin = it }
            Spacer(Modifier.height(8.dp))
            PinField(pin2, "Повторите PIN") { pin2 = it }
            if (error != null) Text(error ?: "", color = MontikColors.Bad, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            BigButton("Сохранить PIN", onClick = {
                when {
                    pin.length != 4 -> error = "PIN должен состоять из 4 цифр."
                    pin != pin2 -> error = "PIN-коды не совпадают."
                    else -> vm.createPin(pin)
                }
            })
        }
    }
}

@Composable
private fun ParentLogin(vm: GameViewModel) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    MontikCard {
        Text("Введите PIN", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        PinField(pin, "PIN") { pin = it }
        if (error != null) Text(error ?: "", color = MontikColors.Bad, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        BigButton("Войти", onClick = {
            if (vm.unlockParent(pin)) {
                error = null
            } else {
                error = "Неверный PIN."
                pin = ""
            }
        })
    }
    MontikCard(color = MontikColors.SurfaceTint) {
        Text("Забыли PIN?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Данные можно удалить без разработчика: Настройки Android → Приложения → Монтик → Хранилище → " +
                "Очистить данные. Прогресс игры будет удалён, и PIN можно задать заново.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ParentDashboard(vm: GameViewModel) {
    val s = vm.state
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmTest by rememberSaveable { mutableStateOf(false) }
    var newPin by rememberSaveable { mutableStateOf("") }

    MontikCard {
        Text("Общий прогресс", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        DiaryRow("Игровой день", "${s.day}")
        DiaryRow("Смен отработано", "${s.shifts}")
        DiaryRow("В кошельке", coinsText(s.coins))
        DiaryRow("В подушке безопасности", coinsText(s.cushion))
        DiaryRow("На вкладе в банке", coinsText(s.deposit))
        if (s.debt > 0) DiaryRow("Долг по кредиту", coinsText(s.debt))
        DiaryRow("Медалей получено", "${Medals.earned(s).size} из ${Medals.all.size}")
        DiaryRow("Заданий решено верно", "${s.tasksCorrect}")
        DiaryRow("Всего заработано", coinsText(s.totalEarned))
        DiaryRow("Налог 13% уплачен", coinsText(s.totalTax))
        DiaryRow("Всего потрачено", coinsText(s.totalSpent))
        Spacer(Modifier.height(6.dp))
        Text(GameEngine.currentGoal(s), style = MaterialTheme.typography.bodyLarge)
        val visited = s.completedTrips.mapNotNull { Destinations.byId(it)?.name }
        Text(
            if (visited.isEmpty()) "Путешествий пока не было." else "Побывали: ${visited.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
    }
    MontikCard {
        Text("Финансовые привычки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        DiaryRow("Уровень", "${s.housing} · ${s.home.levelTitle}")
        DiaryRow("Звёзд привычек", "${s.stars}")
        DiaryRow("Периодов пройдено", "${s.periods.size}")
        DiaryRow("Накоплено (копилка + подушка)", coinsText(s.savings))
        DiaryRow("Заданий пройдено", "${Tasks.doneCount(s)} из ${Tasks.all.size}")
        DiaryRow("Реклама: закрыто / куплено", "${s.adsDeclined} / ${s.adsBought}")
        for (theme in TaskTheme.values()) {
            val list = Tasks.all.filter { it.theme == theme }
            DiaryRow("${theme.emoji} ${theme.title}", "${list.count { it.id in s.taskResults }} из ${list.size}")
        }
        val last = s.periods.lastOrNull()
        if (last != null) {
            Spacer(Modifier.height(6.dp))
            Text("Последний период (${last.period}): звёзд ${last.stars} из 3", style = MaterialTheme.typography.bodyLarge)
            for (line in Life.explain(last)) Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Здесь нет оценок «плохо/хорошо»: ошибки в игре — это учебные ситуации, их можно исправить в следующем периоде.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
    }
    PraiseCard(vm)
    MontikCard {
        Text("Звуки в игре", style = MaterialTheme.typography.titleMedium)
        Text(
            "Короткие звуки: сканер на кассе, монетки, успех и ошибка. Громкость — как у медиа на телефоне.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        BigButton(
            if (s.soundOn) "🔇 Выключить звуки" else "🔊 Включить звуки",
            onClick = { vm.setSound(!s.soundOn) },
            primary = !s.soundOn
        )
    }
    MontikCard {
        Text("Демонстрационный режим", style = MaterialTheme.typography.titleMedium)
        Text(
            "Для экспертной проверки: все задания открыты сразу, а на главном экране появляется кнопка «Следующий период» — " +
                "игровые периоды проходят подряд без ожидания, по тем же правилам.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        BigButton(
            if (s.demo) "Выключить демо-режим" else "Включить демо-режим",
            onClick = { vm.setDemo(!s.demo) },
            primary = !s.demo
        )
        Spacer(Modifier.height(8.dp))
        BigButton("Сбросить тестовый профиль", onClick = { confirmTest = true }, primary = false)
    }
    CheatPanel(vm)
    MontikCard {
        val done = Lesson.values().count { it.name in s.seenLessons }
        Text("Цели обучения: $done из ${Lesson.values().size}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        for (lesson in Lesson.values()) {
            val seen = lesson.name in s.seenLessons
            Text(
                "${if (seen) "✅" else "⬜"} ${lesson.title}: ${lesson.goal}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (seen) MontikColors.Ink else MontikColors.InkSoft
            )
            Spacer(Modifier.height(4.dp))
        }
    }
    MontikCard {
        Text("Навыки Монтика", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        for (skill in Skill.values()) {
            DiaryRow("${skill.emoji} ${skill.title}", "уровень ${s.skillLevel(skill)} · очков ${s.skillPoints(skill)}")
        }
    }
    MontikCard {
        Text("Последние события", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (s.log.isEmpty()) Text("Пока событий нет.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        for (line in s.log.takeLast(15).reversed()) {
            Text("• $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
    MontikCard {
        Text("Настройки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        PinField(newPin, "Новый PIN (4 цифры)") { newPin = it }
        Spacer(Modifier.height(8.dp))
        BigButton("Сменить PIN", onClick = {
            vm.changePin(newPin)
            newPin = ""
        }, enabled = newPin.length == 4, primary = false)
        Spacer(Modifier.height(8.dp))
        BigButton("Удалить весь прогресс", onClick = { confirmReset = true }, primary = false)
        Spacer(Modifier.height(4.dp))
        Text(
            "Данные хранятся только на этом устройстве. Удаление стирает прогресс и рисунок Монтика.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft,
            textAlign = TextAlign.Start
        )
    }
    if (confirmTest) {
        AlertDialog(
            onDismissRequest = { confirmTest = false },
            containerColor = MontikColors.Surface,
            title = { Text("Сбросить тестовый профиль?") },
            text = { Text("Прогресс удалится, игра начнётся заново с готовым героем, 300 монетами и включённым демо-режимом.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmTest = false
                    vm.resetTestProfile()
                }) { Text("Сбросить", color = MontikColors.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmTest = false }) { Text("Отмена") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MontikColors.Surface,
            title = { Text("Удалить весь прогресс?") },
            text = { Text("Монтик, деньги, навыки и рисунок будут удалены. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetProgress()
                }) { Text("Удалить", color = MontikColors.Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
}


/** Похвала от взрослого: готовые слова одной кнопкой или своё сообщение. Ребёнок увидит его на главном экране. */
@Composable
private fun PraiseCard(vm: GameViewModel) {
    val s = vm.state
    var text by rememberSaveable { mutableStateOf("") }
    MontikCard(color = TipGreen) {
        Text("💌 Похвалить ребёнка", style = MaterialTheme.typography.titleMedium, color = NameInk)
        Text(
            "Сообщение появится у ребёнка на главном экране — это сильнее любой награды в игре.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (s.parentNote.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                (if (s.parentNoteNew) "Ждёт прочтения: " else "Прочитано: ") + "«${s.parentNote}»",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
        }
        Spacer(Modifier.height(8.dp))
        for (quick in ru.montik.app.game.Praise.quick) {
            OutlinedButton(onClick = { vm.sendPraise(quick) }, modifier = Modifier.fillMaxWidth()) { Text(quick) }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(ru.montik.app.game.Praise.MAX_LENGTH) },
            label = { Text("Своё сообщение") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BigButton("Отправить", onClick = {
            vm.sendPraise(text)
            text = ""
        }, enabled = text.isNotBlank())
    }
}

// ───────────────────────── Чит-панель (для взрослых и проверки) ─────────────────────────

/**
 * Чит-панель в разделе для взрослых (он закрыт PIN-кодом): добавить монеты, промотать дни,
 * позвать енота на рынок, обнулить кредиты, открыть все скины. Нужна для проверки игры.
 */
@Composable
private fun CheatPanel(vm: GameViewModel) {
    var amount by rememberSaveable { mutableStateOf("") }
    MontikCard(color = MontikColors.SurfaceTint) {
        Text("🎮 Чит-панель", style = MaterialTheme.typography.titleMedium)
        Text(
            "Для проверки игры взрослым. Ребёнок сюда не попадёт без PIN-кода.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        Spacer(Modifier.height(8.dp))
        Text("Монет в кошельке: ${coinsText(vm.state.coins)}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(100, 500, 1000)) {
                OutlinedButton(
                    onClick = { vm.cheatCoins(n) },
                    shape = MontikShapes.Chip,
                    modifier = Modifier.weight(1f)
                ) { Text("+$n") }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() }.take(6) },
                label = { Text("Сколько монет") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    vm.cheatCoins(amount.toIntOrNull() ?: 0)
                    amount = ""
                },
                enabled = (amount.toIntOrNull() ?: 0) > 0,
                shape = MontikShapes.Chip
            ) { Text("Добавить") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.cheatSkipDays(1) }, shape = MontikShapes.Chip, modifier = Modifier.weight(1f)) { Text("+1 день") }
            OutlinedButton(onClick = { vm.cheatSkipDays(10) }, shape = MontikShapes.Chip, modifier = Modifier.weight(1f)) { Text("+10 дней") }
        }
        Spacer(Modifier.height(8.dp))
        BigButton("🦝 Позвать енота на рынок сейчас", onClick = { vm.cheatBarterNow() }, primary = false)
        Spacer(Modifier.height(8.dp))
        BigButton("🎨 Открыть все скины и краски", onClick = { vm.cheatUnlockSkins() }, primary = false)
        Spacer(Modifier.height(8.dp))
        BigButton("💳 Обнулить кредиты и долги", onClick = { vm.cheatClearCredits() }, primary = false)
    }
}
