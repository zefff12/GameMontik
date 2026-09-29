package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BizEquip
import ru.montik.app.game.BizPlace
import ru.montik.app.game.BizStage
import ru.montik.app.game.BizType
import ru.montik.app.game.Business
import ru.montik.app.game.BusinessEngine

/*
 * Свой магазин Монтика: предложение открыть дело, поиск места, стройка, оборудование, первые товары
 * и управление открытым магазином. Правила и расчёты — в game/Business.kt.
 * Кадры макета: «Расположение своего магазина», «Строительство своего магазина» (картинки fg_biz_*).
 */

/** Число с пробелами между тысячами: 20 000. */
fun spaced(n: Int): String {
    val sign = if (n < 0) "-" else ""
    return sign + kotlin.math.abs(n).toString().reversed().chunked(3).joinToString(" ").reversed()
}

// ───────────────────────── Предложение открыть бизнес ─────────────────────────

/** Всплывает, когда накоплено 20 000 монет: Монтик сам предлагает открыть свой магазин. */
@Composable
fun BusinessOfferDialog(vm: GameViewModel) {
    Dialog(onDismissRequest = { vm.bizPostpone() }) {
        MontikCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MontikView(sprite = vm.sprite, state = vm.state, boxSize = 150.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text("💡 Новая цель", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Я уже накопил достаточно денег! А что, если открыть собственный магазин? " +
                    "Тогда я смогу сам управлять своим делом и зарабатывать ещё больше!",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            BigButton("Открыть свой бизнес", onClick = { vm.bizBegin() })
            Spacer(Modifier.height(8.dp))
            BigButton("Пока рано", onClick = { vm.bizPostpone() }, primary = false)
        }
    }
}

// ───────────────────────── Экран бизнеса ─────────────────────────

@Composable
fun BusinessScreen(vm: GameViewModel) {
    BackHandler(enabled = true) { vm.goTo(Screen.Work) }
    val b = vm.state.business
    if (b == null) {
        LaunchedEffect(Unit) { vm.goTo(Screen.Work) }
        return
    }
    when (b.stage) {
        BizStage.PLACE -> PlaceStage(vm)
        BizStage.TYPE -> TypeStage(vm, b)
        BizStage.BUILD -> BuildStage(vm, b)
        BizStage.EQUIP -> EquipStage(vm, b)
        BizStage.STOCK -> StockStage(vm, b)
        BizStage.OPEN -> OwnerDashboard(vm, b)
    }
}

// ───────────────────────── Мелкие детали ─────────────────────────

/** Пять этапов пути к магазину: пройденные закрашены, текущий подписан. */
@Composable
private fun StageDots(stage: BizStage) {
    val steps = BizStage.values().filter { it != BizStage.OPEN }
    MontikCard(color = MontikColors.SurfaceTint) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { i, step ->
                val done = step.ordinal < stage.ordinal
                val current = step == stage
                Box(
                    Modifier
                        .size(if (current) 40.dp else 32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> MontikColors.LimeDeep
                                current -> MontikColors.Coin
                                else -> MontikColors.Track
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (done) "✓" else "${i + 1}", style = MaterialTheme.typography.labelMedium, color = MontikColors.Ink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Этап ${stage.ordinal + 1} из ${steps.size}: ${stage.title}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: Color = MontikColors.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

/** Карточка выбора: значок, название, строки с цифрами и кнопка. */
@Composable
private fun OptionCard(
    emoji: String,
    title: String,
    lines: List<String>,
    buttonText: String,
    enabled: Boolean,
    warning: String? = null,
    onClick: () -> Unit
) {
    MontikCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 34.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                for (line in lines) {
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                }
            }
        }
        if (warning != null) {
            Spacer(Modifier.height(6.dp))
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MontikColors.Bad)
        }
        Spacer(Modifier.height(10.dp))
        BigButton(buttonText, onClick = onClick, enabled = enabled)
    }
}

@Composable
private fun StreetBackground() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF9CCBF2), Color(0xFFF6E3C4), Color(0xFFC9B79C)))
        )
    )
}

