package ru.montik.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Catalog
import ru.montik.app.game.ClothingItem
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Job
import ru.montik.app.game.Rules
import ru.montik.app.game.Tier

// ───────────────────────── Общий каркас внутренних экранов ─────────────────────────

/** Пилюля с монетами из макета: золотая монета и число. */
@Composable
fun CoinPill(coins: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MontikShapes.Chip, color = MontikColors.Cream, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoinIcon(Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Text("$coins", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
        }
    }
}

/** Золотая монета — значок из макета. */
@Composable
fun CoinIcon(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(MontikColors.Coin, MontikColors.CoinDark))),
        contentAlignment = Alignment.Center
    ) {
        Text("♕", fontSize = 16.sp, color = Color(0xFFFFF3C4))
    }
}

/** Круглая кнопка «назад» в левом верхнем углу внутренних экранов. */
@Composable
fun BackCircle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(50.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("←", fontSize = 26.sp, color = MontikColors.Ink)
        }
    }
}

/**
 * Каркас внутреннего экрана в стиле макета: кнопка «назад», заголовок, монеты,
 * а ниже — прокручиваемый столбец белых карточек.
 */
@Composable
fun ScreenScaffold(
    title: String,
    vm: GameViewModel,
    background: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(MontikColors.Cream), contentAlignment = Alignment.TopCenter) {
        if (background != null) {
            ArtImage(background, Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(MontikColors.SurfaceTint, MontikColors.Cream))
                    )
                )
            }
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.72f)))
        }
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 17.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackCircle({ vm.back() })
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MontikColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                CoinPill(vm.state.coins)
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun MeterCard(vm: GameViewModel) {
    val s = vm.state
    MontikCard {
        StatBar("🍲", "Еда", s.food, MontikColors.Hunger)
        Spacer(Modifier.height(8.dp))
        StatBar("💧", "Вода", s.water, MontikColors.Phone)
        Spacer(Modifier.height(8.dp))
        StatBar("⚡", "Силы", s.energy, MontikColors.Energy)
    }
}

// ───────────────────────── Работа ─────────────────────────

