package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Grocery
import ru.montik.app.game.GroceryItem
import ru.montik.app.game.GrocerySection

/*
 * Магазин продуктов — кадры макета «Главная», «Фрукты», «Овощи», «Напитки», «Готовая еда», «Еда» и «Корзина».
 * Каждый кадр — картинка экрана телефона из макета (fg_shop_<раздел>), на ней стёрты цены, число монет
 * и счётчик корзины: их игра пишет сама. Кнопки «+», стрелки, плитки разделов и нижняя панель
 * нажимаются по своим местам на картинке. Корзина собрана из тех же деталей, что в макете.
 * Правила — game/Grocery.kt.
 */

private val ShopGreen = Color(0xFF2E7D32)
private val ShopInk = Color(0xFF14261A)
private val ShopGrey = Color(0xFF8A938C)
private val QtyGreen = Color(0xFFE3F1E1)

/**
 * Разметка кадра (в пикселях картинки): где число монет, «+» у монет, счётчик корзины на нижней панели,
 * а у каждой карточки — место цены и кнопка «+» ([cards]: x0, y0, x1, y1 цены, потом x0, y0, x1, y1 «+»).
 */
private class GroceryPage(
    val art: String,
    val w: Float,
    val h: Float,
    val navH: Float,
    val bg: Color,
    val navBg: Color,
    val coin: FloatArray,
    val plus: FloatArray,
    val badge: FloatArray,
    val cards: List<FloatArray>
)