@Composable
private fun DesignScope.StreetFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Color(0xFFD9C9A8), Offset(0f, 620f * k), Size(size.width, size.height - 620f * k))
        val houses = listOf(Color(0xFFE9D8B6), Color(0xFFDCC7A0), Color(0xFFEFE2C6))
        for (i in 0 until 3) {
            drawRoundRect(
                houses[i],
                topLeft = Offset((8f + i * 140f) * k, 60f * k),
                size = Size(130f * k, 560f * k),
                cornerRadius = CornerRadius(10f * k)
            )
            drawRect(Color(0xFF39505F), Offset((28f + i * 140f) * k, 420f * k), Size(90f * k, 200f * k))
        }
    }
}

@Composable
private fun DesignScope.BuildFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Brush.verticalGradient(listOf(Color(0xFFCFC9BC), Color(0xFF9A968D))), size = size)
        drawRect(Color(0xFF3A3F42), Offset(130f * k, 180f * k), Size(240f * k, 300f * k))
        drawRect(Color(0xFFBFD7E8), Offset(142f * k, 192f * k), Size(216f * k, 276f * k))
        drawRect(Color(0xFF8B877E), Offset(0f, 560f * k), Size(size.width, size.height - 560f * k))
    }
}

// ───────────────────────── Этап 1. Место ─────────────────────────

@Composable
private fun PlaceStage(vm: GameViewModel) {
    // 0 — идея, 1 — Монтик телепортируется, 2 — выбираем место на улице.
    var phase by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<BizPlace?>(null) }
    LaunchedEffect(phase) {
        if (phase == 1) {
            delay(1700)
            phase = 2
        }
    }
    when (phase) {
        0 -> ScreenScaffold("Свой магазин", vm) {
            StageDots(BizStage.PLACE)
            MontikCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MontikView(sprite = vm.sprite, state = vm.state, boxSize = 150.dp)
                }
                Text(
                    "Магазин нужно поставить в хорошем месте: там, где ходит много людей. " +
                        "Но чем оживлённее улица, тем дороже аренда — придётся выбирать.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                BigButton("Искать место для магазина", onClick = { phase = 1 })
            }
        }
        1 -> TeleportScreen()
        else -> PlaceChooser(vm, chosen) { chosen = it }
    }
}

@Composable
private fun TeleportScreen() {
    val transition = rememberInfiniteTransition(label = "teleport")
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color.White, Color(0xFFB7E4FF), Color(0xFF7A6CFF)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("✨", fontSize = (76 * pulse).sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Монтик телепортируется на улицу, где можно открыть магазин…",
                style = MaterialTheme.typography.titleLarge,
                color = MontikColors.Ink,
                textAlign = TextAlign.Center
            )
        }
    }
}

private class PinSpot(val place: BizPlace, val x: Float, val y: Float)

private val PIN_SPOTS = listOf(
    PinSpot(BizPlace.ALLEY, 22f, 112f),
    PinSpot(BizPlace.DISTRICT, 196f, 196f),
    PinSpot(BizPlace.CENTER, 22f, 280f)
)

