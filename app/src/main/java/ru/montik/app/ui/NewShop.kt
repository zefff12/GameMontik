package ru.montik.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Grocery
import ru.montik.app.game.GrocerySection

/*
 * Магазин продуктов по новому макету: каждая страница — кадр целиком (412×917) с ценами на карточках.
 * Число монет в шапке стёрто с картинки и пишется игрой. Нажимаются нарисованные «+», плитки разделов,
 * баннеры и нижняя панель: «Главная», «Корзина», «Монетки» (сколько всего потрачено на продукты),
 * «Профиль» (что куплено за сегодняшний игровой день).
 */

private val NsInk = Color(0xFF14246B)
private val NsGreen = Color(0xFF1E9A3B)

/** Разметка кадра: «+» на карточках (левые верхние углы и размер), ряд плиток, низ панели, баннер. */
private class ShopFrame(
    val art: String,
    val plusX: FloatArray,
    val plusY: FloatArray,
    val plusSize: Float,
    val tilesY: Float,
    val tilesH: Float,
    val navY: Float,
    val bannerY: Float?,
    val headerY: Float
)

private val SHOP_FRAMES = mapOf(
    GrocerySection.HOME to ShopFrame(
        "fg3_shopc_home", floatArrayOf(74f, 172.4f, 269.4f, 366.5f), floatArrayOf(596.2f, 754.4f), 25.5f,
        273f, 66f, 799f, null, 53.4f
    ),
    GrocerySection.VEG to ShopFrame(
        "fg3_shopc_veg", floatArrayOf(108.8f, 230.7f, 356.6f), floatArrayOf(346.8f, 462.9f, 581f, 693.2f), 27.5f,
        200f, 57f, 790f, 732f, 60.7f
    ),
    GrocerySection.FRUITS to ShopFrame(
        "fg3_shopc_fruits", floatArrayOf(162.3f, 352.5f), floatArrayOf(344.8f, 460.6f, 572.9f, 683.2f), 31f,
        196f, 60f, 790f, 732f, 61.3f
    ),
    GrocerySection.FOOD to ShopFrame(
        "fg3_shopc_milk", floatArrayOf(104.5f, 234f, 361.8f), floatArrayOf(363f, 494.3f, 614.3f, 741.2f), 28.5f,
        200f, 57f, 790f, null, 61.2f
    ),
    GrocerySection.DRINKS to ShopFrame(
        "fg3_shopc_drinks", floatArrayOf(75.9f, 173.4f, 270.9f, 368.9f), floatArrayOf(396.8f, 543.8f, 691.2f), 23f,
        214f, 59f, 792f, 730f, 59.3f
    ),
    GrocerySection.READY to ShopFrame(
        "fg3_shopc_ready", floatArrayOf(106.3f, 234.7f, 363.5f), floatArrayOf(393.4f, 544.8f, 698.1f), 26.5f,
        202f, 59f, 806f, 740f, 60.7f
    )
)

/** Плитки разделов слева направо: «Все», «Овощи», «Фрукты», «Молочное», «Напитки», «Готовые продукты». */
private val TILE_SECTIONS = listOf(
    GrocerySection.HOME, GrocerySection.VEG, GrocerySection.FRUITS,
    GrocerySection.FOOD, GrocerySection.DRINKS, GrocerySection.READY
)

@Composable
fun hasNewShop(): Boolean = rememberHasArt("fg3_shopc_home", "fg3_shopc_veg", "fg3_shopc_ready")

