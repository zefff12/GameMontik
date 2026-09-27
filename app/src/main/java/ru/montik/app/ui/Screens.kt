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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BusinessEngine
import ru.montik.app.game.Catalog
import ru.montik.app.game.ClothingItem
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Job
import ru.montik.app.game.Life
import ru.montik.app.game.SpendKind
import ru.montik.app.game.Rules
import ru.montik.app.game.ShopWork
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
    // Монета с лапкой из нового макета; если картинки нет — нарисованная.
    if (rememberHasArt("fg_ui_c_coin")) {
        ArtImage("fg_ui_c_coin", modifier, androidx.compose.ui.layout.ContentScale.Fit) {}
        return
    }
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
    /** Картинка у заголовка (fg_ui_h_*) и рисованный низ страницы (fg_ui_foot_*) из макета. */
    icon: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(Page.Bg), contentAlignment = Alignment.TopCenter) {
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
                Spacer(Modifier.width(10.dp))
                if (icon != null) {
                    FigIcon(icon, "", 54.dp)
                    Spacer(Modifier.width(8.dp))
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
                if (footer != null) PageFooter(footer)
            }
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
    ScreenScaffold("Работа", vm, icon = "fg_ui_h_work", footer = "fg_ui_foot_work") {
        val s = vm.state
        val shift = vm.activeShift
        val blocker = vm.workBlocker
        // Шкалы еды, воды и сил — с рисованным Монтиком справа, как в макете.
        MontikCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StatBar("🍔", "Еда", s.food, MontikColors.Hunger)
                    Spacer(Modifier.height(6.dp))
                    StatBar("💧", "Вода", s.water, Color(0xFF7CC66B))
                    Spacer(Modifier.height(6.dp))
                    StatBar("⚡", "Силы", s.energy, MontikColors.Energy)
                }
                FigIcon("fg_ui_w_dog", "", 72.dp)
            }
        }

        if (blocker != null) {
            MontikCard(color = Page.Mint) {
                Row(verticalAlignment = Alignment.Top) {
                    FigIcon("fg_ui_w_face", "🐰", 56.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Сегодня работать нельзя", style = MaterialTheme.typography.titleMedium, color = Color(0xFF3F8A3A), modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(blocker, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Еда, вода и сон — это не мелочь: без них Монтик просто не сможет работать.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
                Spacer(Modifier.height(12.dp))
                BigButton("🛍  В магазин за едой и водой", onClick = { vm.goTo(Screen.Grocery) }, primary = false)
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
            MontikCard(color = Page.Mint) {
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
            StoreCard(vm, blocker == null)
            BankJobCard(vm, blocker == null)
            BusinessCard(vm)
            for (job in Job.values()) {
                val status = GameEngine.jobStatus(s, job)
                MontikCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FigIcon(if (job == Job.HELPER) "fg_ui_j_tools" else "fg_ui_j_seller", job.emoji, 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title, style = MaterialTheme.typography.titleMedium)
                            Text(job.about, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Ставка за смену: ${job.base} монет",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MontikColors.InkSoft
                            )
                        }
                    }
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

/** Работа в магазине рядом с домом: открывается после нескольких смен, ставка растёт с должностью. */
@Composable
private fun StoreCard(vm: GameViewModel, canWork: Boolean) {
    val s = vm.state
    val unlocked = ShopWork.unlocked(s)
    val rank = ShopWork.rank(s)
    MontikCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigIcon("fg_ui_j_store", "🛒", 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Магазин рядом с домом", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Две мини-игры на выбор: работа на кассе и выкладка товаров. " +
                        "Чем внимательнее и быстрее Монтик, тем больше премия.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (unlocked) "Должность: ${rank.title}. Ставка за смену: ${rank.base} монет + премия до 50%."
            else "🔒 Магазин откроется позже: ${ShopWork.lockHint(s)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        Spacer(Modifier.height(12.dp))
        BigButton("Пойти в магазин", onClick = { vm.goTo(Screen.Store) }, enabled = unlocked && canWork)
    }
}

/** Работа в банке: проверка купюр («Проверяй деньги!»). */
@Composable
private fun BankJobCard(vm: GameViewModel, canWork: Boolean) {
    val s = vm.state
    val unlocked = ru.montik.app.game.BankWork.unlocked(s)
    MontikCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏦", fontSize = 44.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Банк «для твоего будущего»", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Проверяй деньги: сравни купюру клиента с образцом и найди испорченные и поддельные.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val best = ru.montik.app.game.BankWork.bestStars(s)
        Text(
            if (unlocked) "Ставка за смену: ${ru.montik.app.game.BankWork.base(s)} монет + премия до 50%." +
                if (best > 0) "  Лучшая смена: " + "★".repeat(best) else ""
            else "🔒 Банк возьмёт на работу позже: ${ru.montik.app.game.BankWork.lockHint(s)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        Spacer(Modifier.height(12.dp))
        BigButton("Пойти в банк", onClick = { vm.goTo(Screen.BankJob) }, enabled = unlocked && canWork)
    }
}

/** Свой бизнес: цель «накопить 20 000», потом путь от места до открытия и управление магазином. */
@Composable
private fun BusinessCard(vm: GameViewModel) {
    val s = vm.state
    val b = s.business
    val wealth = BusinessEngine.wealth(s)
    MontikCard(color = if (b != null || BusinessEngine.canStart(s)) MontikColors.SurfaceTint else MontikColors.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigIcon("fg_ui_j_biz", "🏬", 64.dp)
            Spacer(Modifier.width(12.dp))
            Text("Свой бизнес", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        when {
            b == null && !BusinessEngine.canStart(s) -> {
                Text(
                    "Накопи ${spaced(BusinessEngine.GOAL_COINS)} монет — и Монтик сможет открыть собственный магазин.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (wealth.toFloat() / BusinessEngine.GOAL_COINS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(MontikShapes.Chip),
                    color = MontikColors.Coin,
                    trackColor = MontikColors.Track
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Накоплено: ${spaced(wealth)} из ${spaced(BusinessEngine.GOAL_COINS)} (кошелёк, подушка и вклад минус долг).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft
                )
            }
            b == null -> {
                Text(
                    "Монтик накопил достаточно денег. Пора открыть свой магазин!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                BigButton("Открыть свой бизнес", onClick = { vm.bizBegin() })
            }
            b.isOpen -> {
                Text(
                    "Магазин «${b.typeInfo?.title ?: ""}» работает. В кассе: ${b.till} монет, на складе на ${b.stock} покупателей.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                BigButton("Управлять магазином", onClick = { vm.goTo(Screen.Business) })
            }
            else -> {
                Text("Этап: «${b.stage.title}». Магазин ещё не открыт.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                BigButton("Продолжить", onClick = { vm.goTo(Screen.Business) })
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

/** Покупка, которую ребёнок подтверждает: цена, категория и что изменится у Монтика (ТЗ 2.5.6). */
private class PurchaseAsk(
    val emoji: String,
    val title: String,
    val price: Int,
    val kind: SpendKind,
    val effect: String,
    val buy: () -> Unit
)

@Composable
fun ShopScreen(vm: GameViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var ask by remember { mutableStateOf<PurchaseAsk?>(null) }
    ScreenScaffold("Вещи и радости", vm, background = "bg_shop") {
        val s = vm.state
        TabButtons(listOf("Еда", "Радости", "Одежда", "Гардероб"), tab) { tab = it }
        when (tab) {
            0 -> {
                MontikCard(color = MontikColors.SurfaceTint) {
                    Text(
                        "🧾 Еда и вода теперь продаются в магазине продуктов: складывай в корзину, оплачивай — " +
                            "и продукты окажутся в холодильнике на кухне.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(10.dp))
                    BigButton("🛒  В магазин продуктов", onClick = { vm.goTo(Screen.Grocery) })
                }
            }
            1 -> {
                MontikCard(color = MontikColors.SurfaceTint) {
                    Text(
                        "🎁 Желаемое: радости поднимают настроение, но без них можно обойтись. " +
                            "Сверься с планом: сколько ты отвёл на желаемое?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val plan = Life.currentPlan(s)
                    if (plan != null && plan.confirmed) {
                        Text(
                            "По плану на желаемое: ${plan.wants}, уже потрачено: ${s.pWants}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (s.pWants > plan.wants) MontikColors.Bad else MontikColors.InkSoft
                        )
                    }
                }
                for (item in Catalog.joys) {
                    MontikCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.emoji, fontSize = 34.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Желаемое · настроение +${item.mood}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MontikColors.InkSoft
                                )
                            }
                            PriceButton(item.price, true) {
                                ask = PurchaseAsk(item.emoji, item.name, item.price, SpendKind.WANT, "настроение +${item.mood}") {
                                    vm.buyJoy(item.id)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                MontikCard(color = MontikColors.SurfaceTint) {
                    Text(
                        "Простая одежда — обязательное, красивая — желаемое. Дешёвая вещь быстро изнашивается, " +
                            "дорогая служит дольше. Опрятный вид добавляет к заработку.",
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
                        ClothingCard(item, true, s.worn[item.slot] == item.id) {
                            val kind = Catalog.kindOf(item)
                            ask = PurchaseAsk(
                                item.emoji, item.name, item.price, kind,
                                "прослужит ${item.durabilityDays} ноч." +
                                    (if (item.tier.appearancePercent > 0) ", +${item.tier.appearancePercent}% к заработку" else "")
                            ) { vm.buyClothing(item.id) }
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

    val a = ask
    if (a != null) {
        val s = vm.state
        val problem = GameEngine.cannotAfford(s, a.price)
        AlertDialog(
            onDismissRequest = { ask = null },
            containerColor = MontikColors.Surface,
            title = { Text("${a.emoji} ${a.title}") },
            text = {
                Column {
                    Text("Цена: ${a.price} монет", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Категория: ${a.kind.title.lowercase()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (a.kind == SpendKind.NEED) MontikColors.Good else MontikColors.WalletDeep
                    )
                    Text("Для Монтика: ${a.effect}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    if (problem != null) {
                        Text(problem, style = MaterialTheme.typography.bodyMedium, color = MontikColors.Bad)
                    } else {
                        Text(
                            "В кошельке останется ${s.coins - a.price}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MontikColors.InkSoft
                        )
                    }
                }
            },
            confirmButton = {
                if (problem == null) {
                    TextButton(onClick = {
                        a.buy()
                        ask = null
                    }) { Text("Купить за ${a.price}") }
                }
            },
            dismissButton = {
                TextButton(onClick = { ask = null }) { Text(if (problem == null) "Не сейчас" else "Понятно") }
            }
        )
    }
}

private fun foodEffect(food: Int, water: Int): String = buildList {
    if (food > 0) add("еда +$food")
    if (water > 0) add("вода +$water")
}.joinToString(", ")

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
fun SmallPill(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(46.dp).clip(MontikShapes.Chip).clickable(onClick = onClick),
        shape = MontikShapes.Chip,
        color = if (filled) MontikColors.Lime else Color.White,
        border = if (filled) null else BorderStroke(2.dp, MontikColors.Ink)
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MontikColors.Ink)
        }
    }
}
