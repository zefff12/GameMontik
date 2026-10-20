package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ru.montik.app.GameViewModel
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medals
import ru.montik.app.game.Praise
import ru.montik.app.game.Progress
import ru.montik.app.game.Skill
import ru.montik.app.game.TaskTheme
import ru.montik.app.game.Tasks

/*
 * Панель родителя в стиле макета «Для родителей» (Frame 69): карточки-разделы, строки-плашки
 * «значок · подпись · значение». Все строки одной высоты, текст в каждой — ровно по центру по
 * вертикали, значения выровнены по правому краю одной колонкой, ничего не переносится вкривь.
 */

private val DInk = Color(0xFF14246B)
private val DSub = Color(0xFF6A7891)
private val DCardBg = Color(0xFFF7FBF3)
private val DCardBorder = Color(0xFFE1EED9)
private val DRowBorder = Color(0xFFDDEBD5)
private val DGreenTop = Color(0xFFE6F7DA)
private val DGreenBottom = Color(0xFFC9EBB3)
private val DGreenEdge = Color(0xFF7CC26B)

@Composable
private fun DesignScope.DCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.requiredWidth(d(388f)).clip(RoundedCornerShape(d(22f))).background(DCardBg)
            .border(d(1.5f), DCardBorder, RoundedCornerShape(d(22f))).padding(d(12f)),
        verticalArrangement = Arrangement.spacedBy(d(8f))
    ) {
        Text(title, color = DInk, fontSize = fs(20f, false), fontWeight = FontWeight.Black, modifier = Modifier.padding(start = d(4f), bottom = d(2f)))
        content()
    }
}

/** Строка-плашка: круглый значок, подпись (по центру строки) и значение справа. */
@Composable
private fun DesignScope.DRow(icon: String, label: String, value: String, coin: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(d(46f)).clip(RoundedCornerShape(d(23f)))
            .background(Brush.horizontalGradient(listOf(Color.White, Color(0xFFF2F9EC))))
            .border(d(1.2f), DRowBorder, RoundedCornerShape(d(23f)))
            .padding(start = d(4f), end = d(12f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(d(38f)).clip(CircleShape).background(Color.White).border(d(1f), DRowBorder, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = fs(19f, false))
        }
        Spacer(Modifier.width(d(10f)))
        Text(
            label, color = DInk, fontSize = fs(14.5f, false), fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(d(6f)))
        Text(value, color = DInk, fontSize = fs(15f, false), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
        if (coin) {
            Spacer(Modifier.width(d(5f)))
            ArtImage("fg4_coin", Modifier.size(d(20f)), ContentScale.Fit) {}
        }
    }
}

/** Блок текста в рамке (цель, события). */
@Composable
private fun DesignScope.DNote(text: String, sub: String? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(d(18f))).background(Color.White)
            .border(d(1.2f), DRowBorder, RoundedCornerShape(d(18f))).padding(horizontal = d(14f), vertical = d(10f)),
        verticalArrangement = Arrangement.spacedBy(d(3f))
    ) {
        Text(text, color = DInk, fontSize = fs(14f, false), fontWeight = FontWeight.Bold, lineHeight = fs(17f, false))
        if (sub != null) Text(sub, color = DSub, fontSize = fs(12.5f, false), lineHeight = fs(15f, false))
    }
}