private val PAGES: Map<GrocerySection, GroceryPage> = mapOf(
    GrocerySection.HOME to GroceryPage(
        "fg_shop_home", 661f, 1241f, 106f, Color(0xFFF1FAF1), Color(0xFFEBF7EA),
        coin = floatArrayOf(428f, 22f, 500f, 69f), plus = floatArrayOf(505f, 19f, 553f, 67f),
        badge = floatArrayOf(341f, 14f, 368f, 41f),
        cards = listOf(floatArrayOf(60f, 911f, 109f, 949f, 115f, 903f, 157f, 945f), floatArrayOf(217f, 911f, 270f, 949f, 276f, 903f, 319f, 945f), floatArrayOf(379f, 911f, 429f, 949f, 435f, 903f, 478f, 945f), floatArrayOf(537f, 911f, 587f, 949f, 593f, 903f, 636f, 945f), floatArrayOf(60f, 1188f, 112f, 1226f, 118f, 1180f, 160f, 1222f), floatArrayOf(218f, 1188f, 271f, 1226f, 277f, 1180f, 319f, 1221f), floatArrayOf(378f, 1188f, 429f, 1226f, 435f, 1179f, 477f, 1221f), floatArrayOf(537f, 1188f, 587f, 1226f, 593f, 1179f, 635f, 1221f))
    ),
    GrocerySection.FRUITS to GroceryPage(
        "fg_shop_fruits", 703f, 1457f, 106f, Color(0xFFEDF5EE), Color(0xFFE3EEE3),
        coin = floatArrayOf(440f, 19f, 524f, 71f), plus = floatArrayOf(529f, 16f, 582f, 69f),
        badge = floatArrayOf(362f, 14f, 396f, 48f),
        cards = listOf(floatArrayOf(81f, 623f, 178f, 664f, 273f, 601f, 326f, 653f), floatArrayOf(416f, 623f, 513f, 664f, 609f, 601f, 662f, 653f), floatArrayOf(81f, 856f, 178f, 898f, 273f, 835f, 326f, 888f), floatArrayOf(416f, 855f, 513f, 898f, 609f, 836f, 662f, 889f), floatArrayOf(81f, 1078f, 178f, 1120f, 273f, 1059f, 326f, 1111f), floatArrayOf(416f, 1078f, 513f, 1120f, 609f, 1059f, 662f, 1111f), floatArrayOf(81f, 1308f, 178f, 1349f, 273f, 1286f, 326f, 1338f), floatArrayOf(417f, 1308f, 514f, 1350f, 609f, 1286f, 662f, 1338f))
    ),
    GrocerySection.VEG to GroceryPage(
        "fg_shop_veg", 725f, 1472f, 96f, Color(0xFFEDF6EB), Color(0xFFE4F0E0),
        coin = floatArrayOf(475f, 19f, 554f, 72f), plus = floatArrayOf(559f, 18f, 611f, 70f),
        badge = floatArrayOf(366f, 14f, 399f, 46f),
        cards = listOf(floatArrayOf(69f, 618f, 166f, 657f, 184f, 605f, 233f, 652f), floatArrayOf(302f, 618f, 399f, 657f, 415f, 605f, 463f, 652f), floatArrayOf(531f, 618f, 628f, 657f, 645f, 605f, 692f, 652f), floatArrayOf(69f, 859f, 166f, 898f, 184f, 844f, 232f, 892f), floatArrayOf(301f, 859f, 398f, 898f, 413f, 844f, 462f, 892f), floatArrayOf(530f, 859f, 627f, 898f, 643f, 844f, 692f, 892f), floatArrayOf(69f, 1100f, 166f, 1139f, 184f, 1086f, 232f, 1134f), floatArrayOf(301f, 1100f, 398f, 1139f, 412f, 1086f, 461f, 1134f), floatArrayOf(530f, 1100f, 627f, 1139f, 643f, 1086f, 691f, 1134f), floatArrayOf(69f, 1328f, 166f, 1367f, 184f, 1315f, 233f, 1363f), floatArrayOf(302f, 1328f, 399f, 1367f, 413f, 1315f, 462f, 1363f), floatArrayOf(530f, 1328f, 627f, 1368f, 643f, 1315f, 691f, 1363f))
    ),
    GrocerySection.DRINKS to GroceryPage(
        "fg_shop_drinks", 757f, 1432f, 120f, Color(0xFFEAF4EB), Color(0xFFE4F2E5),
        coin = floatArrayOf(504f, 18f, 581f, 69f), plus = floatArrayOf(586f, 15f, 638f, 68f),
        badge = floatArrayOf(389f, 14f, 423f, 49f),
        cards = listOf(floatArrayOf(64f, 659f, 134f, 698f, 140f, 653f, 181f, 695f), floatArrayOf(250f, 659f, 315f, 698f, 321f, 653f, 363f, 695f), floatArrayOf(430f, 659f, 499f, 698f, 505f, 653f, 547f, 695f), floatArrayOf(614f, 660f, 683f, 698f, 689f, 653f, 730f, 695f), floatArrayOf(62f, 955f, 134f, 994f, 140f, 949f, 181f, 990f), floatArrayOf(250f, 955f, 315f, 994f, 321f, 949f, 362f, 990f), floatArrayOf(429f, 955f, 499f, 994f, 505f, 949f, 547f, 990f), floatArrayOf(613f, 955f, 682f, 994f, 688f, 948f, 730f, 990f), floatArrayOf(64f, 1253f, 133f, 1293f, 139f, 1246f, 181f, 1288f), floatArrayOf(250f, 1253f, 315f, 1293f, 321f, 1247f, 363f, 1288f), floatArrayOf(430f, 1253f, 499f, 1293f, 505f, 1246f, 547f, 1288f), floatArrayOf(614f, 1253f, 683f, 1292f, 689f, 1247f, 730f, 1288f))
    ),
    GrocerySection.READY to GroceryPage(
        "fg_shop_ready", 739f, 1439f, 120f, Color(0xFFEBF3EA), Color(0xFFE5F0E5),
        coin = floatArrayOf(489f, 17f, 566f, 71f), plus = floatArrayOf(571f, 16f, 623f, 69f),
        badge = floatArrayOf(375f, 14f, 410f, 50f),
        cards = listOf(floatArrayOf(71f, 661f, 168f, 701f, 193f, 649f, 237f, 693f), floatArrayOf(310f, 662f, 407f, 701f, 428f, 649f, 472f, 694f), floatArrayOf(544f, 660f, 641f, 701f, 662f, 649f, 706f, 694f), floatArrayOf(72f, 954f, 169f, 995f, 193f, 944f, 237f, 988f), floatArrayOf(310f, 955f, 407f, 995f, 428f, 944f, 472f, 989f), floatArrayOf(544f, 954f, 641f, 995f, 662f, 944f, 706f, 989f), floatArrayOf(71f, 1250f, 168f, 1290f, 193f, 1237f, 237f, 1282f), floatArrayOf(311f, 1251f, 408f, 1292f, 428f, 1241f, 472f, 1286f), floatArrayOf(544f, 1251f, 641f, 1291f, 662f, 1238f, 706f, 1283f))
    ),
    GrocerySection.FOOD to GroceryPage(
        "fg_shop_food", 656f, 1202f, 108f, Color(0xFFF7FAF7), Color(0xFFFCFEFD),
        coin = floatArrayOf(419f, 11f, 493f, 65f), plus = floatArrayOf(498f, 11f, 551f, 64f),
        badge = floatArrayOf(331f, 14f, 363f, 47f),
        cards = listOf(floatArrayOf(59f, 526f, 156f, 565f, 165f, 513f, 211f, 558f), floatArrayOf(277f, 526f, 373f, 565f, 379f, 513f, 424f, 558f), floatArrayOf(491f, 526f, 587f, 565f, 593f, 513f, 637f, 558f), floatArrayOf(59f, 739f, 156f, 779f, 166f, 726f, 211f, 771f), floatArrayOf(277f, 740f, 373f, 778f, 379f, 726f, 424f, 771f), floatArrayOf(491f, 740f, 587f, 779f, 593f, 726f, 637f, 771f), floatArrayOf(59f, 941f, 156f, 979f, 165f, 927f, 211f, 971f), floatArrayOf(277f, 941f, 373f, 979f, 379f, 927f, 424f, 971f), floatArrayOf(491f, 941f, 587f, 979f, 593f, 927f, 637f, 971f), floatArrayOf(59f, 1152f, 156f, 1191f, 166f, 1136f, 211f, 1180f), floatArrayOf(277f, 1152f, 373f, 1191f, 379f, 1136f, 424f, 1180f), floatArrayOf(491f, 1152f, 587f, 1191f, 593f, 1136f, 637f, 1180f))
    )
)