@Composable
private fun PlaceChooser(vm: GameViewModel, chosen: BizPlace?, onChoose: (BizPlace) -> Unit) {
    val s = vm.state
    DesignCanvas(background = {
        ArtImage("fg_biz_street", Modifier.fillMaxSize(), ContentScale.Crop) { StreetBackground() }
    }) {
        Art("fg_biz_street", -54f, -175f, 768f, 1152f)
        if (!rememberHasArt("fg_biz_street")) StreetFallback()
        Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
        Box(Modifier.at(212f, 10f, 190f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }

        // Три места, которые нашёл Монтик.
        for (spot in PIN_SPOTS) {
            val active = spot.place == chosen
            Box(
                Modifier
                    .at(spot.x, spot.y, 194f, 48f)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) MontikColors.Lime else Color.White)
                    .clickable { onChoose(spot.place) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "📍 ${spot.place.title}",
                    color = MontikColors.Ink,
                    fontSize = fs(15f, false),
                    fontWeight = FontWeight.Bold,
                    fontFamily = MontikFont,
                    maxLines = 1
                )
            }
        }

        MontikView(sprite = vm.sprite, state = s, boxSize = d(170f), modifier = Modifier.at(236f, 360f))
        if (chosen != null) {
            Bubble("Я думаю это лучшее место для моего магазина!", 20f, 392f, 240f, 90f, size = 15f)
        }

        // Панель с цифрами выбранного места.
        Box(
            Modifier.at(17f, 560f, 378f, 345f).clip(RoundedCornerShape(d(26f))).background(Color.White.copy(alpha = 0.95f))
        )
        if (chosen == null) {
            DText("Выбери место: нажми на метку 📍 на улице.", 34f, 590f, 344f, 18f, mono = false, align = TextAlign.Center)
            DText(
                "Тихий переулок дёшево, но людей мало. Центральная улица дорогая, зато прохожих больше всего.",
                34f, 660f, 344f, 14f, bold = false, mono = false, color = MontikColors.InkSoft, align = TextAlign.Center
            )
        } else {
            val ok = s.coins >= chosen.price
            DText("${chosen.emoji} ${chosen.title}", 34f, 574f, 344f, 20f, mono = false)
            DText(chosen.about, 34f, 604f, 344f, 13f, bold = false, mono = false, color = MontikColors.InkSoft)
            DText("Оформление аренды (один раз): ${spaced(chosen.price)} монет", 34f, 660f, 344f, 14f, bold = false, mono = false)
            DText("Аренда каждый день: ${chosen.rent} монет", 34f, 686f, 344f, 14f, bold = false, mono = false)
            DText("Прохожих в день: около ${chosen.traffic}", 34f, 712f, 344f, 14f, bold = false, mono = false)
            DText("В кошельке: ${spaced(s.coins)} монет", 34f, 738f, 344f, 14f, mono = false)
            if (!ok) {
                DText(
                    "Не хватает ${chosen.price - s.coins} монет в кошельке. Сними деньги с вклада или подушки: Телефон → Банк.",
                    34f, 766f, 344f, 13f, bold = false, mono = false, color = MontikColors.Bad
                )
            }
            DPill("Арендовать место: −${spaced(chosen.price)}", 34f, 820f, 344f, 64f, fill = MontikColors.Lime, textColor = MontikColors.Ink, enabled = ok) {
                vm.bizRentPlace(chosen.id)
            }
        }
    }
}

// ───────────────────────── Этап 2. Что продаём ─────────────────────────

@Composable
private fun TypeStage(vm: GameViewModel, b: Business) {
    ScreenScaffold("Что продаём?", vm) {
        StageDots(BizStage.TYPE)
        MontikCard(color = MontikColors.SurfaceTint) {
            Text(
                "Место: ${b.placeInfo?.title ?: ""}. Выбери, чем будет торговать магазин. " +
                    "У каждого товара свой средний чек, своя закупочная цена и своё число покупателей.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        for (type in BizType.values()) {
            OptionCard(
                emoji = type.emoji,
                title = type.title,
                lines = listOf(
                    type.about,
                    "Средний чек: ${type.basket} монет, товары обходятся в ${type.unitCost}.",
                    "Прибыль с одного покупателя: ${type.margin} монет.",
                    "Покупатели заходят: ${footfallWord(type)}."
                ),
                buttonText = "Торговать: ${type.title.lowercase()}",
                enabled = true
            ) { vm.bizChooseType(type.id) }
        }
    }
}

private fun footfallWord(type: BizType): String = when {
    type.footfall >= 90 -> "часто"
    type.footfall >= 60 -> "реже"
    else -> "редко"
}

// ───────────────────────── Этап 3. Строительство ─────────────────────────

@Composable
private fun BuildStage(vm: GameViewModel, b: Business) {
    val s = vm.state
    val place = b.placeInfo
    var building by remember { mutableStateOf(false) }
    LaunchedEffect(building) {
        if (building) {
            delay(1800)
            vm.bizBuild()
            building = false
        }
    }
    val cost = place?.buildCost ?: 0
    val ok = s.coins >= cost
    DesignCanvas(background = {
        ArtImage("fg_biz_build_bg", Modifier.fillMaxSize(), ContentScale.Crop) { StreetBackground() }
    }) {
        Art("fg_biz_build_bg", 0f, 0f, 418f, 931f)
        if (!rememberHasArt("fg_biz_build_bg")) BuildFallback()
        Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
        Box(Modifier.at(212f, 10f, 190f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }

        // Монтик-строитель: тот герой, которого создал ребёнок, с каской и молотком поверх.
        MontikView(sprite = vm.sprite, state = s, boxSize = d(250f), modifier = Modifier.at(152f, 560f))
        Text("🔨", fontSize = fs(46f, false), modifier = Modifier.at(150f, 600f))
        Text("⛑", fontSize = fs(46f, false), modifier = Modifier.at(250f, 545f))

        Bubble(
            if (building) "Идёт стройка… Стучат молотки!" else "Давай начнем строительство!",
            51f, 454f, 279f, 68f, size = 16f
        )
        if (building) {
            // Полоска «идёт стройка».
            val transition = rememberInfiniteTransition(label = "build")
            val p by transition.animateFloat(0.05f, 1f, infiniteRepeatable(tween(1800)), label = "progress")
            Box(Modifier.at(60f, 760f, 292f, 18f)) {
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(50)),
                    color = MontikColors.Lime,
                    trackColor = Color.White
                )
            }
        } else {
            DText(
                "Ремонт и подготовка помещения: ${spaced(cost)} монет.",
                40f, 770f, 332f, 14f, mono = false, color = Color.White, align = TextAlign.Center
            )
            if (!ok) {
                DText(
                    "Не хватает ${cost - s.coins} монет в кошельке.",
                    40f, 796f, 332f, 13f, mono = false, color = Color(0xFFFFC7C1), align = TextAlign.Center
                )
            }
            DPill("Построить: −${spaced(cost)}", 60f, 830f, 292f, 62f, enabled = ok) { building = true }
        }
    }
}

