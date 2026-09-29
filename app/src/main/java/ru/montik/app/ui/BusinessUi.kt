package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    BackHandler(enabled = true) { vm.bizPostpone() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        // Та же карточка, что у сообщений в игре (светло-зелёная, скругление 28), и твой Монтик.
        DesignCanvas {
            Box(Modifier.at(21f, 150f, 370f, 590f).clip(RoundedCornerShape(d(28f))).background(TipGreen))
            SoftGlow(cx = 206f, cy = 402f, w = 190f, h = 38f, color = Color.Black, peak = 0.18f)
            SceneMontik(vm, 106f, 190f, 200f)
            DText("💡 Новая цель", 41f, 420f, 330f, 24f, color = NameInk, align = TextAlign.Center, mono = false)
            Box(
                Modifier.at(41f, 462f, 330f, 160f).clip(RoundedCornerShape(d(18f))).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "«Я уже накопил достаточно денег! А что, если открыть собственный магазин? " +
                        "Тогда я смогу сам управлять своим делом и зарабатывать ещё больше!»",
                    color = NameInk,
                    fontSize = fs(16f, false),
                    lineHeight = fs(21f, false),
                    fontFamily = MontikFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = d(16f))
                )
            }
            LivePill("Открыть бизнес", 41f, 650f, 200f) { vm.bizBegin() }
            LivePill("Пока рано", 251f, 650f, 120f, fill = Color(0xFFB9C9B0)) { vm.bizPostpone() }
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

private val BizPill = Color(0xFF8AD742)
private val BizLine = Color(0xFFCFE3CF)
private val BizSoft = Color(0xFF55726B)
private val BizDotIdle = Color(0xFFE3ECE0)

/** Шапка сцен бизнеса: «назад», монеты и (на пути к открытию) полоска из пяти этапов. */
@Composable
private fun DesignScope.BizHeader(vm: GameViewModel, stage: BizStage?) {
    Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
    Box(Modifier.at(212f, 10f, 190f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(vm.state.coins) }
    if (stage != null) BizSteps(stage)
}

/** Белая пилюля с кружками этапов: пройденные — с галочкой, текущий — зелёный. */
@Composable
private fun DesignScope.BizSteps(stage: BizStage) {
    val steps = BizStage.values().filter { it != BizStage.OPEN }
    val x = 17f
    val y = 72f
    Box(Modifier.at(x, y, 378f, 52f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.96f)))
    steps.forEachIndexed { i, step ->
        val done = step.ordinal < stage.ordinal
        val current = step == stage
        Box(
            Modifier.at(x + 14f + i * 33f, y + 12f, 28f, 28f).clip(CircleShape)
                .background(if (done) LevelGreen else if (current) BizPill else BizDotIdle),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "${i + 1}",
                color = if (done || current) Color.White else BizSoft,
                fontSize = fs(14f, false),
                fontWeight = FontWeight.Bold,
                fontFamily = MontikFont
            )
        }
    }
    DText("Этап ${stage.ordinal + 1} из ${steps.size}", x + 188f, y + 7f, 180f, 12f, bold = false, color = BizSoft, mono = false, softWrap = false)
    DText(stage.title, x + 188f, y + 23f, 180f, 17f, color = NameInk, mono = false, softWrap = false)
}

/** Твой Монтик в сцене слева вверху и его реплика в белом облачке, как в кадрах бизнеса. */
@Composable
private fun DesignScope.BizHost(vm: GameViewModel, say: String) {
    SoftGlow(cx = 96f, cy = 322f, w = 170f, h = 38f, color = Color.Black, peak = 0.25f)
    SceneMontik(vm, 4f, 140f, 185f)
    BizBubble(say, 172f, 158f, 228f, 100f)
}

/** Фон сцены: картинка из макета на весь экран. */
@Composable
private fun BizBackground(art: String) {
    ArtImage(art, Modifier.fillMaxSize(), ContentScale.Crop) { StreetBackground() }
}

/** Белая панель снизу, прокручивается; внутри — карточки выбора. */
@Composable
private fun DesignScope.BizPanel(y: Float = 345f, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .at(12f, y, 388f, 905f - y)
            .clip(RoundedCornerShape(d(30f)))
            .background(Color.White.copy(alpha = 0.96f))
            .verticalScroll(rememberScrollState())
            .padding(d(14f)),
        verticalArrangement = Arrangement.spacedBy(d(12f))
    ) { content() }
}