/** Куда ведёт баннер внизу раздела («Смотреть все», стрелка) — в следующий раздел. */
private fun nextSection(s: GrocerySection): GrocerySection = when (s) {
    GrocerySection.FRUITS -> GrocerySection.VEG
    GrocerySection.VEG -> GrocerySection.DRINKS
    GrocerySection.DRINKS -> GrocerySection.READY
    GrocerySection.READY -> GrocerySection.FOOD
    GrocerySection.FOOD, GrocerySection.HOME -> GrocerySection.FRUITS
}

/** Картинка продукта (вырезана из макета) — fg_food_<id>. */
fun groceryArt(id: String): String = "fg_food_$id"

@Composable
fun GroceryScreen(vm: GameViewModel) {
    var sectionName by rememberSaveable { mutableStateOf(GrocerySection.HOME.name) }
    var cartOpen by rememberSaveable { mutableStateOf(false) }
    var catalogOpen by remember { mutableStateOf(false) }
    val section = GrocerySection.valueOf(sectionName)
    val go: (GrocerySection) -> Unit = { sectionName = it.name; cartOpen = false; catalogOpen = false }

    BackHandler(enabled = cartOpen || catalogOpen || section != GrocerySection.HOME) {
        when {
            catalogOpen -> catalogOpen = false
            cartOpen -> cartOpen = false
            else -> go(GrocerySection.HOME)
        }
    }

    val page = PAGES.getValue(section)
    if (!rememberHasArt(page.art)) {
        GroceryFallback(vm)
        return
    }
    if (cartOpen) {
        CartScreen(vm, onBack = { cartOpen = false }, onHome = { go(GrocerySection.HOME) }, onCatalog = { cartOpen = false; catalogOpen = true })
    } else {
        SectionPage(vm, section, page, go, onCart = { cartOpen = true }, onCatalog = { catalogOpen = true })
    }
    if (catalogOpen) CatalogSheet(onPick = go, onClose = { catalogOpen = false })
}

/** Место на картинке (в её пикселях) → отступ и размер в dp при масштабе [k] dp на пиксель. */
private fun Modifier.px(k: Float, x0: Float, y0: Float, x1: Float, y1: Float): Modifier =
    this.offset((x0 * k).dp, (y0 * k).dp).size(((x1 - x0) * k).dp, ((y1 - y0) * k).dp)

private fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick
)