/** Вкладка нижней панели: 0 — ничего, 2 — «Монетки», 3 — «Профиль». */
@Composable
fun NewShopPage(
    vm: GameViewModel,
    section: GrocerySection,
    tab: Int,
    onTab: (Int) -> Unit,
    go: (GrocerySection) -> Unit,
    onCart: () -> Unit
) {
    val f = SHOP_FRAMES.getValue(section)
    val s = vm.state
    val fb = rememberFeedback()
    val coins = rememberCountUp(s.coins)
    DesignCanvas {
        FrameArt(f.art)
        // Монеты и «+» — заработать.
        Box(Modifier.at(317f, f.headerY + 7f, 40f, 18f), contentAlignment = Alignment.CenterStart) {
            Text("$coins", color = NsInk, fontSize = fs(if (coins >= 1000) 13f else 17f, false), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        PressHit(352f, f.headerY - 4f, 40f, 40f, 20f) { vm.goTo(Screen.Work) }
        // Стрелка «назад»: из раздела — на «Главную» магазина, с «Главной» — в комнату.
        PressHit(16f, f.headerY - 6f, 52f, 52f, 26f) { if (section == GrocerySection.HOME) vm.back() else go(GrocerySection.HOME) }

        // На «Главной» у овсянки и молока цены на картинке другие — пишем настоящие.
        if (section == GrocerySection.HOME) {
            for ((id, x) in listOf("oats" to 44f, "milk" to 335f)) {
                val price = Grocery.item(id)?.price ?: continue
                Box(Modifier.at(x, 760f, 28f, 21f), contentAlignment = Alignment.CenterStart) {
                    Text("$price", color = NsInk, fontSize = fs(12.5f, false), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        // Плитки разделов.
        val tileW = 376f / 6f
        TILE_SECTIONS.forEachIndexed { i, target ->
            Hit(18f + i * tileW, f.tilesY, tileW, f.tilesH) { if (target != section) go(target) }
        }
        if (section == GrocerySection.HOME) {
            Hit(24f, 426f, 134f, 38f) { go(GrocerySection.READY) }          // «Выбрать →»
            Hit(298f, 480f, 100f, 24f) { go(GrocerySection.FRUITS) }        // «Смотреть все» (Популярное)
            Hit(298f, 638f, 100f, 24f) { go(GrocerySection.FOOD) }          // «Смотреть все» (Завтрак)
        }
        f.bannerY?.let { y -> Hit(20f, y, 372f, 50f) { go(nextShopSection(section)) } }

        // «+» на карточках и число в корзине.
        val items = Grocery.section(section)
        var i = 0
        for (py in f.plusY) for (px in f.plusX) {
            val item = items.getOrNull(i++) ?: continue
            val sz = f.plusSize
            PressHit(px - 6f, py - 6f, sz + 12f, sz + 12f, (sz + 12f) / 2f) { fb.tap(); vm.cartAdd(item.id) }
            val inCart = vm.cart[item.id] ?: 0
            if (inCart > 0) {
                Box(
                    Modifier.at(px + sz - 9f, py - 8f, 17f, 17f).clip(CircleShape).background(Color(0xFFF2A516))
                        .border(d(1.5f), Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("$inCart", color = Color.White, fontSize = fs(10f, false), fontWeight = FontWeight.Bold) }
            }
        }

        // Нижняя панель: 4 вкладки.
        val navCy = f.navY + 22f
        val centers = floatArrayOf(64f, 158f, 253f, 348f)
        centers.forEachIndexed { t, cx ->
            Hit(cx - 46f, navCy - 26f, 92f, 52f) {
                when (t) {
                    0 -> { onTab(0); go(GrocerySection.HOME) }
                    1 -> { onTab(0); onCart() }
                    else -> onTab(if (tab == t) 0 else t)
                }
            }
        }
        val n = Grocery.count(vm.cart)
        if (n > 0) {
            Box(
                Modifier.at(166f, navCy - 24f, 18f, 18f).clip(CircleShape).background(NsGreen),
                contentAlignment = Alignment.Center
            ) { Text("$n", color = Color.White, fontSize = fs(10f, false), fontWeight = FontWeight.Bold) }
        }

        // «Монетки»: плашка над нижней панелью — сколько всего потрачено на продукты.
        AnimatedVisibility(
            visible = tab == 2,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.at(26f, f.navY - 66f, 360f, 58f)
        ) {
            Row(
                Modifier.fillMaxSize().shadow(d(6f), RoundedCornerShape(d(22f))).clip(RoundedCornerShape(d(22f)))
                    .background(Color.White).border(d(2f), Color(0xFFBFE3C2), RoundedCornerShape(d(22f)))
                    .padding(horizontal = d(14f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage("fg2_coin", Modifier.size(d(34f)), ContentScale.Fit) {}
                Spacer(Modifier.width(d(10f)))
                Column(Modifier.weight(1f)) {
                    Text("Потрачено на продукты", color = Color(0xFF5E6F86), fontSize = fs(12.5f, false), maxLines = 1)
                    Text("${s.groceryTotal} ${coinWord(s.groceryTotal)}", color = NsInk, fontSize = fs(19f, false), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
    if (tab == 3) TodayPurchases(vm) { onTab(0) }
}

private fun nextShopSection(s: GrocerySection): GrocerySection = when (s) {
    GrocerySection.VEG -> GrocerySection.FRUITS
    GrocerySection.FRUITS -> GrocerySection.FOOD
    GrocerySection.FOOD -> GrocerySection.DRINKS
    GrocerySection.DRINKS -> GrocerySection.READY
    GrocerySection.READY, GrocerySection.HOME -> GrocerySection.VEG
}

/** «Профиль»: история покупок за сегодняшний игровой день. */
@Composable
private fun TodayPurchases(vm: GameViewModel, onClose: () -> Unit) {
    val s = vm.state
    val list = Grocery.boughtToday(s)
    val total = list.sumOf { (item, n) -> item.price * n }
    androidx.activity.compose.BackHandler { onClose() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 22.dp).widthIn(max = 380.dp).fillMaxWidth()
                .clip(RoundedCornerShape(26.dp)).background(Color(0xFFF4FBF3))
                .border(2.dp, Color(0xFFBFE3C2), RoundedCornerShape(26.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(18.dp)
        ) {
            Text("Покупки за день ${s.day}", color = NsInk, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
            Spacer(Modifier.padding(top = 8.dp))
            if (list.isEmpty()) {
                Text("Сегодня в магазине ещё ничего не куплено.", color = Color(0xFF5E6F86), fontSize = 15.sp, fontFamily = MontikFont)
            } else {
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((item, n) in list) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtImage(groceryArt(item.id), Modifier.size(40.dp), ContentScale.Fit) { Text(item.emoji, fontSize = 26.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = NsInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont, maxLines = 1)
                                Text("${item.amount} · $n шт.", color = Color(0xFF7C8AA0), fontSize = 13.sp, fontFamily = MontikFont)
                            }
                            Text("${item.price * n}", color = NsInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                            Spacer(Modifier.width(4.dp))
                            ArtImage("fg2_coin", Modifier.size(18.dp), ContentScale.Fit) {}
                        }
                    }
                }
                Spacer(Modifier.padding(top = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Итого за день: $total", color = NsInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                    Spacer(Modifier.width(4.dp))
                    ArtImage("fg2_coin", Modifier.size(20.dp), ContentScale.Fit) {}
                }
            }
            Spacer(Modifier.padding(top = 12.dp))
            GreenButton("Понятно", height = 48.dp) { onClose() }
        }
    }
}