@Composable
fun WorkScreen(vm: GameViewModel) {
    ScreenScaffold("Работа", vm, background = "bg_work") {
        val s = vm.state
        val shift = vm.activeShift
        val blocker = vm.workBlocker
        MeterCard(vm)

        if (blocker != null) {
            MontikCard(color = MontikColors.SurfaceTint) {
                Text("Сегодня работать нельзя", style = MaterialTheme.typography.titleMedium, color = MontikColors.Bad)
                Spacer(Modifier.height(6.dp))
                Text(blocker, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Еда, вода и сон — это не мелочь: без них Монтик просто не сможет работать.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
                Spacer(Modifier.height(12.dp))
                BigButton("🛍  В магазин за едой и водой", onClick = { vm.goTo(Screen.Shop) }, primary = false)
                Spacer(Modifier.height(8.dp))
                BigButton("😴  Пойти спать", onClick = { vm.goTo(Screen.Sleep) }, primary = false)
                if (vm.choreAvailable) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Монет на еду не хватает. Можно выполнить мелкое поручение — оно не требует сил.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    BigButton("🤝  Помочь соседке (+${Rules.CHORE_COINS} монет)", onClick = { vm.doChore() })
                }
            }
        } else {
            val eff = GameEngine.efficiency(s)
            MontikCard {
                Text(
                    if (eff >= 100) "Монтик полон сил и работает на все $eff%."
                    else "Монтик устал или голоден: он сможет сделать только $eff% работы и заработает меньше.",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (eff < 100) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Поешь, попей или поспи — тогда заработок будет больше.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft
                    )
                }
            }
        }

        if (shift == null) {
            for (job in Job.values()) {
                val status = GameEngine.jobStatus(s, job)
                MontikCard {
                    Text("${job.emoji} ${job.title}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(job.about, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ставка за смену: ${job.base} монет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.InkSoft
                    )
                    if (!status.unlocked) {
                        Spacer(Modifier.height(4.dp))
                        Text("🔒 ${job.requirements}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Осталось: ${status.hint}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MontikColors.InkSoft
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BigButton(
                        "Выйти на смену",
                        onClick = { vm.startShift(job) },
                        enabled = status.unlocked && blocker == null
                    )
                }
            }
        } else {
            MontikCard {
                Text("${shift.job.emoji} ${shift.job.title}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Задание на смене", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                Text(shift.task.question, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                shift.task.options.forEachIndexed { index, option ->
                    BigButton(option, onClick = { vm.answerTask(index) }, primary = false)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Верный ответ даёт премию +${Rules.TASK_BONUS_PERCENT}%.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
                Spacer(Modifier.height(8.dp))
                BigButton("Пропустить задание (без премии)", onClick = { vm.skipTask() }, primary = false)
                Spacer(Modifier.height(8.dp))
                BigButton("Отмена", onClick = { vm.cancelShift() }, primary = false)
            }
        }
    }
}

// ───────────────────────── Магазин ─────────────────────────

@Composable
private fun TabButtons(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(MontikShapes.Chip)
                    .clickable { onSelect(index) },
                shape = MontikShapes.Chip,
                color = if (active) MontikColors.Lime else Color.White,
                border = if (active) null else BorderStroke(2.dp, MontikColors.Ink)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MontikColors.Ink,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ShopScreen(vm: GameViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    ScreenScaffold("Магазин", vm, background = "bg_shop") {
        val s = vm.state
        TabButtons(listOf("Еда и вода", "Одежда", "Гардероб"), tab) { tab = it }
        when (tab) {
            0 -> {
                MontikCard(color = MontikColors.SurfaceTint) {
                    Text(
                        "Еда, вода и жильё — это нужное. Покупай их в первую очередь.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                for (item in Catalog.food) {
                    MontikCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.emoji, fontSize = 34.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                val effects = buildList {
                                    if (item.food > 0) add("еда +${item.food}")
                                    if (item.water > 0) add("вода +${item.water}")
                                }
                                Text(
                                    effects.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MontikColors.InkSoft
                                )
                            }
                            PriceButton(item.price, s.coins >= item.price) { vm.buyFood(item.id) }
                        }
                    }
                }
            }
            1 -> {
                MontikCard(color = MontikColors.SurfaceTint) {
                    Text(
                        "Простая вещь дешевле, но быстро изнашивается. Дорогая служит дольше. " +
                            "Сравни цену и срок службы. Опрятный вид добавляет к заработку.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                for (tier in Tier.values()) {
                    Text(
                        "${tier.title} одежда" +
                            if (tier.appearancePercent > 0) " (+${tier.appearancePercent}% к заработку за вещь)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MontikColors.Ink
                    )
                    for (item in Catalog.clothes.filter { it.tier == tier }) {
                        ClothingCard(item, s.coins >= item.price, s.worn[item.slot] == item.id) {
                            vm.buyClothing(item.id)
                        }
                    }
                }
            }
            else -> {
                if (s.owned.isEmpty()) {
                    MontikCard {
                        Text(
                            "В гардеробе пока пусто. Загляни во вкладку «Одежда».",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                for ((id, daysLeft) in s.owned) {
                    val item = Catalog.clothing(id) ?: continue
                    val worn = s.worn[item.slot] == id
                    MontikCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.emoji, fontSize = 34.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ещё ночей: $daysLeft из ${item.durabilityDays}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MontikColors.InkSoft
                                )
                            }
                            SmallPill(if (worn) "Снять" else "Надеть", filled = !worn) {
                                if (worn) vm.takeOff(item.slot) else vm.wear(id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClothingCard(item: ClothingItem, canBuy: Boolean, worn: Boolean, onBuy: () -> Unit) {
    MontikCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji, fontSize = 34.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.name + if (worn) " · надето" else "", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${item.slot.title}. Служит ${item.durabilityDays} ноч.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
            }
            PriceButton(item.price, canBuy, onBuy)
        }
    }
}

/** Ценник-кнопка: монета и цена. */
@Composable
private fun PriceButton(price: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(46.dp)
            .clip(MontikShapes.Chip)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MontikShapes.Chip,
        color = if (enabled) MontikColors.Lime else MontikColors.Track
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoinIcon(Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "$price",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MontikColors.Ink else MontikColors.InkSoft
            )
        }
    }
}

/** Небольшая кнопка-пилюля внутри карточки. */
@Composable
fun SmallPill(text: String, filled: Boolean = true, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(46.dp).clip(MontikShapes.Chip).clickable(onClick = onClick),
        shape = MontikShapes.Chip,
        color = if (filled) MontikColors.Lime else Color.White,
        border = if (filled) null else BorderStroke(2.dp, MontikColors.Ink)
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MontikColors.Ink)
        }
    }
}