// ───────────────────────── Этап 4. Оборудование ─────────────────────────

@Composable
private fun EquipStage(vm: GameViewModel, b: Business) {
    val s = vm.state
    val type = b.typeInfo
    val minStock = if (type != null) BusinessEngine.stockCost(type, BusinessEngine.FIRST_STOCK.first()) else 0
    ScreenScaffold("Оборудование", vm) {
        StageDots(BizStage.EQUIP)
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("Ты решаешь, на что потратить деньги", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Хорошее оборудование привлекает больше покупателей и вмещает больше людей, но стоит дороже. " +
                    "Останется меньше денег на товары, а без товаров магазин не заработает. " +
                    "Хотя бы на самую маленькую закупку нужно оставить $minStock монет.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text("В кошельке: ${spaced(s.coins)} монет", style = MaterialTheme.typography.bodyLarge)
        }
        for (equip in BizEquip.values()) {
            val left = s.coins - equip.price
            OptionCard(
                emoji = equip.emoji,
                title = equip.title,
                lines = listOf(
                    equip.about,
                    "Принимает до ${equip.capacity} покупателей в день, привлекает на ${equip.attract - 100}% больше.",
                    "Содержание: ${equip.upkeep} монет в день.",
                    "Останется на товары: ${spaced(left.coerceAtLeast(0))} монет."
                ),
                buttonText = "Купить: −${spaced(equip.price)}",
                enabled = s.coins >= equip.price,
                warning = when {
                    s.coins < equip.price -> "Не хватает ${equip.price - s.coins} монет."
                    left < minStock -> "После покупки не хватит денег на первые товары."
                    else -> null
                }
            ) { vm.bizEquip(equip.level) }
        }
    }
}

// ───────────────────────── Этап 5. Первые товары ─────────────────────────

@Composable
private fun StockStage(vm: GameViewModel, b: Business) {
    val s = vm.state
    val type = b.typeInfo ?: return
    val served = BusinessEngine.expectedServed(b).coerceAtLeast(1)
    ScreenScaffold("Первые товары", vm) {
        StageDots(BizStage.STOCK)
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("Сколько товаров закупить?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "В обычный день сюда придёт около ${BusinessEngine.demand(b)} покупателей, а магазин успеет обслужить $served. " +
                    "Если товара не хватит, покупатели уйдут ни с чем. Если купить слишком много, деньги лежат на складе без дела.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text("В кошельке: ${spaced(s.coins)} монет", style = MaterialTheme.typography.bodyLarge)
        }
        for (baskets in BusinessEngine.FIRST_STOCK) {
            val cost = BusinessEngine.stockCost(type, baskets)
            val days = baskets.toFloat() / served
            OptionCard(
                emoji = "📦",
                title = "Товары на $baskets покупателей",
                lines = listOf(
                    "Хватит примерно на ${oneDecimal(days)} дн. торговли.",
                    "Цена закупки: ${spaced(cost)} монет.",
                    "Останется в кошельке: ${spaced((s.coins - cost).coerceAtLeast(0))} монет."
                ),
                buttonText = "Закупить и открыть магазин: −${spaced(cost)}",
                enabled = s.coins >= cost,
                warning = if (s.coins < cost) "Не хватает ${cost - s.coins} монет." else null
            ) { vm.bizStock(baskets) }
        }
    }
}