@Composable
private fun SectionPage(
    vm: GameViewModel,
    section: GrocerySection,
    page: GroceryPage,
    go: (GrocerySection) -> Unit,
    onCart: () -> Unit,
    onCatalog: () -> Unit
) {
    val fb = rememberFeedback()
    val items = Grocery.section(section)
    Box(Modifier.fillMaxSize().background(page.bg).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
            if (section == GrocerySection.HOME) {
                // На «Главной» в макете нет стрелки назад — добавлена полоска «В комнату».
                Row(
                    Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { vm.back() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("←  В комнату", color = ShopInk, fontSize = 15.sp, fontFamily = MontikFont, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    val inFridge = Grocery.fridgeCount(vm.state)
                    Text("В холодильнике: $inFridge", color = ShopGrey, fontSize = 13.sp, fontFamily = MontikFont)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val k = maxWidth.value / page.w
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().aspectRatio(page.w / page.h)) {
                        ArtImage(page.art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
                        // Монеты в кошельке и «+» — заработать.
                        Box(Modifier.px(k, page.coin[0], page.coin[1], page.coin[2], page.coin[3]), contentAlignment = Alignment.CenterStart) {
                            Text(spaced(vm.state.coins), color = ShopInk, fontSize = (34f * k).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Box(Modifier.px(k, page.plus[0], page.plus[1], page.plus[2], page.plus[3]).tap { vm.goTo(Screen.Work) })
                        if (section != GrocerySection.HOME) {
                            // Стрелка «назад» в левом верхнем углу кадра — на «Главную» магазина.
                            Box(Modifier.px(k, 0f, 0f, 120f, 100f).tap { go(GrocerySection.HOME) })
                        }
                        // Карточки: цена в монетах и «+» в корзину.
                        items.forEachIndexed { i, item ->
                            val c = page.cards.getOrNull(i)
                            if (c != null) {
                            Box(Modifier.px(k, c[0], c[1], c[2], c[3]), contentAlignment = Alignment.CenterStart) {
                                Text("${item.price}", color = ShopInk, fontSize = (27f * k).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Box(Modifier.px(k, c[4] - 10f, c[5] - 10f, c[6] + 10f, c[7] + 10f).tap { fb.tap(); vm.cartAdd(item.id) })
                            val inCart = vm.cart[item.id] ?: 0
                            if (inCart > 0) {
                                val size = (c[6] - c[4]) * 0.62f
                                Box(
                                    Modifier.px(k, c[6] - size * 0.55f, c[5] - size * 0.55f, c[6] + size * 0.45f, c[5] + size * 0.45f)
                                        .clip(CircleShape).background(Color(0xFFF2A516)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$inCart", color = Color.White, fontSize = (size * k * 0.62f).sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            }
                        }
                        SectionHits(section, page, k, go)
                    }
                }
            }
            NavBar(page.art + "_nav", page.w, page.navH, page.navBg, page.badge, vm.cart, cartTab = false,
                onHome = { go(GrocerySection.HOME) }, onCatalog = onCatalog, onCart = onCart,
                onCoins = { vm.goTo(Screen.Work) }, onProfile = { vm.goTo(Screen.Shop) })
        }
    }
}

/** Плитки разделов и баннеры на картинке кадра. */
@Composable
private fun SectionHits(section: GrocerySection, page: GroceryPage, k: Float, go: (GrocerySection) -> Unit) {
    if (section == GrocerySection.HOME) {
        Box(Modifier.px(k, 21f, 369f, 134f, 476f).tap { go(GrocerySection.FOOD) })
        Box(Modifier.px(k, 145f, 369f, 261f, 476f).tap { go(GrocerySection.VEG) })
        Box(Modifier.px(k, 272f, 369f, 389f, 476f).tap { go(GrocerySection.FRUITS) })
        Box(Modifier.px(k, 399f, 369f, 517f, 476f).tap { go(GrocerySection.FOOD) })
        Box(Modifier.px(k, 527f, 369f, 645f, 476f).tap { go(GrocerySection.DRINKS) })
        Box(Modifier.px(k, 19f, 491f, 644f, 691f).tap { go(GrocerySection.READY) })
        Box(Modifier.px(k, 480f, 703f, 650f, 746f).tap { go(GrocerySection.FRUITS) })
        Box(Modifier.px(k, 480f, 970f, 650f, 1012f).tap { go(GrocerySection.FOOD) })
    } else {
        // Баннер внизу раздела («Смотреть все», стрелка) — следующий раздел.
        Box(Modifier.px(k, 0f, page.h - 130f, page.w, page.h - 8f).tap { go(nextSection(section)) })
    }
}

/**
 * Нижняя панель кадра: «Главная», «Каталог», «Корзина», «Монетки» (или «Избранное» в корзине), «Профиль».
 * Счётчик корзины на панели стёрт — число рисуется своё.
 */
@Composable
private fun NavBar(
    art: String,
    w: Float,
    h: Float,
    bg: Color,
    badge: FloatArray,
    cart: Map<String, Int>,
    cartTab: Boolean,
    onHome: () -> Unit,
    onCatalog: () -> Unit,
    onCart: () -> Unit,
    onCoins: () -> Unit,
    onProfile: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(bg)) {
        val k = maxWidth.value / w
        Box(Modifier.fillMaxWidth().aspectRatio(w / h)) {
            ArtImage(art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
            val col = w / 5f
            Box(Modifier.px(k, 0f, 0f, col, h).tap(onHome))
            Box(Modifier.px(k, col, 0f, col * 2, h).tap(onCatalog))
            Box(Modifier.px(k, col * 2, 0f, col * 3, h).tap(onCart))
            Box(Modifier.px(k, col * 3, 0f, col * 4, h).tap(onCoins))
            Box(Modifier.px(k, col * 4, 0f, w, h).tap(onProfile))
            val n = Grocery.count(cart)
            if (n > 0) {
                Box(
                    Modifier.px(k, badge[0], badge[1], badge[2], badge[3]).clip(CircleShape).background(ShopGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$n", color = Color.White, fontSize = ((badge[3] - badge[1]) * k * 0.62f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** «Каталог»: разделы магазина — всплывающая карточка в стиле макета. */
@Composable
private fun CatalogSheet(onPick: (GrocerySection) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).tap(onClose), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFFF1FAF1))
                .tap { }
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Каталог", color = ShopInk, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
            val rows = listOf(
                GrocerySection.FRUITS to "fg_food_oranges",
                GrocerySection.VEG to "fg_food_tomatoes",
                GrocerySection.DRINKS to "fg_food_juice_orange",
                GrocerySection.READY to "fg_food_chicken_rice",
                GrocerySection.FOOD to "fg_food_cheese"
            )
            for ((sec, icon) in rows) {
                Row(
                    Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                        .background(Color.White).clickable { onPick(sec) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(icon, Modifier.size(46.dp), ContentScale.Fit) {}
                    Spacer(Modifier.width(12.dp))
                    Text(sec.title, Modifier.weight(1f), color = ShopInk, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                    Text("›", color = ShopGreen, fontSize = 26.sp)
                }
            }
        }
    }
}

// ───────────────────────── Корзина ─────────────────────────

private const val CART_W = 752f
private const val CART_TOP_H = 200f
private const val CART_NAV_H = 126f

@Composable
private fun CartScreen(vm: GameViewModel, onBack: () -> Unit, onHome: () -> Unit, onCatalog: () -> Unit) {
    val fb = rememberFeedback()
    val cart = vm.cart
    val lines = cart.entries.mapNotNull { (id, n) -> Grocery.item(id)?.let { it to n } }
    val total = Grocery.total(cart)
    val count = Grocery.count(cart)
    Box(Modifier.fillMaxSize().background(Color(0xFFEDF5EE)).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val k = maxWidth.value / CART_W
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().aspectRatio(CART_W / CART_TOP_H)) {
                        ArtImage("fg_shop_cart_top", Modifier.fillMaxSize(), ContentScale.FillBounds) {}
                        Box(Modifier.px(k, 0f, 0f, 130f, 90f).tap(onBack))
                        Box(Modifier.px(k, 620f, 0f, 752f, 90f).tap { vm.cartClear() })
                        Box(Modifier.px(k, 30f, 148f, 700f, 180f), contentAlignment = Alignment.CenterStart) {
                            Text(
                                if (count == 0) "Пока пусто" else "${goodsWord(count)} на сумму $total монет",
                                color = ShopGrey, fontSize = (30f * k).sp, maxLines = 1
                            )
                        }
                    }
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (lines.isEmpty()) {
                            CartCard {
                                Text("Корзина пуста", color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                                Spacer(Modifier.height(4.dp))
                                Text("Нажимай «+» на карточках продуктов — они появятся здесь.", color = ShopGrey, fontSize = 15.sp, fontFamily = MontikFont)
                                Spacer(Modifier.height(12.dp))
                                GreenButton("В каталог  →") { onCatalog() }
                            }
                        }
                        for ((item, n) in lines) CartRow(item, n, onMinus = { fb.tap(); vm.cartRemove(item.id) }, onPlus = { fb.tap(); vm.cartAdd(item.id) }, onDrop = { vm.cartDrop(item.id) })
                        if (lines.isNotEmpty()) {
                            val (needs, wants) = Grocery.split(cart)
                            CartCard {
                                SumRow("Итого", "$total", big = true)
                                SumRow("Товары ($count)", "$total")
                                SumRow("Нужное (еда и вода)", "$needs")
                                if (wants > 0) SumRow("Желаемое (вкусности)", "$wants")
                                SumRow("Доставка", "Бесплатно", green = true)
                                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color(0xFFD5E6D3)))
                                SumRow("К оплате", "$total", big = true)
                                SumRow("В кошельке", "${vm.state.coins}")
                                Spacer(Modifier.height(10.dp))
                                val enough = vm.state.coins >= total
                                GreenButton(if (enough) "🔒  Оплатить  →" else "Не хватает ${total - vm.state.coins} монет", enabled = enough) {
                                    if (vm.checkout()) fb.coin() else fb.bad()
                                }
                                if (!enough) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "В минус покупать нельзя: убери что-нибудь из корзины или заработай монеты.",
                                        color = ShopGrey, fontSize = 14.sp, fontFamily = MontikFont
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            NavBar("fg_shop_cart_nav", CART_W, CART_NAV_H, Color(0xFFEFF5EF), floatArrayOf(380f, 14f, 417f, 50f), cart, cartTab = true,
                onHome = onHome, onCatalog = onCatalog, onCart = {}, onCoins = onHome, onProfile = { vm.goTo(Screen.Shop) })
        }
    }
}

private fun goodsWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> "товар"
        m10 in 2..4 && m100 !in 12..14 -> "товара"
        else -> "товаров"
    }
    return "$n $w"
}

@Composable
private fun CartCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF8FBF7)).padding(16.dp)
    ) { content() }
}

@Composable
private fun CartRow(item: GroceryItem, n: Int, onMinus: () -> Unit, onPlus: () -> Unit, onDrop: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF8FBF7)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(groceryArt(item.id), Modifier.size(64.dp), ContentScale.Fit) {
            Text(item.emoji, fontSize = 34.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = ShopInk, fontSize = 15.sp, fontFamily = MontikFont, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.amount, color = ShopGrey, fontSize = 13.sp, fontFamily = MontikFont)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(QtyGreen).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("−", Modifier.clickable(onClick = onMinus).padding(horizontal = 8.dp, vertical = 4.dp), color = ShopGreen, fontSize = 20.sp)
            Text("$n", color = ShopGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("+", Modifier.clickable(onClick = onPlus).padding(horizontal = 8.dp, vertical = 4.dp), color = ShopGreen, fontSize = 20.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.price * n}", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                CoinIcon(Modifier.size(16.dp))
            }
            if (n > 1) Text("${item.price} / шт.", color = ShopGrey, fontSize = 11.sp)
        }
        Text("🗑", Modifier.clickable(onClick = onDrop).padding(6.dp), fontSize = 18.sp, color = ShopGrey)
    }
}

@Composable
private fun SumRow(label: String, value: String, big: Boolean = false, green: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, Modifier.weight(1f), color = if (big) ShopInk else ShopGrey,
            fontSize = if (big) 20.sp else 15.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, fontFamily = MontikFont
        )
        Text(
            value, color = if (green) ShopGreen else ShopInk,
            fontSize = if (big) 22.sp else 15.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal
        )
        if (!green) {
            Spacer(Modifier.width(4.dp))
            CoinIcon(Modifier.size(if (big) 20.dp else 15.dp))
        }
    }
}

@Composable
fun GreenButton(text: String, enabled: Boolean = true, height: Dp = 54.dp, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(50))
            .background(if (enabled) ShopGreen else Color(0xFFB9C9B7))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = MontikFont, textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Без картинок макета ─────────────────────────

/** Если картинок магазина нет — простой список всех продуктов с «+» и оплатой. */
@Composable
private fun GroceryFallback(vm: GameViewModel) {
    ScreenScaffold("🛒 Продукты", vm) {
        for (item in Grocery.all) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${item.emoji} ${item.name}", Modifier.weight(1f))
                Text("${item.price} ", fontWeight = FontWeight.Bold)
                Text("+ ${vm.cart[item.id] ?: ""}", Modifier.clickable { vm.cartAdd(item.id) }.padding(8.dp), color = ShopGreen)
            }
        }
        GreenButton("Оплатить ${Grocery.total(vm.cart)}", enabled = vm.cart.isNotEmpty()) { vm.checkout() }
    }
}