/** Зелёная кнопка как «Сменить PIN» в макете. */
@Composable
private fun DesignScope.DButton(text: String, h: Float = 48f, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(d(h)).clip(RoundedCornerShape(d(h / 2f)))
            .background(Brush.verticalGradient(listOf(DGreenTop, DGreenBottom)))
            .border(d(2f), DGreenEdge, RoundedCornerShape(d(h / 2f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF173F17), fontSize = fs(16f, false), fontWeight = FontWeight.Black, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DesignScope.DField(value: String, hint: String, digits: Int, secret: Boolean = false, onValue: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(d(48f)).clip(RoundedCornerShape(d(12f))).background(Color.White)
            .border(d(1.5f), Color(0xFF5C6F80), RoundedCornerShape(d(12f))).padding(horizontal = d(16f)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, color = DInk.copy(alpha = 0.55f), fontSize = fs(15f, false), fontWeight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = { v -> onValue(if (digits > 0) v.filter { it.isDigit() }.take(digits) else v.take(Praise.MAX_LENGTH)) },
            singleLine = true,
            textStyle = TextStyle(color = DInk, fontSize = fs(15f, false), fontWeight = FontWeight.Bold),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (digits > 0) KeyboardType.NumberPassword else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DesignScope.DChecked(title: String, done: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(d(14f))).background(Color.White)
            .border(d(1.2f), DRowBorder, RoundedCornerShape(d(14f))).padding(horizontal = d(10f), vertical = d(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(d(24f)).clip(RoundedCornerShape(d(6f)))
                .background(if (done) Color(0xFF34B53A) else Color(0xFFF1F2F1))
                .border(d(1.5f), if (done) Color(0xFF1E8E2A) else Color(0xFFC9CDCB), RoundedCornerShape(d(6f))),
            contentAlignment = Alignment.Center
        ) { if (done) Text("✓", color = Color.White, fontSize = fs(15f, false), fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(d(10f)))
        Text(title, color = if (done) DInk else DSub, fontSize = fs(13.5f, false), fontWeight = FontWeight.Bold, lineHeight = fs(16f, false), modifier = Modifier.weight(1f))
    }
}

/** Вся панель родителя — разделы друг под другом. */
@Composable
fun DesignScope.ParentDashboard(vm: GameViewModel, onReset: () -> Unit, onTest: () -> Unit) {
    val s = vm.state
    Column(
        Modifier.requiredWidth(d(DESIGN_W)).padding(top = d(10f), bottom = d(24f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(d(14f))
    ) {
        DCard("Общий прогресс") {
            DRow("📅", "Игровой день", "${s.day}")
            DRow("🛠️", "Смен отработано", "${s.shifts}")
            DRow("👛", "В кошельке", spaced(s.coins), coin = true)
            DRow("🛟", "В подушке безопасности", spaced(s.cushion), coin = true)
            DRow("🏦", "На вкладе в банке", spaced(s.deposit), coin = true)
            DRow("🏅", "Медалей получено", "${Medals.earned(s).size} из ${Medals.all.size}")
            DRow("🎯", "Заданий решено верно", "${s.tasksCorrect}")
            DRow("💰", "Всего заработано", spaced(s.totalEarned), coin = true)
            DRow("％", "Налог 13% уплачен", spaced(s.totalTax), coin = true)
            DRow("🛍️", "Всего потрачено", spaced(s.totalSpent), coin = true)
            val visited = s.completedTrips.mapNotNull { Destinations.byId(it)?.name }
            DNote(
                "Цель: " + GameEngine.currentGoal(s).dropWhile { !it.isLetterOrDigit() },
                if (visited.isEmpty()) "Путешествий пока не было." else "Побывали: ${visited.joinToString(", ")}"
            )
        }
        DCard("Финансовые привычки") {
            DRow("⭐", "Уровень Монтика", "${Progress.levelOf(s.xp)}")
            DRow("🏠", "Жильё", s.home.title)
            DRow("📊", "Периодов пройдено", "${s.periods.size}")
            DRow("🐷", "Накоплено (копилка + подушка)", spaced(s.savings), coin = true)
            DRow("✅", "Заданий пройдено", "${Tasks.doneCount(s)} из ${Tasks.all.size}")
            DRow("📢", "Реклама: закрыто / куплено", "${s.adsDeclined} / ${s.adsBought}")
            for (theme in TaskTheme.values()) {
                val list = Tasks.all.filter { it.theme == theme }
                DRow(theme.emoji, theme.title, "${list.count { it.id in s.taskResults }} из ${list.size}")
            }
            DNote("Здесь нет оценок «плохо/хорошо».", "Ошибки в игре — это учебные ситуации, их можно исправить в следующем периоде.")
        }
        DCard("Похвалить ребёнка") {
            Text(
                "Сообщение появится у ребёнка на главном экране — это сильнее любой награды в игре.",
                color = DSub, fontSize = fs(13f, false), lineHeight = fs(16f, false), modifier = Modifier.padding(horizontal = d(4f))
            )
            for (text in Praise.quick) DButton(text, 44f) { vm.sendPraise(text) }
            var msg by rememberSaveable { mutableStateOf("") }
            DField(msg, "Своё сообщение", 0) { msg = it }
            DButton("Отправить") {
                if (msg.isNotBlank()) {
                    vm.sendPraise(msg)
                    msg = ""
                } else vm.say("Сначала напишите сообщение.")
            }
        }
        DCard("Игра") {
            DButton(if (s.soundOn) "🔇  Выключить звуки" else "🔊  Включить звуки") { vm.setSound(!s.soundOn) }
            DButton(if (s.demo) "Выключить демо-режим" else "Включить демо-режим") { vm.setDemo(!s.demo) }
            DButton("Сбросить тестовый профиль", onClick = onTest)
        }
        DCard("Чит-панель") {
            DRow("👛", "Монет в кошельке", spaced(s.coins), coin = true)
            Row(horizontalArrangement = Arrangement.spacedBy(d(8f))) {
                for (n in listOf(100, 500, 1000)) Box(Modifier.weight(1f)) { DButton("+$n", 44f) { vm.cheatCoins(n) } }
            }
            var amount by rememberSaveable { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(d(8f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.3f)) { DField(amount, "Сколько монет", 6) { amount = it } }
                Box(Modifier.weight(1f)) {
                    DButton("Добавить") {
                        val n = amount.toIntOrNull() ?: 0
                        if (n > 0) { vm.cheatCoins(n); amount = "" }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(d(8f))) {
                Box(Modifier.weight(1f)) { DButton("+1 день", 44f) { vm.cheatSkipDays(1) } }
                Box(Modifier.weight(1f)) { DButton("+10 дней", 44f) { vm.cheatSkipDays(10) } }
            }
            DButton("🦝  Позвать енота на рынок") { vm.cheatBarterNow() }
            DButton("🎨  Открыть все скины и краски") { vm.cheatUnlockSkins() }
            DButton("💳  Обнулить кредиты и долги") { vm.cheatClearCredits() }
        }
        val lessons = Lesson.values()
        DCard("Цели обучения: ${lessons.count { it.name in s.seenLessons }} из ${lessons.size}") {
            for (l in lessons) DChecked("${l.title}: ${l.goal}", l.name in s.seenLessons)
        }
        DCard("Навыки Монтика") {
            for (skill in Skill.values()) {
                DRow(skill.emoji, skill.title, "уровень ${s.skillLevel(skill)} · очков ${s.skillPoints(skill)}")
            }
        }
        DCard("Последние события") {
            val events = s.log.takeLast(3).reversed()
            DNote(if (events.isEmpty()) "Пока событий нет." else events.joinToString("\n") { "•  $it" })
        }
        DCard("Настройки") {
            var newPin by rememberSaveable { mutableStateOf("") }
            DField(newPin, "Новый PIN (4 цифры)", 4, secret = true) { newPin = it }
            DButton("Сменить PIN") {
                if (newPin.length == 4) { vm.changePin(newPin); newPin = "" } else vm.say("Новый PIN — 4 цифры.")
            }
            DButton("Удалить весь прогресс", onClick = onReset)
            Text(
                "Данные хранятся только на этом устройстве. Удаление стирает прогресс и рисунок Монтика.",
                color = DSub, fontSize = fs(12.5f, false), lineHeight = fs(15f, false), modifier = Modifier.padding(horizontal = d(4f))
            )
        }
    }
}
