package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import ru.montik.app.GameViewModel
import ru.montik.app.game.Catalog
import ru.montik.app.game.Market

/*
 * «Маркет» по новому макету: заставка «Маркет / Войти» (Android Compact - 88), вкладки
 * «Радости» (89), «Одежда» (82) и «Гардероб» (87). Кадр — фон, всё живое поверх: баланс,
 * покупка, надеть / снять. Купленная вещь сразу появляется на Монтике.
 */

private val MarketInk = Color(0xFF0B2A5B)
private val MarketGreen = Color(0xFF55B93E)

@Composable
fun MarketScreen(vm: GameViewModel) {
    var entered by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = entered) { entered = false }
    AnimatedContent(targetState = entered, transitionSpec = { sceneFade() }, label = "market") { inside ->
        if (!inside) MarketEntry(vm) { entered = true }
        else AnimatedContent(targetState = tab, transitionSpec = { stepTransform() }, label = "marketTab") { t ->
            when (t) {
                0 -> MarketJoys(vm) { tab = it }
                1 -> MarketClothes(vm) { tab = it }
                else -> MarketWardrobe(vm) { tab = it }
            }
        }
    }
}

/** Заставка маркета: Монтик с покупками и розовая кнопка «Войти», которая мягко «дышит». */
@Composable
private fun MarketEntry(vm: GameViewModel, onEnter: () -> Unit) {
    val breath = androidx.compose.animation.core.rememberInfiniteTransition(label = "enter")
    val k by breath.animateFloat(
        1f, 1.04f,
        androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "breath"
    )
    DesignCanvas(background = { ArtImage("fg2_market", Modifier.fillMaxSize(), ContentScale.Crop) {} }) {
        Box(Modifier.at(0f, -31f, 426f, 948f)) { ArtImage("fg2_market", Modifier.fillMaxSize(), ContentScale.FillBounds) {} }
        RoundBack(16f, 40f) { vm.back() }
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val press by animateFloatAsState(if (pressed) 0.8f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "enterBtn")
        Box(
            Modifier.at(41f, 620f, 343f, 61f)
                .graphicsLayer { alpha = press }
                .shadow(d(6f), RoundedCornerShape(d(35f)))
                .clip(RoundedCornerShape(d(35f)))
                .background(Color(0xFFFB4D83))
                .clickable(interactionSource = source, indication = null, onClick = onEnter),
            contentAlignment = Alignment.Center
        ) {
            Text("Войти", color = Color.White, fontSize = fs(24f), fontWeight = FontWeight.Bold)
        }
    }
}

/** Шапка вкладок: стрелка назад, баланс и нажимаемые вкладки. */
@Composable
private fun DesignScope.MarketHeader(vm: GameViewModel, coinBox: FloatArray, onTab: (Int) -> Unit) {
    PressHit(18f, 40f, 52f, 46f, 23f) { vm.back() }
    val coins = rememberCountUp(vm.state.coins)
    LiveText("$coins", coinBox[0], coinBox[1], coinBox[2], coinBox[3], Color(0xFFFCFCFC), 20f, MarketInk)
    PressHit(36f, 100f, 100f, 48f, 24f) { onTab(0) }
    PressHit(150f, 100f, 124f, 48f, 24f) { onTab(1) }
    PressHit(280f, 100f, 122f, 48f, 24f) { onTab(2) }
}

/** Радости: мороженое, цветок, комиксы, кино — покупка поднимает настроение. */
@Composable
private fun MarketJoys(vm: GameViewModel, onTab: (Int) -> Unit) {
    val fb = rememberFeedback()
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFFBFCF6))) }) {
        FrameArt("fg2_joys")
        MarketHeader(vm, floatArrayOf(324f, 45f, 56f, 23f), onTab)
        val rows = listOf("icecream" to 376f, "flower" to 480f, "comics" to 588f, "cinema" to 696f)
        rows.forEachIndexed { i, (id, y) ->
            val joy = Catalog.joy(id) ?: return@forEachIndexed
            SlideInRow(i) {
                BuyRow(16f, y, 384f, 88f) {
                    if (vm.state.coins >= joy.price) fb.coin() else fb.bad()
                    vm.buyJoy(id)
                }
            }
            val count = vm.state.joys[id] ?: 0
            if (count > 0) Badge("×$count", 228f, y + 56f, 44f, 22f, MarketGreen)
        }
    }
}