@Composable
private fun DesignScope.PanelText(
    text: String,
    size: Float = 15f,
    bold: Boolean = false,
    color: Color = NameInk,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text,
        color = color,
        fontSize = fs(size, false),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = MontikFont,
        lineHeight = fs(size * 1.25f, false),
        textAlign = align,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Зелёная кнопка-пилюля на всю ширину карточки (как «Готово» в макете). */
@Composable
private fun DesignScope.PanelPill(
    text: String,
    enabled: Boolean = true,
    fill: Color = BizPill,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(d(54f))
            .clip(RoundedCornerShape(50))
            .background(if (enabled) fill else fill.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = fs(17f, false),
            fontWeight = FontWeight.Bold,
            fontFamily = MontikFont,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = fs(19f, false),
            modifier = Modifier.padding(horizontal = d(16f))
        )
    }
}

/** Карточка в панели: белая, скругление 22, тонкая зелёная рамка. */
@Composable
private fun DesignScope.PanelCard(tint: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(d(22f))
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tint).border(d(1.5f), BizLine, shape).padding(d(14f)),
        verticalArrangement = Arrangement.spacedBy(d(4f))
    ) { content() }
}

/** Заголовок карточки со значком в светло-зелёном квадратике. */
@Composable
private fun DesignScope.PanelTitle(emoji: String, title: String, icon: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(d(48f)).clip(RoundedCornerShape(d(14f))).background(TipGreen),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                ArtImage(icon, Modifier.fillMaxSize().padding(d(4f)), ContentScale.Fit) { Text(emoji, fontSize = fs(26f, false)) }
            } else {
                Text(emoji, fontSize = fs(26f, false))
            }
        }
        Spacer(Modifier.width(d(12f)))
        Text(
            title,
            color = NameInk,
            fontSize = fs(19f, false),
            fontWeight = FontWeight.Bold,
            fontFamily = MontikFont,
            lineHeight = fs(22f, false),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Вариант выбора: значок, название, цифры и кнопка. */
@Composable
private fun DesignScope.BizOption(
    emoji: String,
    title: String,
    lines: List<String>,
    buttonText: String,
    enabled: Boolean,
    warning: String? = null,
    icon: String? = null,
    onClick: () -> Unit
) {
    PanelCard {
        PanelTitle(emoji, title, icon)
        Spacer(Modifier.height(d(4f)))
        for (line in lines) PanelText(line, 14f, color = BizSoft)
        if (warning != null) PanelText(warning, 14f, bold = true, color = MontikColors.Bad)
        Spacer(Modifier.height(d(6f)))
        PanelPill(buttonText, enabled, onClick = onClick)
    }
}

/** Строка «название — число». */
@Composable
private fun DesignScope.PanelStat(label: String, value: String, valueColor: Color = NameInk) {
    Row(Modifier.fillMaxWidth().padding(vertical = d(1f)), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = BizSoft, fontSize = fs(14f, false), fontFamily = MontikFont, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(d(8f)))
        Text(value, color = valueColor, fontSize = fs(16f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
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
        0 -> PlaceIntro(vm) { phase = 1 }
        1 -> TeleportScreen()
        else -> PlaceChooser(vm, chosen) { chosen = it }
    }
}

/** Начало пути: улица из макета, твой Монтик и мысль о своём магазине. */
@Composable
private fun PlaceIntro(vm: GameViewModel, onSearch: () -> Unit) {
    DesignCanvas(background = { BizBackground("fg_biz_street") }) {
        Art("fg_biz_street", -54f, -175f, 768f, 1152f)
        if (!rememberHasArt("fg_biz_street")) StreetFallback()
        BizHeader(vm, BizStage.PLACE)
        SoftGlow(cx = 206f, cy = 590f, w = 230f, h = 50f, color = Color.Black, peak = 0.25f)
        SceneMontik(vm, 81f, 340f, 250f)
        BizBubble("Давай найдём место для моего магазина!", 66f, 236f, 280f, 79f)
        BizPanel(y = 620f) {
            PanelText(
                "Магазин нужно поставить там, где ходит много людей. " +
                    "Но чем оживлённее улица, тем дороже аренда — придётся выбирать.",
                15f, align = TextAlign.Center
            )
            PanelPill("Искать место", onClick = onSearch)
        }
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

        // Монтик задумался (кадр «Расположение своего магазина»): фигура из макета в цвете героя.
        // Твой Монтик на улице (вместо белого человечка из макета) и тень под ним.
        SoftGlow(cx = 283f, cy = 808f, w = 218f, h = 60f, color = Color.Black, peak = 0.22f)
        SceneMontik(vm, 180f, 590f, 220f)
        if (chosen != null) {
            BizBubble("Я думаю это лучшее место для моего магазина!", 43f, 408f, 279f, 79f)
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
    DesignCanvas(background = { BizBackground("fg_biz_street") }) {
        Art("fg_biz_street", -54f, -175f, 768f, 1152f)
        if (!rememberHasArt("fg_biz_street")) StreetFallback()
        BizHeader(vm, BizStage.TYPE)
        BizHost(vm, "Чем будет торговать мой магазин?")
        BizPanel {
            PanelText(
                "Место: ${b.placeInfo?.title ?: ""}. У каждого товара свой средний чек, " +
                    "своя закупочная цена и своё число покупателей.",
                14f, color = BizSoft, align = TextAlign.Center
            )
            for (type in BizType.values()) {
                BizOption(
                    emoji = type.emoji,
                    title = type.title,
                    lines = listOf(
                        type.about,
                        "Средний чек: ${type.basket} монет, товары обходятся в ${type.unitCost}.",
                        "Прибыль с одного покупателя: ${type.margin} монет.",
                        "Покупатели заходят: ${footfallWord(type)}."
                    ),
                    buttonText = "Выбрать",
                    enabled = true
                ) { vm.bizChooseType(type.id) }
            }
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

        // Монтик-строитель в каске и с молотком (кадр «Строительство своего магазина») в цвете героя.
        // Твой Монтик-строитель: каска из макета надета поверх выбранного скина.
        SceneMontik(vm, 150f, 575f, 250f)
        if (rememberHasArt("fg_biz_helmet")) {
            Art("fg_biz_helmet", 196f, 560f, 158f, 98f, ContentScale.Fit)
        } else {
            Text("⛑", fontSize = fs(46f, false), modifier = Modifier.at(250f, 545f))
        }

        BizBubble(
            if (building) "Идёт стройка… Стучат молотки!" else "Давай начнем строительство!",
            51f, 454f, 279f, 68f
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
    DesignCanvas(background = { BizBackground("fg_biz_build_bg") }) {
        Art("fg_biz_build_bg", 0f, 0f, 418f, 931f)
        if (!rememberHasArt("fg_biz_build_bg")) BuildFallback()
        BizHeader(vm, BizStage.EQUIP)
        BizHost(vm, "Стройка готова! Какое оборудование поставим?")
        BizPanel {
            PanelCard(tint = TipGreen) {
                PanelText("Ты решаешь, на что потратить деньги", 16f, bold = true)
                PanelText(
                    "Хорошее оборудование привлекает больше покупателей, но стоит дороже. " +
                        "Останется меньше денег на товары, а без товаров магазин не заработает. " +
                        "На самую маленькую закупку нужно оставить $minStock монет.",
                    14f, color = BizSoft
                )
                PanelText("В кошельке: ${spaced(s.coins)} монет", 15f, bold = true)
            }
            for (equip in BizEquip.values()) {
                val left = s.coins - equip.price
                BizOption(
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
}

// ───────────────────────── Этап 5. Первые товары ─────────────────────────

@Composable
private fun StockStage(vm: GameViewModel, b: Business) {
    val s = vm.state
    val type = b.typeInfo ?: return
    val served = BusinessEngine.expectedServed(b).coerceAtLeast(1)
    DesignCanvas(background = { BizBackground("fg_shelf_bg") }) {
        Art("fg_shelf_bg", 0f, 0f, DESIGN_W, DESIGN_H)
        BizHeader(vm, BizStage.STOCK)
        BizHost(vm, "Полки пустые. Сколько товаров закупить?")
        BizPanel {
            PanelCard(tint = TipGreen) {
                PanelText(
                    "В обычный день сюда придёт около ${BusinessEngine.demand(b)} покупателей, а магазин успеет обслужить $served. " +
                        "Если товара не хватит, покупатели уйдут ни с чем. Если купить слишком много, деньги лежат на складе без дела.",
                    14f, color = BizSoft
                )
                PanelText("В кошельке: ${spaced(s.coins)} монет", 15f, bold = true)
            }
            for (baskets in BusinessEngine.FIRST_STOCK) {
                val cost = BusinessEngine.stockCost(type, baskets)
                val days = baskets.toFloat() / served
                BizOption(
                    emoji = "📦",
                    title = "Товары на $baskets покупателей",
                    lines = listOf(
                        "Хватит примерно на ${oneDecimal(days)} дн. торговли.",
                        "Цена закупки: ${spaced(cost)} монет.",
                        "Останется в кошельке: ${spaced((s.coins - cost).coerceAtLeast(0))} монет."
                    ),
                    buttonText = "Закупить и открыть: −${spaced(cost)}",
                    enabled = s.coins >= cost,
                    warning = if (s.coins < cost) "Не хватает ${cost - s.coins} монет." else null
                ) { vm.bizStock(baskets) }
            }
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
    val tips = advice(b)
    DesignCanvas(background = { BizBackground("fg_shop_hub_bg") }) {
        Art("fg_shop_hub_bg", 0f, 0f, DESIGN_W, DESIGN_H)
        BizHeader(vm, null)
        // Табличка магазина вместо полоски этапов.
        Box(
            Modifier.at(17f, 72f, 378f, 52f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.96f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${type.emoji} Мой магазин «${type.title}»",
                color = NameInk,
                fontSize = fs(19f, false),
                fontWeight = FontWeight.Bold,
                fontFamily = MontikFont,
                maxLines = 1
            )
        }
        BizHost(
            vm,
            when {
                b.broken -> "Ой! Касса сломалась, надо починить."
                b.stock <= 0 -> "Товар закончился! Нужно закупить ещё."
                b.till > 0 -> "В кассе есть выручка. Забери её!"
                else -> "Мой магазин работает!"
            }
        )
        BizPanel {
            // Место, оборудование, реклама и поломка.
            PanelCard(tint = TipGreen) {
                PanelText(
                    "${b.placeInfo?.title ?: ""} · ${equip?.title ?: ""}" +
                        if (b.expansion > 0) " · расширен ×${b.expansion}" else "",
                    14f, color = BizSoft, align = TextAlign.Center
                )
                if (b.adDays > 0) PanelText("📣 Реклама работает: ещё ${b.adDays} дн.", 14f, align = TextAlign.Center)
                if (b.broken) {
                    PanelText("🔧 Касса сломалась: магазин работает вполсилы.", 15f, bold = true, color = MontikColors.Bad, align = TextAlign.Center)
                    Spacer(Modifier.height(d(4f)))
                    PanelPill("Починить кассу: −${BusinessEngine.REPAIR_PRICE}", enabled = s.coins >= BusinessEngine.REPAIR_PRICE) { vm.bizRepair() }
                }
            }

            // Совет владельцу — как «Совет от Лобачевского» в макете.
            if (tips.isNotEmpty()) {
                PanelCard {
                    PanelTitle("💡", "Совет", icon = "fg_tip_lamp")
                    for (tip in tips) PanelText(tip, 14f)
                }
            }

            // Касса.
            PanelCard {
                val debtPaid = minOf(b.till, b.overdue)
                val taxable = b.till - debtPaid
                val tax = BusinessEngine.tax(taxable)
                PanelTitle("💰", "Касса: ${spaced(b.till)} монет", icon = "fg_ui_c_coins")
                PanelText("Деньги в кассе — деньги магазина. Твоими они станут, когда ты их заберёшь.", 14f, color = BizSoft)
                if (b.overdue > 0) {
                    PanelText("Долг по аренде: ${spaced(b.overdue)}. Он спишется из выручки первым.", 14f, color = MontikColors.Bad)
                }
                if (b.till > 0) {
                    PanelText("Налог ${BusinessEngine.TAX_PERCENT}%: $tax. Тебе достанется: ${spaced(taxable - tax)}.", 14f)
                }
                Spacer(Modifier.height(d(6f)))
                PanelPill("Забрать выручку", enabled = b.till > 0) { vm.bizCollect() }
            }

            // Итоги последнего дня.
            PanelCard {
                PanelTitle("🧾", "Итоги последнего дня", icon = "fg_ui_c_doc")
                if (report == null) {
                    PanelText("Первый торговый день ещё впереди. Ляг спать, а утром здесь появится отчёт.", 14f, color = BizSoft)
                } else {
                    PanelText("День ${report.day}", 14f, color = BizSoft)
                    report.event?.let { PanelText("⚡ $it", 14f) }
                    PanelStat("Хотели зайти", "${report.demand}")
                    PanelStat("Обслужено", "${report.served}")
                    if (report.lost > 0) PanelStat("Ушли без покупки", "${report.lost}", MontikColors.Bad)
                    PanelStat("Выручка", "+${spaced(report.revenue)}")
                    PanelStat("Товары", "−${spaced(report.goodsCost)}")
                    PanelStat("Аренда", "−${spaced(report.rent)}")
                    PanelStat("Содержание", "−${spaced(report.upkeep)}")
                    PanelStat(
                        "Прибыль",
                        (if (report.profit >= 0) "+" else "") + spaced(report.profit),
                        if (report.profit >= 0) MontikColors.Good else MontikColors.Bad
                    )
                }
            }

            // Склад и закупка.
            PanelCard {
                val days = BusinessEngine.stockDays(b)
                PanelTitle("📦", "Склад: товаров на ${b.stock} покупателей")
                PanelText("Хватит примерно на ${oneDecimal(days)} дн. Каждый покупатель уносит часть товара.", 14f, color = BizSoft)
                for (baskets in BusinessEngine.RESTOCK_OPTIONS) {
                    val cost = BusinessEngine.stockCost(type, baskets)
                    Spacer(Modifier.height(d(4f)))
                    PanelPill(
                        "Закупить на $baskets покупателей: −${spaced(cost)}",
                        enabled = s.coins >= cost,
                        fill = TipGreen,
                        textColor = NameInk
                    ) { vm.bizStock(baskets) }
                }
            }

            // Развитие: оборудование, помещение, реклама.
            PanelCard {
                PanelTitle("🚀", "Развитие магазина", icon = "fg_ui_j_tools")
                val next = BizEquip.byLevel(b.equip + 1)
                if (next != null) {
                    val price = next.price - (equip?.price ?: 0)
                    PanelText(
                        "${next.emoji} ${next.title}: принимает до ${next.capacity} покупателей, привлекает на ${next.attract - 100}% больше, содержание ${next.upkeep} в день.",
                        14f
                    )
                    PanelPill("Улучшить оборудование: −${spaced(price)}", enabled = s.coins >= price) { vm.bizEquip(next.level) }
                } else {
                    PanelText("✨ Оборудование уже самое лучшее.", 14f)
                }
                Spacer(Modifier.height(d(8f)))
                if (b.expansion < BusinessEngine.MAX_EXPANSION) {
                    val price = BusinessEngine.EXPANSION_PRICES[b.expansion]
                    PanelText(
                        "📐 Расширить помещение: ещё ${BusinessEngine.EXPANSION_CAPACITY} покупателей в день, но аренда выше на ${BusinessEngine.EXPANSION_RENT} в день.",
                        14f
                    )
                    PanelPill("Расширить магазин: −${spaced(price)}", enabled = s.coins >= price) { vm.bizExpand() }
                } else {
                    PanelText("📐 Помещение расширено до предела.", 14f)
                }
                Spacer(Modifier.height(d(8f)))
                PanelText(
                    "📣 Реклама на ${BusinessEngine.AD_DAYS} дня: покупателей больше на ${BusinessEngine.AD_BOOST_PERCENT}%. " +
                        "Помогает, только если в магазине хватает места и товара.",
                    14f
                )
                PanelPill(
                    if (b.adDays > 0) "Реклама уже идёт" else "Запустить рекламу: −${BusinessEngine.AD_PRICE}",
                    enabled = b.adDays == 0 && s.coins >= BusinessEngine.AD_PRICE,
                    fill = TipGreen,
                    textColor = NameInk
                ) { vm.bizAdvertise() }
            }

            // Цифры за всё время.
            PanelCard {
                PanelTitle("📊", "Дела магазина", icon = "fg_ui_b_chart")
                PanelStat("Работает дней", "${b.daysOpen}")
                PanelStat("Выручка за всё время", spaced(b.totalRevenue))
                PanelStat("Прибыль за всё время", spaced(b.totalProfit), if (b.totalProfit >= 0) MontikColors.Good else MontikColors.Bad)
                PanelStat("Ожидаем прибыли в день", spaced(BusinessEngine.expectedProfit(b)))
                PanelStat("Расходы в день", spaced(BusinessEngine.dailyRent(b) + BusinessEngine.dailyUpkeep(b)))
                PanelStat("В кошельке", spaced(s.coins))
            }
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

/** Белое облачко реплики из кадров бизнеса: скругление 44,5, Inter Bold 16, чёрный текст. */
@Composable
private fun DesignScope.BizBubble(text: String, x: Float, y: Float, w: Float, h: Float) {
    Box(
        Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(44.5f))).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.Black,
            fontSize = fs(16f, false),
            fontWeight = FontWeight.Bold,
            lineHeight = fs(19f, false),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = d(24f))
        )
    }
}