private fun oneDecimal(v: Float): String {
    val tenths = (v * 10f + 0.5f).toInt()
    return "${tenths / 10},${tenths % 10}"
}

// ───────────────────────── Магазин открыт: управление ─────────────────────────

@Composable
private fun OwnerDashboard(vm: GameViewModel, b: Business) {
    val s = vm.state
    val type = b.typeInfo ?: return
    val equip = b.equipInfo
    val report = b.report
    ScreenScaffold("Мой магазин", vm) {
        // Шапка: что за магазин и что с ним сейчас.
        MontikCard(color = MontikColors.SurfaceTint) {
            Text("${type.emoji} Магазин «${type.title}»", style = MaterialTheme.typography.titleLarge)
            Text(
                "${b.placeInfo?.title ?: ""} · ${equip?.title ?: ""}" +
                    if (b.expansion > 0) " · расширен ×${b.expansion}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            if (b.adDays > 0) {
                Spacer(Modifier.height(4.dp))
                Text("📣 Реклама работает: ещё ${b.adDays} дн.", style = MaterialTheme.typography.bodyMedium)
            }
            if (b.broken) {
                Spacer(Modifier.height(6.dp))
                Text("🔧 Касса сломалась: магазин работает вполсилы.", style = MaterialTheme.typography.bodyLarge, color = MontikColors.Bad)
                Spacer(Modifier.height(8.dp))
                BigButton(
                    "Починить кассу: −${BusinessEngine.REPAIR_PRICE}",
                    onClick = { vm.bizRepair() },
                    enabled = s.coins >= BusinessEngine.REPAIR_PRICE
                )
            }
        }

        // Совет владельцу.
        val tips = advice(b)
        if (tips.isNotEmpty()) {
            MontikCard {
                Text("💡 Совет", style = MaterialTheme.typography.titleMedium)
                for (tip in tips) {
                    Spacer(Modifier.height(4.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Касса.
        MontikCard {
            val debtPaid = minOf(b.till, b.overdue)
            val taxable = b.till - debtPaid
            val tax = BusinessEngine.tax(taxable)
            Text("💰 Касса магазина: ${spaced(b.till)} монет", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Деньги в кассе — деньги магазина. Твоими они станут, когда ты их заберёшь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            if (b.overdue > 0) {
                Text("Долг по аренде: ${spaced(b.overdue)}. Он спишется из выручки первым.", style = MaterialTheme.typography.bodyMedium, color = MontikColors.Bad)
            }
            if (b.till > 0) {
                Text(
                    "Налог ${BusinessEngine.TAX_PERCENT}%: $tax. Тебе достанется: ${spaced(taxable - tax)}.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(10.dp))
            BigButton("Забрать выручку", onClick = { vm.bizCollect() }, enabled = b.till > 0)
        }

        // Итоги последнего дня.
        MontikCard {
            Text("🧾 Итоги последнего дня", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (report == null) {
                Text(
                    "Первый торговый день ещё впереди. Ляг спать, а утром здесь появится отчёт.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("День ${report.day}", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
                report.event?.let {
                    Text("⚡ $it", style = MaterialTheme.typography.bodyMedium)
                }
                StatLine("Хотели зайти", "${report.demand}")
                StatLine("Обслужено", "${report.served}")
                if (report.lost > 0) StatLine("Ушли без покупки", "${report.lost}", MontikColors.Bad)
                StatLine("Выручка", "+${spaced(report.revenue)}")
                StatLine("Товары", "−${spaced(report.goodsCost)}")
                StatLine("Аренда", "−${spaced(report.rent)}")
                StatLine("Содержание", "−${spaced(report.upkeep)}")
                StatLine(
                    "Прибыль",
                    (if (report.profit >= 0) "+" else "") + spaced(report.profit),
                    if (report.profit >= 0) MontikColors.Good else MontikColors.Bad
                )
            }
        }

        // Склад и закупка.
        MontikCard {
            val days = BusinessEngine.stockDays(b)
            Text("📦 Склад: товаров на ${b.stock} покупателей", style = MaterialTheme.typography.titleMedium)
            Text(
                "Хватит примерно на ${oneDecimal(days)} дн. Каждый покупатель уносит часть товара.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
            Spacer(Modifier.height(10.dp))
            for (baskets in BusinessEngine.RESTOCK_OPTIONS) {
                val cost = BusinessEngine.stockCost(type, baskets)
                BigButton(
                    "Закупить на $baskets покупателей: −${spaced(cost)}",
                    onClick = { vm.bizStock(baskets) },
                    enabled = s.coins >= cost,
                    primary = false
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Развитие: оборудование, помещение, реклама.
        MontikCard {
            Text("🚀 Развитие магазина", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val next = BizEquip.byLevel(b.equip + 1)
            if (next != null) {
                val price = next.price - (equip?.price ?: 0)
                Text(
                    "${next.emoji} ${next.title}: принимает до ${next.capacity} покупателей, привлекает на ${next.attract - 100}% больше, содержание ${next.upkeep} в день.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                BigButton("Улучшить оборудование: −${spaced(price)}", onClick = { vm.bizEquip(next.level) }, enabled = s.coins >= price)
            } else {
                Text("✨ Оборудование уже самое лучшее.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            if (b.expansion < BusinessEngine.MAX_EXPANSION) {
                val price = BusinessEngine.EXPANSION_PRICES[b.expansion]
                Text(
                    "📐 Расширить помещение: ещё ${BusinessEngine.EXPANSION_CAPACITY} покупателей в день, но аренда выше на ${BusinessEngine.EXPANSION_RENT} в день.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                BigButton("Расширить магазин: −${spaced(price)}", onClick = { vm.bizExpand() }, enabled = s.coins >= price)
            } else {
                Text("📐 Помещение расширено до предела.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "📣 Реклама на ${BusinessEngine.AD_DAYS} дня: покупателей больше на ${BusinessEngine.AD_BOOST_PERCENT}%. " +
                    "Помогает, только если в магазине хватает места и товара.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            BigButton(
                if (b.adDays > 0) "Реклама уже идёт" else "Запустить рекламу: −${BusinessEngine.AD_PRICE}",
                onClick = { vm.bizAdvertise() },
                enabled = b.adDays == 0 && s.coins >= BusinessEngine.AD_PRICE,
                primary = false
            )
        }

        // Цифры за всё время.
        MontikCard {
            Text("📊 Дела магазина", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            StatLine("Работает дней", "${b.daysOpen}")
            StatLine("Выручка за всё время", spaced(b.totalRevenue))
            StatLine("Прибыль за всё время", spaced(b.totalProfit), if (b.totalProfit >= 0) MontikColors.Good else MontikColors.Bad)
            StatLine("Ожидаем прибыли в день", spaced(BusinessEngine.expectedProfit(b)))
            StatLine("Расходы в день (аренда и содержание)", spaced(BusinessEngine.dailyRent(b) + BusinessEngine.dailyUpkeep(b)))
            StatLine("В кошельке", spaced(s.coins))
        }
    }
}

/** Подсказки владельцу: что сейчас мешает магазину зарабатывать. */
private fun advice(b: Business): List<String> {
    val tips = mutableListOf<String>()
    val report = b.report
    if (b.stock <= 0) {
        tips += "Товар закончился! Пока склад пуст, магазин ничего не зарабатывает, а аренда идёт. Закупи товары."
    } else if (BusinessEngine.stockDays(b) < 1.2f) {
        tips += "Товара хватит меньше чем на день. Закупи ещё, чтобы не упустить покупателей."
    }
    if (report != null && report.lost > 0 && BusinessEngine.capacity(b) < BusinessEngine.demand(b)) {
        tips += "Покупатели уходят: в магазине не хватает места. Улучши оборудование или расширь помещение."
    }
    if (report != null && report.profit < 0) {
        tips += "Вчера расходы оказались больше выручки. Проверь: хватает ли товара и не слишком ли дорого содержание."
    }
    if (b.overdue > 0) {
        tips += "Есть долг по аренде. Забери выручку из кассы: сначала спишется долг."
    }
    if (b.till >= 1500) {
        tips += "В кассе уже много денег. Заберёшь их — сможешь вложить в товары или развитие."
    }
    return tips
}
