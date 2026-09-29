package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.FinTask
import ru.montik.app.game.Glossary
import ru.montik.app.game.Goals
import ru.montik.app.game.Housing
import ru.montik.app.game.Life
import ru.montik.app.game.Rating
import ru.montik.app.game.Rules
import ru.montik.app.game.TaskKind
import ru.montik.app.game.TaskOutcome
import ru.montik.app.game.Tasks

/*
 * Экраны по ТЗ: «План бюджета» (план и факт), «Копилка» (цели), «Задания», «Жильё» (уровни и квартира)
 * и «Справка» (термины). Правила — в game/Life.kt, game/Savings.kt, game/Tasks.kt.
 */

// ───────────────────────── Общие мелочи ─────────────────────────

@Composable
private fun Line(left: String, right: String, rightColor: Color = MontikColors.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(right, style = MaterialTheme.typography.bodyLarge, color = rightColor)
    }
}

@Composable
private fun Bar(value: Float, color: Color) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(12.dp).clip(MontikShapes.Chip),
        color = color,
        trackColor = MontikColors.Track
    )
}

@Composable
private fun ChipButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MontikShapes.Chip,
        modifier = modifier.heightIn(min = 48.dp)
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
}

private fun stars(n: Int): String = "⭐".repeat(n) + "☆".repeat((3 - n).coerceAtLeast(0))

private fun days(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "$n день"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n дня"
    else -> "$n дней"
}

// ───────────────────────── План бюджета ─────────────────────────