/** Одежда: шапка, очки, галстук, кепка, тапочки. Купленное — «Надеть» / «Снять». */
@Composable
private fun MarketClothes(vm: GameViewModel, onTab: (Int) -> Unit) {
    val fb = rememberFeedback()
    val s = vm.state
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFFBFCF6))) }) {
        FrameArt("fg2_clothes")
        MarketHeader(vm, floatArrayOf(333f, 50f, 52f, 24f), onTab)
        val ids = listOf("beanie", "spikes", "tie", "cap", "slippers")
        ids.forEachIndexed { i, id ->
            val item = Market.item(id) ?: return@forEachIndexed
            val top = 358f + i * 92.5f
            val owned = id in s.wardrobe
            val worn = id in s.outfit
            SlideInRow(i) {
                BuyRow(16f, top, 384f, 86f) {
                    when {
                        worn -> vm.takeOffMarket(id)
                        owned -> { fb.tap(); vm.wearMarket(id) }
                        else -> {
                            if (s.coins >= item.price) fb.coin() else fb.bad()
                            vm.buyMarket(id)
                        }
                    }
                }
            }
            if (owned) {
                // Строка «Вещь из коллекции» → состояние вещи, цена → «Надеть» / «Снять».
                LiveText(if (worn) "✓ Надето на Монтике" else "✓ В гардеробе", 137f, top + 62f, 140f, 18f, Color.White, 11.5f, MarketGreen, align = Alignment.CenterStart)
                Badge(if (worn) "Снять" else "Надеть", 272f, top + 30f, 118f, 34f, if (worn) Color(0xFFF07A4A) else MarketGreen)
            }
        }
    }
}

/** Гардероб: Монтик в надетых вещах и все купленные вещи — нажми, чтобы надеть или снять. */
@Composable
private fun MarketWardrobe(vm: GameViewModel, onTab: (Int) -> Unit) {
    val fb = rememberFeedback()
    val s = vm.state
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFFBFCF7))) }) {
        FrameArt("fg2_wardrobe")
        MarketHeader(vm, floatArrayOf(331f, 45f, 58f, 28f), onTab)
        val owned = Market.items.filter { it.id in s.wardrobe }
        if (owned.isEmpty()) {
            // Пусто: кадр макета «Здесь пока нет вещей» — как есть, без добавленных надписей.
            return@DesignCanvas
        }
        Box(Modifier.at(0f, 330f, DESIGN_W, 500f).background(Color(0xFFFBFCF7)))
        // Монтик в образе.
        Box(Modifier.at(96f, 340f, 220f, 220f).clip(CircleShape).background(Color(0xFFF1ECFD)))
        SceneMontik(vm, 116f, 352f, 180f)
        DText("Нажми на вещь — надеть или снять", 16f, 568f, 380f, 15f, color = MarketInk, align = TextAlign.Center, mono = false)
        owned.forEachIndexed { i, item ->
            val col = i % 3
            val row = i / 3
            val x = 22f + col * 126f
            val y = 600f + row * 124f
            val worn = item.id in s.outfit
            key(item.id) {
                val ring by animateFloatAsState(if (worn) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "worn")
                val enter = rememberEnter(MOTION_MS + i * 70)
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val press by animateFloatAsState(if (pressed) 0.8f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "tile")
                Box(
                    Modifier.at(x, y, 114f, 114f)
                        .enterPop(enter)
                        .graphicsLayer { alpha = press }
                        .shadow(d(3f), RoundedCornerShape(d(20f)))
                        .clip(RoundedCornerShape(d(20f)))
                        .background(Color.White)
                        .border(d(2f + 2f * ring), if (worn) Color(0xFF8B6CE8) else Color(0xFFE5E0F5), RoundedCornerShape(d(20f)))
                        .clickable(interactionSource = source, indication = null) {
                            fb.tap()
                            if (worn) vm.takeOffMarket(item.id) else vm.wearMarket(item.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ArtImage(item.art, Modifier.size(d(58f)), ContentScale.Fit) {}
                        Text(item.name, color = MarketInk, fontSize = fs(12f, false), fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (worn) "надето" else "надеть", color = if (worn) Color(0xFF8B6CE8) else Color(0xFF8A94A6), fontSize = fs(10.5f, false))
                    }
                }
            }
        }
    }
}

/** Строка-кнопка поверх строки кадра: при нажатии подсвечивается и чуть сжимается. */
@Composable
private fun DesignScope.BuyRow(x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val flash by animateFloatAsState(if (pressed) 0.28f else 0f, tween(if (pressed) 50 else 260), label = "row")
    Box(
        Modifier.at(x, y, w, h)
            .clip(RoundedCornerShape(d(20f)))
            .background(Color(0xFF9AD98A).copy(alpha = flash))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

/** Плашка (статус или кнопка-действие). */
@Composable
fun DesignScope.Badge(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fill: Color,
    textColor: Color = Color.White,
    size: Float = 13f,
    onClick: (() -> Unit)? = null
) {
    val enter = rememberEnter()
    Box(
        Modifier.at(x, y, w, h).enterPop(enter).shadow(d(2f), RoundedCornerShape(d(h / 2f))).clip(RoundedCornerShape(d(h / 2f)))
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = fs(size, false), fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Строки списка выезжают по очереди справа. */
@Composable
fun SlideInRow(index: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    Box(Modifier.graphicsLayer { translationX = (1f - p.value) * 160f; alpha = p.value }) { content() }
}

/** Круглая белая стрелка «назад» в стиле игры (для кадров без своей стрелки). */
@Composable
fun DesignScope.RoundBack(x: Float, y: Float, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, 46f, 46f).shadow(d(3f), CircleShape).clip(CircleShape).background(Color.White)
            .border(d(2f), Color(0xFFBFE3F7), CircleShape),
        contentAlignment = Alignment.Center
    ) { ChildBackArrow() }
    PressHit(x, y, 46f, 46f, 23f, onClick = onClick)
}
