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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medals
import ru.montik.app.game.Rules
import ru.montik.app.game.Skill
import ru.montik.app.game.SleepPlace

// ───────────────────────── Путешествия ─────────────────────────

@Composable
fun TravelScreen(vm: GameViewModel) {
    ScreenScaffold("🚂 Путешествие", vm) {
        val s = vm.state
        val trip = s.trip
        val stop = GameEngine.currentStop(s)
        val dest = trip?.let { Destinations.byId(it.destinationId) }
        if (trip != null && stop != null && dest != null) {
            MeterCard(vm)
            MontikCard {
                Text("${dest.emoji} Путешествие: ${dest.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Остановка ${trip.stopsDone + 1} из ${dest.stops.size}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { trip.stopsDone / dest.stops.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = MontikColors.Ink,
                    trackColor = MontikColors.Track
                )
            }
            ScenarioCard(stop, s) { index -> vm.choose(stop.id, index) }
        } else {
            MontikCard(color = MontikColors.SurfaceTint) {
                Text(
                    "Монтик живёт в городе ${Destinations.HOME_CITY}. Отсюда можно отправиться в другие города. " +
                        "В пути его ждут задания: нужно сделать правильный выбор.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            for (d in Destinations.all) {
                val status = GameEngine.destinationStatus(s, d)
                val visited = d.id in s.completedTrips
                MontikCard {
                    Text("${d.emoji} ${d.name}", style = MaterialTheme.typography.titleMedium)
                    Text(d.intro, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Билет: ${d.ticket} монет", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    if (visited) {
                        Text("✅ Уже побывали", style = MaterialTheme.typography.titleMedium, color = MontikColors.Good)
                    } else {
                        BigButton(
                            "Купить билет (−${d.ticket})",
                            onClick = { vm.startTrip(d.id) },
                            enabled = status == null && s.coins >= d.ticket
                        )
                        if (status != null) {
                            Spacer(Modifier.height(4.dp))
                            Text("🔒 $status", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                        } else if (s.coins < d.ticket) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Не хватает ${d.ticket - s.coins} монет. Заработай на смене!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MontikColors.InkSoft
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Сон ─────────────────────────

@Composable
fun SleepScreen(vm: GameViewModel) {
    val s = vm.state
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
                    Text("07:00", style = MaterialTheme.typography.displayLarge, color = MontikColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "День ${s.day}. Сон восстанавливает силы.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            MontikCard(color = MontikColors.Surface) {
                Text("🏠 Ночь в домике", style = MaterialTheme.typography.titleMedium)
                Text("Силы восстановятся полностью.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (s.prepaidNight) "Ночь уже оплачена." else "Цена: ${Rules.CABIN_PRICE} монет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
                Spacer(Modifier.height(10.dp))
                BigButton(
                    if (s.prepaidNight) "Лечь спать в домике" else "Оплатить домик и лечь спать",
                    onClick = { vm.sleep(SleepPlace.CABIN) },
                    enabled = s.prepaidNight || s.coins >= Rules.CABIN_PRICE
                )
                if (!s.prepaidNight && s.coins < Rules.CABIN_PRICE) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Не хватает монет на домик. Можно переночевать на скамейке.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MontikCard(color = MontikColors.Surface) {
                Text("🪑 Скамейка в парке", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Бесплатно, но спится плохо: сил восстановится только на ${Rules.BENCH_ENERGY}%.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(10.dp))
                BigButton("Переночевать на скамейке", onClick = { vm.sleep(SleepPlace.BENCH) }, primary = false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Зелёный будильник из макета, нарисованный кодом. */
@Composable
private fun AlarmClock(modifier: Modifier = Modifier) {
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
    ScreenScaffold("🛟 Подушка безопасности", vm) {
        val s = vm.state
        MontikCard(color = MontikColors.SurfaceTint) {
            Text(
                "Подушка безопасности — это запас денег на трудные дни: поломку, потерю вещи, непредвиденные траты. " +
                    "Хорошее правило: откладывать пятую часть (20%) от заработка.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        MontikCard {
            Text("В подушке: ${s.cushion} из ${Rules.CUSHION_GOAL} монет", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (s.cushion / Rules.CUSHION_GOAL.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(14.dp),
                color = MontikColors.Good,
                trackColor = MontikColors.Track
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (s.cushion >= Rules.CUSHION_GOAL) "🎉 Подушка готова! Теперь Монтик спокойнее переживёт трудные дни."
                else "Цель: накопить ${Rules.CUSHION_GOAL} монет.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        MontikCard {
            Text("Отложить в подушку", style = MaterialTheme.typography.titleMedium)
            Text("В кошельке: ${s.coins} монет", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (amount in listOf(10, 20, 50)) {
                    OutlinedButton(
                        onClick = { vm.deposit(amount) },
                        enabled = s.coins >= amount,
                        shape = MontikShapes.Chip,
                        modifier = Modifier.weight(1f)
                    ) { Text("+$amount") }
                }
            }
            Spacer(Modifier.height(8.dp))
            BigButton("Отложить всё, что в кошельке", onClick = { vm.deposit(s.coins) }, enabled = s.coins > 0, primary = false)
        }
        MontikCard {
            Text("Взять из подушки", style = MaterialTheme.typography.titleMedium)
            Text(
                "Подушка нужна для трудных дней, а не для игрушек.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.withdraw(10) },
                    enabled = s.cushion >= 10,
                    shape = MontikShapes.Chip,
                    modifier = Modifier.weight(1f)
                ) { Text("−10") }
                OutlinedButton(
                    onClick = { vm.withdraw(s.cushion) },
                    enabled = s.cushion > 0,
                    shape = MontikShapes.Chip,
                    modifier = Modifier.weight(1f)
                ) { Text("Забрать всё") }
            }
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
            if (s.debt > 0) {
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

        MontikCard {
            Text("Кредит", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (s.debt > 0) {
                Text("Нужно вернуть банку: ${coinsText(s.debt)}", style = MaterialTheme.typography.titleMedium, color = MontikColors.Bad)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Долг не растёт, но и не исчезает сам. Пока он есть, часть заработка уходит на его возврат.",
                    style = MaterialTheme.typography.bodyLarge
                )
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
            } else {
                Text(
                    "Банк может дать ${Rules.LOAN_AMOUNT} монет сразу, но вернуть придётся ${Rules.LOAN_REPAY}. " +
                        "Разница в ${Rules.LOAN_REPAY - Rules.LOAN_AMOUNT} монет — это плата банку за то, что деньги дали раньше времени.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Подумай: если можно подождать и накопить — накопить дешевле.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
                Spacer(Modifier.height(10.dp))
                BigButton(
                    "Взять ${Rules.LOAN_AMOUNT} и вернуть ${Rules.LOAN_REPAY}",
                    onClick = { vm.takeLoan() },
                    primary = false
                )
            }
        }
    }
}

// ───────────────────────── Дневник и награды ─────────────────────────

@Composable
fun DiaryScreen(vm: GameViewModel) {
    ScreenScaffold("📒 Дневник и награды", vm) {
        val s = vm.state
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