@Composable
fun BudgetScreen(vm: GameViewModel) {
    val s = vm.state
    val plan = Life.currentPlan(s)
    val first = Life.periodFirstDay(s.period)
    val last = Life.periodLastDay(s.period)
    ScreenScaffold("План бюджета", vm, icon = "fg_ui_h_budget", footer = "fg_ui_foot_budget") {
        IconCard(
            "fg_ui_b_cal", "📅", color = Page.Mint, iconSize = 72.dp,
            below = {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    IconLine("fg_ui_b_wallet", "👛", "В кошельке", spaced(s.coins))
                    IconLine("fg_ui_b_pigface", "🐷", "Накоплено (копилка и подушка)", spaced(s.savings))
                }
            }
        ) {
            Text("Период ${s.period}: дни $first–$last", style = MaterialTheme.typography.titleLarge)
            Text(
                "Осталось ${days(Life.daysLeftInPeriod(s))}. В начале периода распредели деньги на три части: " +
                    "обязательное, желаемое и копилку. В конце сравним план с тем, что было на самом деле.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (plan == null || !plan.confirmed) {
            PlanEditor(vm, plan?.needs, plan?.wants, plan?.savings)
        } else {
            PlanVsFact(vm)
        }

        if (s.purchases.isNotEmpty()) {
            MontikCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FigIcon("fg_ui_b_cart", "🛒", 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Покупки этого периода", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(4.dp))
                for (line in s.purchases.takeLast(15).reversed()) {
                    Text("• $line", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        val lastResult = s.periods.lastOrNull()
        if (lastResult != null) {
            MontikCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FigIcon("fg_ui_b_chart", "📊", 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Итоги периода ${lastResult.period}  ${stars(lastResult.stars)}", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(6.dp))
                PlanFactTable(lastResult)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        for (line in Life.explain(lastResult)) {
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    FigIcon("fg_ui_b_montik", "🐷", 92.dp)
                }
            }
        }
        if (s.periods.size > 1) {
            MontikCard {
                Text("⭐ Звёзды привычек: ${s.stars}", style = MaterialTheme.typography.titleMedium)
                for (r in s.periods.reversed()) Line("Период ${r.period}", stars(r.stars))
            }
        }
    }
}

@Composable
private fun PlanEditor(vm: GameViewModel, draftNeeds: Int?, draftWants: Int?, draftSavings: Int?) {
    val s = vm.state
    val available = s.coins
    val expected = Life.expectedNeeds(s)
    var needs by rememberSaveable(s.period, draftNeeds) { mutableStateOf(draftNeeds ?: minOf(expected, available)) }
    var savings by rememberSaveable(s.period, draftSavings) { mutableStateOf(draftSavings ?: ((available - minOf(expected, available)) / 5)) }
    var wants by rememberSaveable(s.period, draftWants) { mutableStateOf(draftWants ?: 0) }
    val left = available - needs - wants - savings

    MontikCard {
        Text("Составь план: $available монет", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val rentHint = if (!s.rentPaidAhead && s.rentDueDay <= Life.periodLastDay(s.period))
            "квартира ${s.home.rent} (день ${s.rentDueDay}), " else ""
        Text(
            "Подсказка: в этом периоде ждут ${rentHint}еда и вода ≈ ${Rules.FOOD_PER_DAY} в день" +
                (if (s.rentDebt > 0) ", долг за квартиру ${s.rentDebt}" else "") +
                ". Всего обязательного ≈ $expected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        Spacer(Modifier.height(10.dp))
        PlanRow("🧾 Обязательное", "еда, вода, квартира", needs, left) { needs = it }
        PlanRow("🎁 Желаемое", "радости, покупки для души", wants, left) { wants = it }
        PlanRow("🐷 В копилку", "на цель и подушку", savings, left) { savings = it }
        Spacer(Modifier.height(8.dp))
        Text(
            if (left >= 0) "Не распределено: $left" else "Перебор на ${-left}: убавь одну из сумм",
            style = MaterialTheme.typography.titleMedium,
            color = if (left >= 0) MontikColors.Ink else MontikColors.Bad
        )
        if (needs < expected && left >= 0) {
            Text(
                "На обязательное меньше, чем нужно (≈ $expected). Можно, но тогда придётся доплачивать из того, что заработаешь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.Warn
            )
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Подтвердить план", onClick = { vm.setPlan(needs, wants, savings, confirm = true) }, enabled = left >= 0)
        Spacer(Modifier.height(6.dp))
        BigButton(
            "Сохранить черновик",
            onClick = { if (vm.setPlan(needs, wants, savings, confirm = false)) vm.say("Черновик сохранён — его можно менять.") },
            enabled = left >= 0,
            primary = false
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "После подтверждения план не меняется до конца периода — так честнее сравнивать с фактом.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
    }
}

@Composable
private fun PlanRow(title: String, hint: String, value: Int, left: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            }
            Text("$value", style = MaterialTheme.typography.headlineMedium, color = MontikColors.Ink)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipButton("−10", value >= 10, Modifier.weight(1f)) { onChange(value - 10) }
            ChipButton("−1", value >= 1, Modifier.weight(1f)) { onChange(value - 1) }
            ChipButton("+1", left >= 1, Modifier.weight(1f)) { onChange(value + 1) }
            ChipButton("+10", left >= 10, Modifier.weight(1f)) { onChange(value + 10) }
        }
    }
}

@Composable
private fun PlanVsFact(vm: GameViewModel) {
    val s = vm.state
    val plan = Life.currentPlan(s) ?: return
    val now = Life.evaluate(s)
    MontikCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigIcon("fg_ui_b_chart", "📊", 34.dp)
            Spacer(Modifier.width(10.dp))
            Text("План и факт", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "План подтверждён: было ${plan.available}, не распределено ${plan.left}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        Spacer(Modifier.height(8.dp))
        FactRow("fg_ui_b_clip", "🧾", "Обязательное", s.pNeeds, plan.needs, Color(0xFFE86A64))
        FactRow("fg_ui_b_gift", "🎁", "Желаемое", s.pWants, plan.wants, Color(0xFFF2B233))
        FactRow("fg_ui_b_pigface", "🐷", "Отложено", s.pSaved.coerceAtLeast(0), plan.savings, Color(0xFF3F9E55))
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Page.Mint).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FigIcon("fg_ui_b_coins", "💰", 26.dp)
            Spacer(Modifier.width(8.dp))
            Text("Заработано в этом периоде: ${s.pEarned}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text("Если период закончится сейчас: ${stars(now.stars)}", style = MaterialTheme.typography.titleMedium)
        for (line in Life.explain(now)) Text(line, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("🐷 В копилку") { vm.goTo(Screen.Goals) }
            SmallPill("🛍 Магазин", filled = false) { vm.goTo(Screen.Grocery) }
        }
    }
}

@Composable
private fun FactRow(icon: String, fallback: String, title: String, fact: Int, plan: Int, color: Color) {
    val over = fact > plan + Life.tolerance(plan)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigIcon(icon, fallback, 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$fact из $plan", style = MaterialTheme.typography.titleMedium, color = if (over) MontikColors.Bad else color)
        }
        Bar(if (plan <= 0) (if (fact > 0) 1f else 0f) else fact / plan.toFloat(), if (over) MontikColors.Bad else color)
    }
}

// ───────────────────────── Копилка ─────────────────────────

@Composable
fun GoalsScreen(vm: GameViewModel) {
    val s = vm.state
    val goal = Goals.current(s)
    var withdrawAsk by rememberSaveable { mutableStateOf(0) }
    ScreenScaffold("Копилка", vm, icon = "fg_ui_h_goals", footer = "fg_ui_foot_goals") {
        MontikCard(color = Page.Mint) {
            Text(
                "Выбери цель и откладывай понемногу. Копилка — отдельно от кошелька: эти деньги не тратятся случайно.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        IconCard(
            if (goal == null) "fg_ui_g_target" else goalArt(goal.id),
            goal?.emoji ?: "🎯",
            photo = goal != null && goalIsPhoto(goal.id),
            below = {
                if (goal != null) {
                    Bar(s.piggy / goal.cost.toFloat(), MontikColors.Good)
                    Spacer(Modifier.height(6.dp))
                    Line("Накоплено", spaced(s.piggy))
                    Line("Осталось", spaced(Goals.remaining(s)))
                    val eta = Goals.etaDays(s)
                    val avg = Goals.averagePerDay(s)
                    Text(
                        when {
                            s.piggy >= goal.cost -> "🎉 Хватает! Можно достигать цели."
                            eta == null -> "Пополни копилку — и я посчитаю, когда цель будет достигнута."
                            else -> "При твоём темпе (в среднем $avg в день) — через ≈ ${days(eta)}: " +
                                "${Goals.remaining(s)} ÷ $avg."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (s.piggy >= goal.cost) {
                        Spacer(Modifier.height(8.dp))
                        BigButton(
                            if (goal.tripId != null) "Купить билет из копилки" else "Купить: ${goal.title.lowercase()}",
                            onClick = { vm.reachGoal() }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Отложить из кошелька (там ${spaced(s.coins)})",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PillRow {
                    for (a in listOf(10, 20, 50)) PagePill("+$a", Modifier.weight(1f), filled = false, enabled = s.coins >= a) { vm.piggyIn(a) }
                    PagePill("Всё", Modifier.weight(1f), filled = false, enabled = s.coins > 0) { vm.piggyIn(s.coins) }
                }
                Spacer(Modifier.height(10.dp))
                PagePill("Снять из копилки…", Modifier.fillMaxWidth(), filled = false, enabled = s.piggy > 0) {
                    withdrawAsk = minOf(10, s.piggy)
                }
            }
        ) {
            if (goal == null) {
                Text("Цель не выбрана", style = MaterialTheme.typography.titleLarge)
                Text("В копилке: ${spaced(s.piggy)}. Выбери цель ниже.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            } else {
                Text(goal.title, style = MaterialTheme.typography.titleLarge)
                Text("Цена: ${spaced(goal.cost)} монет", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            }
        }

        Text("Цели", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink, modifier = Modifier.padding(top = 4.dp))
        for (g in Goals.all) {
            val blocker = Goals.blocker(s, g)
            val current = g.id == s.goalId
            IconCard(
                goalArt(g.id), g.emoji,
                color = if (current) Page.Mint else goalTint(g.id),
                iconSize = 84.dp,
                photo = goalIsPhoto(g.id)
            ) {
                Text(g.title, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${spaced(g.cost)} монет", style = MaterialTheme.typography.bodyMedium)
                }
                Text(g.about, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                Spacer(Modifier.height(4.dp))
                when {
                    current -> Text("✓ Копим на эту цель", style = MaterialTheme.typography.titleMedium, color = MontikColors.Good)
                    blocker == null -> PagePill("Выбрать ›") { vm.chooseGoal(g.id) }
                    else -> Text("🔒 $blocker", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        IconCard("fg_ui_g_buoy", "🛟", color = Page.Mint, iconSize = 72.dp) {
            Text("Подушка безопасности: ${spaced(s.cushion)}", style = MaterialTheme.typography.titleMedium)
            Text("Запас на непредвиденное — тоже накопления.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
            Spacer(Modifier.height(6.dp))
            PagePill("Открыть подушку ›", filled = false) { vm.goTo(Screen.Cushion) }
        }
    }

    if (withdrawAsk > 0) {
        val p = Goals.previewWithdraw(s, withdrawAsk)
        AlertDialog(
            onDismissRequest = { withdrawAsk = 0 },
            containerColor = MontikColors.Surface,
            title = { Text("Снять из копилки?") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (a in listOf(10, 50)) ChipButton("$a", s.piggy >= a) { withdrawAsk = a }
                        ChipButton("Всё") { withdrawAsk = s.piggy }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Снять: ${p.amount}", style = MaterialTheme.typography.titleMedium)
                    Text("В копилке станет ${p.savedAfter} вместо ${p.savedBefore}.", style = MaterialTheme.typography.bodyMedium)
                    if (goal != null) {
                        Text(
                            when {
                                p.daysBefore == null -> "Срок до цели пока не посчитать."
                                p.daysAfter == null -> "Цель станет дальше."
                                else -> "Цель отодвинется: было ≈ ${days(p.daysBefore)}, станет ≈ ${days(p.daysAfter)}."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MontikColors.Bad
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.piggyOut(p.amount)
                    withdrawAsk = 0
                }) { Text("Да, снять ${p.amount}") }
            },
            dismissButton = { TextButton(onClick = { withdrawAsk = 0 }) { Text("Оставить в копилке") } }
        )
    }
}

// ───────────────────────── Задания ─────────────────────────

@Composable
fun TasksScreen(vm: GameViewModel) {
    val s = vm.state
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openId?.let { Tasks.byId(it) }
    if (open != null) {
        TaskPlayer(vm, open) { openId = null }
        return
    }
    ScreenScaffold("Задания", vm, icon = "fg_ui_h_tasks", footer = "fg_ui_foot_tasks") {
        IconCard("fg_ui_t_lamp", "💡", color = Page.Mint, iconSize = 60.dp) {
            Text(
                "Пройдено ${Tasks.doneCount(s)} из ${Tasks.all.size}. Каждый день открывается новое задание" +
                    (if (s.demo) " (в демо-режиме открыты все)." else ".") +
                    " За первое прохождение — монеты, а объяснение — всегда.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        val unlocked = Tasks.unlocked(s)
        for (theme in ru.montik.app.game.TaskTheme.values()) {
            when (theme) {
                ru.montik.app.game.TaskTheme.BUDGET -> SectionTitle(theme.title, "fg_ui_t_clip", theme.emoji, Page.HighlightGreen)
                ru.montik.app.game.TaskTheme.SAVING -> SectionTitle(theme.title, "fg_ui_t_pig", theme.emoji, Page.HighlightLilac)
                ru.montik.app.game.TaskTheme.PAYMENTS -> SectionTitle(theme.title, "fg_ui_b_cart", theme.emoji, Page.HighlightPeach)
            }
            for (t in Tasks.all.filter { it.theme == theme }) {
                val result = s.taskResults[t.id]
                val isOpen = t in unlocked
                val index = Tasks.all.indexOf(t)
                IconCard(
                    taskArt(t.id), t.emoji,
                    color = if (isOpen) MontikColors.Surface else MontikColors.Track,
                    iconSize = 72.dp,
                    arrow = isOpen,
                    onClick = if (isOpen) ({ openId = t.id }) else null
                ) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !isOpen -> "🔒 Откроется через ${days(index - unlocked.size + 1)}"
                            result == Rating.GREAT -> "✅ Отлично! Можно пройти ещё раз"
                            result != null -> "✔ Пройдено: ${result.title.lowercase()}. Попробуй лучше!"
                            else -> "Новое · награда до ${t.reward} монет"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft
                    )
                }
            }
        }
    }
}

/** Прохождение одного задания: ситуация → действие → последствия и объяснение. */
@Composable
private fun TaskPlayer(vm: GameViewModel, task: FinTask, onClose: () -> Unit) {
    var outcome by rememberSaveable(task.id) { mutableStateOf<Triple<String, String, String>?>(null) }
    var attempt by rememberSaveable(task.id) { mutableStateOf(0) }
    val firstTime = task.id !in vm.state.taskResults
    // «Назад» во время задания возвращает к списку заданий, а не на главный экран.
    BackHandler(enabled = true) { onClose() }

    fun submit(r: TaskOutcome?) {
        if (r == null) return
        vm.completeTask(task.id, r)
        outcome = Triple(r.rating.name, r.outcome, r.explanation)
    }

    ScreenScaffold("${task.emoji} ${task.title}", vm) {
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("${task.theme.emoji} ${task.theme.title}", style = MaterialTheme.typography.labelLarge, color = MontikColors.InkSoft)
            Spacer(Modifier.height(4.dp))
            Text(task.situation, style = MaterialTheme.typography.bodyLarge)
        }
        val done = outcome
        if (done == null) {
            when (val kind = task.kind) {
                is TaskKind.Pick -> MontikCard {
                    kind.options.forEachIndexed { i, o ->
                        BigButton(o.text, onClick = { submit(Tasks.checkPick(task, i)) }, primary = false)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                is TaskKind.Basket -> BasketPlayer(task, kind, attempt) { submit(it) }
                is TaskKind.Split -> SplitPlayer(task, kind, attempt) { submit(it) }
                is TaskKind.Count -> CountPlayer(task, attempt) { submit(it) }
            }
        } else {
            val rating = Rating.valueOf(done.first)
            MontikCard(color = if (rating == Rating.GREAT) MontikColors.SurfaceTint else MontikColors.Surface) {
                Text(
                    when (rating) {
                        Rating.GREAT -> "🌟 ${rating.title}"
                        Rating.OK -> "👍 ${rating.title}"
                        Rating.BAD -> "🤔 ${rating.title}"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(6.dp))
                Text(done.second, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text("Почему так? ${done.third}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                if (rating != Rating.GREAT) {
                    BigButton("Попробовать ещё раз", onClick = {
                        outcome = null
                        attempt++
                    }, primary = false)
                    Spacer(Modifier.height(8.dp))
                }
                BigButton("К заданиям", onClick = onClose)
            }
        }
        if (done == null) {
            Text(
                if (firstTime) "Награда: до ${task.reward} монет за первое прохождение." else "Задание уже пройдено — сейчас без награды.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            BigButton("Назад к заданиям", onClick = onClose, primary = false)
        }
    }
}

@Composable
private fun BasketPlayer(task: FinTask, kind: TaskKind.Basket, attempt: Int, onDone: (TaskOutcome?) -> Unit) {
    var picked by rememberSaveable(task.id, attempt) { mutableStateOf(listOf<Int>()) }
    val total = picked.sumOf { kind.items[it].price }
    MontikCard {
        Text("В кошельке: ${kind.budget} · в корзине: $total", style = MaterialTheme.typography.titleMedium,
            color = if (total > kind.budget) MontikColors.Bad else MontikColors.Ink)
        Spacer(Modifier.height(8.dp))
        kind.items.forEachIndexed { i, item ->
            val on = i in picked
            Surface(
                shape = MontikShapes.Card,
                color = if (on) MontikColors.SurfaceTint else MontikColors.Surface,
                border = BorderStroke(2.dp, if (on) MontikColors.LimeDeep else MontikColors.Track),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                    picked = if (on) picked - i else picked + i
                }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.emoji, fontSize = 28.sp)
                    Text(item.name, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodyLarge)
                    Text("${item.price}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(if (on) "☑" else "☐", fontSize = 24.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Оплатить корзину", onClick = { onDone(Tasks.checkBasket(task, picked.toSet())) }, enabled = picked.isNotEmpty())
    }
}

@Composable
private fun SplitPlayer(task: FinTask, kind: TaskKind.Split, attempt: Int, onDone: (TaskOutcome?) -> Unit) {
    var needs by rememberSaveable(task.id, attempt) { mutableStateOf(0) }
    var wants by rememberSaveable(task.id, attempt) { mutableStateOf(0) }
    var savings by rememberSaveable(task.id, attempt) { mutableStateOf(0) }
    val left = kind.total - needs - wants - savings
    MontikCard {
        Text("Осталось разложить: $left из ${kind.total}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SplitRow("🧾 Обязательное", needs, left, kind.step) { needs = it }
        SplitRow("🎁 Желаемое", wants, left, kind.step) { wants = it }
        SplitRow("🐷 Копилка", savings, left, kind.step) { savings = it }
        Spacer(Modifier.height(8.dp))
        BigButton("Готово", onClick = { onDone(Tasks.checkSplit(task, needs, wants, savings)) }, enabled = left == 0)
        if (left != 0) Text("Разложи все монеты, чтобы проверить.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
    }
}

@Composable
private fun SplitRow(title: String, value: Int, left: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        ChipButton("−", value >= step) { onChange(value - step) }
        Text("$value", Modifier.width(56.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        ChipButton("+", left >= step) { onChange(value + step) }
    }
}

@Composable
private fun CountPlayer(task: FinTask, attempt: Int, onDone: (TaskOutcome?) -> Unit) {
    var text by rememberSaveable(task.id, attempt) { mutableStateOf("") }
    MontikCard {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 5 && it.all { ch -> ch.isDigit() }) text = it },
            label = { Text("Твой ответ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BigButton("Проверить", onClick = { onDone(Tasks.checkCount(task, text.toIntOrNull())) }, enabled = text.isNotEmpty())
    }
}

// ───────────────────────── Жильё и уровни ─────────────────────────

@Composable
fun HousingScreen(vm: GameViewModel) {
    val s = vm.state
    val home = s.home
    ScreenScaffold("Жильё и уровень", vm, icon = "fg_ui_h_housing", footer = "fg_ui_foot_housing") {
        MontikCard(color = Page.Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FigPhoto(housingPhoto(home.level), home.emoji, 88.dp, 80.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(home.title, style = MaterialTheme.typography.titleLarge)
                    Text("Уровень ${home.level} из ${Housing.values().size} · ${home.levelTitle}", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            IconLine("fg_ui_hs_coins", "🪙", "Аренда (2 раза в месяц)", "${home.rent}")
            IconLine("fg_ui_hs_cal", "📅", "Следующий платёж", "день ${s.rentDueDay} (${Life.dayOfMonth(s.rentDueDay)}-е число)")
            IconLine("fg_ui_hs_glass", "⏳", "Звёзды привычек", "${s.stars}")
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Page.CardBorder))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                FigIcon("fg_ui_hs_info", "ℹ️", 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    when {
                        s.rentDebt > 0 -> {
                            Text("Долг за квартиру: ${s.rentDebt}. Хозяин ждёт, но долг нужно вернуть.", style = MaterialTheme.typography.bodyLarge, color = MontikColors.Bad)
                            Spacer(Modifier.height(6.dp))
                            BigButton("Погасить долг", onClick = { vm.payRentDebt() }, enabled = s.coins > 0)
                        }
                        s.rentPaidAhead -> Text("✅ Ближайший платёж уже внесён заранее.", style = MaterialTheme.typography.bodyLarge, color = MontikColors.Good)
                        Life.rentCountdown(s) -> {
                            Text(
                                "До оплаты: ${days(Life.daysUntilRent(s))}. Если в срок не хватит, недостающее возьмётся из подушки, а остаток станет долгом.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            BigButton("Оплатить заранее (−${home.rent})", onClick = { vm.payRentAhead() }, enabled = s.coins >= home.rent)
                        }
                        else -> Text(
                            "До оплаты ${days(Life.daysUntilRent(s))}. За ${Rules.RENT_NOTICE_DAYS} дней до срока начнётся отсчёт и можно будет заплатить заранее.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        MontikCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FigIcon("fg_ui_hs_lamp", "💡", 40.dp)
                Spacer(Modifier.width(10.dp))
                Text("Как растёт Монтик", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "За каждый период можно получить до трёх звёзд: оплатил обязательное, уложился в план, отложил в копилку. " +
                        "Звёзды и деньги на переезд открывают жильё получше. Если платить станет трудно, можно переехать попроще — бесплатно.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                FigIcon("fg_ui_hs_montik", "🐰", 96.dp)
            }
        }
        for (h in Housing.values()) {
            val current = h.level == s.housing
            val blocker = Life.moveBlocker(s, h)
            MontikCard(color = if (current) Page.Mint else MontikColors.Surface) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FigIcon("fg_ui_h_housing", h.emoji, 34.dp)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Page.HighlightGreen)
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                            ) { Text("Уровень ${h.level}", style = MaterialTheme.typography.titleMedium) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(h.levelTitle, style = MaterialTheme.typography.titleLarge)
                        Text(h.title, style = MaterialTheme.typography.titleMedium)
                    }
                    FigPhoto(housingPhoto(h.level), h.emoji, 110.dp, 96.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(h.about, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoinIcon(Modifier.size(20.dp)); Text(" Аренда ${h.rent}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("⭐ Переезд ${h.moveCost}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(12.dp))
                    Text("🌱 Нужно звёзд ${h.starsNeeded}", style = MaterialTheme.typography.bodyMedium)
                }
                if (h.starsNeeded > 0 && h.level > s.housing) {
                    Spacer(Modifier.height(4.dp))
                    Bar(s.stars / h.starsNeeded.toFloat(), MontikColors.Good)
                }
                Spacer(Modifier.height(8.dp))
                when {
                    current -> Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Page.HighlightGreen).padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FigIcon("fg_ui_h_housing", "🏠", 30.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Монтик живёт здесь", style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink)
                        }
                    }
                    blocker == null -> BigButton(
                        if (h.level > s.housing) "Переехать (−${h.moveCost})" else "Переехать попроще (бесплатно)",
                        onClick = { vm.moveTo(h.level) },
                        primary = h.level > s.housing
                    )
                    else -> Text("🔒 $blocker", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                }
            }
        }
        PagePill("📖 Справка: что значат слова", Modifier.fillMaxWidth(), filled = false) { vm.goTo(Screen.Glossary) }
    }
}

/** Картинка жилья для уровня: на уровне 1 — комната из макета «Жильё и уровень», дальше — комнаты уровней. */
private fun housingPhoto(level: Int): String = when (level) {
    1 -> "fg_ui_hs_levelroom"
    2 -> "fg_room2_day"
    else -> "fg_room3_day"
}

// ───────────────────────── Справка ─────────────────────────

@Composable
fun GlossaryScreen(vm: GameViewModel) {
    ScreenScaffold("📖 Справка", vm) {
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("Короткие объяснения слов, которые встречаются в игре.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            SmallPill("? Как играть: три решения", filled = false) { vm.openHelp() }
        }
        for (t in Glossary.terms) {
            MontikCard {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(t.emoji, fontSize = 26.sp) }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(t.word, style = MaterialTheme.typography.titleMedium)
                        Text(t.meaning, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
