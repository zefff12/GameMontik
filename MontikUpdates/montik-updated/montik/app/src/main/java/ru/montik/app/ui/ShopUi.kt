package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Customer
import ru.montik.app.game.CustomerResult
import ru.montik.app.game.ShelfCategory
import ru.montik.app.game.ShopGame
import ru.montik.app.game.ShopProduct
import ru.montik.app.game.ShopWork
import ru.montik.app.game.StandLayout

/*
 * Работа Монтика в магазине — экраны точно по кадрам макета Figma (кадр 412×917, координаты из макета):
 *   «Работа с монтиком за кассой»  — главный экран магазина (StoreHub);
 *   «Работа»                        — пустая касса, когда работать сейчас нельзя;
 *   «Загрузка миниигры "касса"», «Миниигра касса2» (подсказка + «Начать»), «Миниигра касса» (лента, «Готово»);
 *   «Загрузка миниигры "стенд"», «Корзина с продуктами для стенда» («Стеллажи»), «Мини игра стенд» («Корзина»).
 * Картинки — файлы fg_shop_*, fg_cashier_bg, fg_shelf_*, fg_prod_* в res/drawable-nodpi.
 * Товары — пять упаковок из макета; где они лежат в корзине и на ленте, взято из тех же кадров.
 * Правила и оценка — game/ShopWork.kt. Без картинок экраны рисуются кодом (запасной вариант).
 */

// Цвета из макета.
private val PillGreen = Color(0xFF8AD742)
private val PillBlack = Color(0xFF070707)
private val BubbleText = Color(0xFF45602B)
private val DisplayInk = Color(0xFF0B2D19)
private val PosBlack = Color(0xFF141313)
private val PosLime = Color(0xFFC1FA86)
private val LoadGreen = Color(0xFF4DD453)
private val LoadInk = Color(0xFF1E381F)
private val StoreGreen = Color(0xFF0F4A32)
private val StoreGreenLight = Color(0xFF1F7A4A)

// ───────────────────────── Общие детали экранов ─────────────────────────

/** Белое облачко с репликой, как «Жми на продукты и пробивай их!» в макете (скругление 41, текст #45602B). */
@Composable
fun DesignScope.Bubble(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    size: Float = 18f,
    color: Color = BubbleText,
    align: TextAlign = TextAlign.Center
) {
    Box(
        Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(minOf(41f, h / 2f)))).background(Color.White),
        contentAlignment = if (align == TextAlign.Start) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text,
            color = color,
            fontSize = fs(size, false),
            fontWeight = FontWeight.Bold,
            fontFamily = MontikFont,
            textAlign = align,
            lineHeight = fs(size * 1.2f, false),
            modifier = if (align == TextAlign.Start) Modifier.padding(start = d(25f)).requiredWidth(d(w - 44f))
            else Modifier.requiredWidth(d(w - 36f))
        )
    }
}

/** Кнопка-пилюля 169×59 из макета: «Готово», «Корзина», «Стеллажи» — зелёные #8AD742, «Начать» — чёрная. */
@Composable
fun DesignScope.DPill(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fill: Color = PillGreen,
    textColor: Color = Color.White,
    size: Float = 20f,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .at(x, y, w, h)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) fill else fill.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontSize = fs(size, false),
            fontWeight = FontWeight.Bold,
            fontFamily = MontikFont,
            textAlign = TextAlign.Center
        )
    }
}

/** Фон под кадром на весь экран (для телефонов длиннее макета): та же картинка, обрезанная по краям. */
@Composable
private fun StoreBackground(art: String) {
    ArtImage(art, Modifier.fillMaxSize(), ContentScale.Crop) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(StoreGreen, StoreGreenLight))))
    }
}

/** Картинка кадра в рамке из макета; если файла нет — рисуется [fallback] во весь кадр. */
@Composable
private fun DesignScope.Scene(
    art: String,
    x: Float = 0f,
    y: Float = 0f,
    w: Float = DESIGN_W,
    h: Float = DESIGN_H,
    fallback: @Composable DesignScope.() -> Unit
) {
    if (rememberHasArt(art)) Art(art, x, y, w, h, ContentScale.FillBounds) else fallback()
}

// ───────────────────────── Товары из макета ─────────────────────────

/**
 * Картинка товара и где он лежит в кадрах макета.
 * [vis] — видимая часть картинки без прозрачных полей (доли ширины и высоты): по ней считаются
 * зоны нажатия и посадка на полку. [aspect] — ширина / высота файла.
 * Корзина — кадр «Корзина с продуктами для стенда»: центр ([bx], [by]), размер картинки ([bw]×[bh]), поворот [rot].
 * Лента — кадр «Миниигра касса»: размер на ленте ([beltW]×[beltH]).
 */
private class ProductArt(
    val art: String,
    val aspect: Float,
    val vis: FloatArray,
    val beltW: Float,
    val beltH: Float,
    val bx: Float,
    val by: Float,
    val bw: Float,
    val bh: Float,
    val rot: Float,
    val z: Int
)

private val PRODUCT_ART = mapOf(
    // Порядок z — как слои в кадре: масло снизу, макароны сверху.
    "olive_oil" to ProductArt(
        "fg_prod_olive_oil", 1024f / 1536f, floatArrayOf(0.297f, 0.009f, 0.704f, 0.983f),
        98f, 147f, 49f + 221.87f / 2f, 367f + 279.33f / 2f, 163f, 245f, 15.3f, 0
    ),
    "tomato_sauce" to ProductArt(
        "fg_prod_tomato_sauce", 1024f / 1536f, floatArrayOf(0.141f, 0.042f, 0.859f, 0.955f),
        73f, 110f, 204f + 132.51f / 2f, 469f + 167.11f / 2f, 97.86f, 146.78f, -15f, 1
    ),
    "salt" to ProductArt(
        "fg_prod_salt", 1024f / 1536f, floatArrayOf(0.081f, 0.023f, 0.938f, 0.970f),
        73f, 109f, 69f + 132.91f / 2f, 322f + 159.89f / 2f, 91.18f, 136.77f, 20.26f, 2
    ),
    "granola" to ProductArt(
        "fg_prod_granola", 1295f / 1214f, floatArrayOf(0.215f, 0.071f, 0.777f, 0.949f),
        235f, 220f, 135f + 223f / 2f, 285f + 209f / 2f, 223f, 209f, 0f, 3
    ),
    "pasta" to ProductArt(
        "fg_prod_pasta", 1024f / 1536f, floatArrayOf(0.035f, 0.021f, 0.966f, 0.956f),
        71.55f, 107.33f, 109f + 193.27f / 2f, 402f + 243.83f / 2f, 142.81f, 214.22f, -14.96f, 4
    ),
    // Товары большого стеллажа (картинки вырезаны по контуру, прозрачных полей нет).
    "corn_puffs" to ProductArt("fg_prod_corn_puffs", 213f / 420f, floatArrayOf(0f, 0f, 1f, 1f), 56f, 110f, 305f, 330f, 70f, 138f, 12f, 5),
    "olives" to ProductArt("fg_prod_olives", 228f / 420f, floatArrayOf(0f, 0f, 1f, 1f), 58f, 106f, 102f, 590f, 70f, 129f, -10f, 6),
    "jam" to ProductArt("fg_prod_jam", 420f / 333f, floatArrayOf(0f, 0f, 1f, 1f), 108f, 86f, 300f, 612f, 112f, 89f, 6f, 7),
    "banana_chips" to ProductArt("fg_prod_banana_chips", 270f / 420f, floatArrayOf(0f, 0f, 1f, 1f), 66f, 103f, 118f, 300f, 76f, 118f, -14f, 8),
    "eggs" to ProductArt("fg_prod_eggs", 419f / 220f, floatArrayOf(0f, 0f, 1f, 1f), 128f, 67f, 196f, 648f, 132f, 69f, 0f, 9)
)

private fun artOf(p: ShopProduct): ProductArt? = PRODUCT_ART[p.id]

/**
 * Упаковка товара с центром ([cx], [cy]), размером [w]×[h] и поворотом [rot] (как у слоя в Figma).
 * Нажатие ловится только по видимой части упаковки, с учётом поворота.
 */
@Composable
private fun DesignScope.ProductSprite(
    product: ShopProduct,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    rot: Float,
    alpha: Float = 1f,
    lifted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val art = artOf(product)
    Box(
        Modifier
            .at(cx - w / 2f, cy - h / 2f, w, h)
            .graphicsLayer {
                rotationZ = rot
                this.alpha = alpha
                if (lifted) {
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
            }
    ) {
        ArtImage(art?.art ?: "", Modifier.fillMaxSize(), ContentScale.FillBounds) {
            Box(
                Modifier.fillMaxSize().padding(d(6f)).clip(RoundedCornerShape(d(12f))).background(Color(0xFFF1E3C7)),
                contentAlignment = Alignment.Center
            ) { Text(product.emoji, fontSize = fs(minOf(w, h) * 0.45f, false)) }
        }
        if (onClick != null) {
            val v = art?.vis ?: floatArrayOf(0f, 0f, 1f, 1f)
            Box(
                Modifier
                    .at(v[0] * w, v[1] * h, (v[2] - v[0]) * w, (v[3] - v[1]) * h)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            )
        }
    }
}

/**
 * Товар стоит в рамке [x], [y], [w], [h] (пустое место на полке): видимая часть вписывается по ширине
 * и чуть выше рамки, низ упаковки — на полке, как макароны в кадре «Мини игра стенд».
 */
@Composable
private fun DesignScope.StandingProduct(product: ShopProduct, x: Float, y: Float, w: Float, h: Float, tallness: Float = 1.35f) {
    val art = artOf(product)
    if (art == null) {
        ProductSprite(product, x + w / 2f, y + h / 2f, w, h, 0f)
        return
    }
    val v = art.vis
    val visAspect = (v[2] - v[0]) * art.aspect / (v[3] - v[1])
    val visW = minOf(w, h * tallness * visAspect)
    val visH = visW / visAspect
    val fullW = visW / (v[2] - v[0])
    val fullH = visH / (v[3] - v[1])
    val bottom = y + h + 4f
    val left = x + w / 2f - fullW * (v[0] + v[2]) / 2f
    val top = bottom - fullH * v[3]
    Art(art.art, left, top, fullW, fullH, ContentScale.FillBounds)
}

// ───────────────────────── Запасные фоны (если картинок из макета нет) ─────────────────────────

@Composable
private fun DesignScope.HubFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE9E2D2), Color(0xFFCFC6B3))), size = size)
        drawRoundRect(
            Color(0xFF0B3B26),
            topLeft = Offset(96f * k, 34f * k),
            size = Size(282f * k, 118f * k),
            cornerRadius = CornerRadius(14f * k)
        )
        drawRect(Color(0xFFB9BEC2), Offset(0f, 560f * k), Size(size.width, 90f * k))
        drawRect(Color(0xFF8A5A34), Offset(0f, 650f * k), Size(size.width, 200f * k))
    }
    DText("КАССА 1", 96f, 70f, 282f, 44f, align = TextAlign.Center, color = Color.White, mono = false)
}

@Composable
private fun DesignScope.CashierFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Brush.verticalGradient(listOf(Color(0xFF16402C), Color(0xFF2B6B47))), size = size)
        drawRect(Color(0xFF9BA3A8), Offset(20f * k, 280f * k), Size(290f * k, 480f * k))
        drawRect(Color(0xFF26292B), Offset(40f * k, 300f * k), Size(250f * k, 450f * k))
        drawRect(Color(0xFFB9BEC2), Offset(0f, 760f * k), Size(size.width, size.height - 760f * k))
        drawRoundRect(Color(0xFF15181A), Offset(264f * k, 240f * k), Size(148f * k, 222f * k), CornerRadius(14f * k))
        drawRoundRect(Color(0xFFE9F0E4), Offset(274f * k, 253f * k), Size(132f * k, 197f * k), CornerRadius(8f * k))
    }
    DText("Товар", 283f, 260f, 60f, 11f, bold = false, mono = false, color = DisplayInk)
    DText("Цена", 340f, 260f, 57f, 11f, bold = false, mono = false, color = DisplayInk, align = TextAlign.End)
    DText("Итого:", 283f, 423f, 60f, 15f, bold = false, mono = false, color = DisplayInk)
}

@Composable
private fun DesignScope.ShelfFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Color(0xFF12402B), size = size)
        drawRect(Color(0xFF33413A), Offset(66f * k, 110f * k), Size(size.width - 66f * k, size.height - 110f * k))
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f * k, 8f * k))
        for (slot in SHELF_SLOTS) {
            drawRect(Color(0xFFB9BEC2), Offset(66f * k, (slot.y + slot.h + 6f) * k), Size(size.width - 66f * k, 10f * k))
            drawRect(
                Color.White,
                topLeft = Offset(slot.x * k, slot.y * k),
                size = Size(slot.w * k, slot.h * k),
                style = Stroke(width = 2f * k, pathEffect = dash)
            )
        }
    }
}

@Composable
private fun DesignScope.BasketFallback() {
    Canvas(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H)) {
        val k = size.width / DESIGN_W
        drawRect(Color(0xFFDCD6C8), size = size)
        drawRoundRect(Color(0xFF1E6B3A), Offset(20f * k, 200f * k), Size(372f * k, 500f * k), CornerRadius(40f * k))
        drawRoundRect(Color(0xFF2C8A4D), Offset(46f * k, 228f * k), Size(320f * k, 444f * k), CornerRadius(26f * k))
    }
}

// ───────────────────────── Главный экран магазина ─────────────────────────

private enum class StoreMode { Hub, Cashier, Shelves }

/** Экран «Магазин»: выбор мини-игры. «Назад» ведёт на экран «Работа», а из игры — в этот выбор. */
@Composable
fun StoreScreen(vm: GameViewModel) {
    var mode by remember { mutableStateOf(StoreMode.Hub) }
    BackHandler(enabled = true) {
        if (mode == StoreMode.Hub) vm.goTo(Screen.Work) else mode = StoreMode.Hub
    }
    when (mode) {
        StoreMode.Hub -> StoreHub(vm, onCashier = { mode = StoreMode.Cashier }, onShelves = { mode = StoreMode.Shelves })
        StoreMode.Cashier -> CashierGame(vm) { mode = StoreMode.Hub }
        StoreMode.Shelves -> ShelfGame(vm) { mode = StoreMode.Hub }
    }
}

/** Кадр «Работа с монтиком за кассой»: Монтик в фартуке за кассой; внизу — выбор задания. */
@Composable
private fun StoreHub(vm: GameViewModel, onCashier: () -> Unit, onShelves: () -> Unit) {
    val s = vm.state
    val blocker = vm.storeBlocker
    val rank = ShopWork.rank(s)
    val next = rank.next

    if (blocker != null) {
        // Кадр «Работа»: касса пустая — Монтик сегодня не на смене.
        DesignCanvas(background = { StoreBackground("fg_shop_idle_bg") }) {
            Scene("fg_shop_idle_bg", -3f, -5f, 415f, 922f) { HubFallback() }
            Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
            Bubble(blocker, 24f, 668f, 364f, 132f, size = 18f, color = MontikColors.Bad)
            DPill("Назад", 121f, 828f, 169f, 59f, fill = PillBlack) { vm.goTo(Screen.Work) }
        }
        return
    }

    DesignCanvas(background = { StoreBackground("fg_shop_hub_bg") }) {
        Scene("fg_shop_hub_bg", -20f, -8f, 526f, 933f) { HubFallback() }
        if (rememberHasArt("fg_shop_hub_hero")) {
            Art("fg_shop_hub_hero", 84f, 285f, 289f, 347f, ContentScale.FillBounds)
        } else {
            MontikView(sprite = vm.sprite, state = s, boxSize = d(250f), modifier = Modifier.at(104f, 300f))
        }
        Art("fg_shop_hub_counter", -170f, 385f, 826f, 494f, ContentScale.FillBounds)
        PosDisplay()
        NameBadge(s.heroName)

        Box(Modifier.at(17f, 10f)) { BackCircle({ vm.goTo(Screen.Work) }) }
        Bubble(
            "${rank.title} · за смену ${rank.base} монет + премия.\n" +
                (if (next != null) "До должности «${next.title}» смен: ${ShopWork.shiftsToNextRank(s)}." else "Это высшая должность!") +
                "\nЛучшее: касса ${starsText(ShopWork.bestStars(s, ShopGame.CASHIER))} · стенд ${starsText(ShopWork.bestStars(s, ShopGame.SHELVES))}",
            24f, 656f, 364f, 150f, size = 16f, align = TextAlign.Start
        )
        DPill("Касса", 24f, 828f, 169f, 59f, fill = PillGreen, onClick = onCashier)
        DPill("Стенд", 219f, 828f, 169f, 59f, fill = PillBlack, onClick = onShelves)
    }
}

/** Экран кассы из кадра: чёрный дисплей с надписью «К оплате:» и окошком суммы (слои 57:22–57:37). */
@Composable
private fun DesignScope.PosDisplay() {
    Box(Modifier.at(24.88f, 425.88f, 118.49f, 68.45f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(d(116.735f), d(65.251f))
                .rotate(1.58f)
                .clip(RoundedCornerShape(d(4f)))
                .background(PosBlack)
        )
    }
    Box(Modifier.at(33.28f, 431.74f, 46.1f, 12.4f).rotate(0.5f), contentAlignment = Alignment.CenterStart) {
        Text("К оплате:", color = PosLime, fontSize = fs(10f, false), fontFamily = MontikFont, maxLines = 1, softWrap = false)
    }
    Box(Modifier.at(50.07f + 37.22f, 428.68f + 4f, 45.29f, 14.03f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(d(45f), d(13f))
                .rotate(0.96f)
                .clip(RoundedCornerShape(d(3f)))
                .background(PosBlack)
                .border(d(0.5f), PosLime, RoundedCornerShape(d(3f))),
            contentAlignment = Alignment.Center
        ) {
            Text("0 ₽", color = Color.White.copy(alpha = 0.3f), fontSize = fs(5f, false), fontFamily = MontikFont, maxLines = 1, softWrap = false)
        }
    }
}

/** Бейдж на фартуке (слой «Монтик», 5 px, наклон −7,57°) — с именем, которое дал ребёнок. */
@Composable
private fun DesignScope.NameBadge(name: String) {
    val size = if (name.length <= 7) 5f else 5f * 7f / name.length
    Box(Modifier.at(253.03f - 6f, 467.18f, 19.63f + 12f, 8.45f).rotate(-7.57f), contentAlignment = Alignment.Center) {
        Text(name, color = Color.Black, fontSize = fs(size, false), fontFamily = MontikFont, maxLines = 1, softWrap = false)
    }
}

// ───────────────────────── Мини-игра «Касса» ─────────────────────────

private enum class CashierStep { Loading, Intro, Playing, Change }

/**
 * Два места на ленте из кадра «Миниигра касса»: ближнее к кассиру (гранола, слой 185:39)
 * и следующее (макароны, слой 104:103). Остальные товары покупателя ждут своей очереди.
 */
private val BELT_FRONT = floatArrayOf(12f + 318.29f / 2f, 433f + 314.42f / 2f, -34.51f)
private val BELT_NEXT = floatArrayOf(109f + 96.83f / 2f, 346f + 122.16f / 2f, -14.96f)

@Composable
private fun CashierGame(vm: GameViewModel, onExit: () -> Unit) {
    val rank = remember { ShopWork.rank(vm.state) }
    val customers = remember { ShopWork.customers(vm.state) }
    var step by remember { mutableStateOf(CashierStep.Loading) }
    var index by remember { mutableStateOf(0) }
    val scanned = remember { mutableStateListOf<Int>() }
    var slips by remember { mutableStateOf(0) }
    var startedAt by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val results = remember { mutableStateListOf<CustomerResult>() }
    val fb = rememberFeedback()

    LaunchedEffect(Unit) {
        delay(1300)
        step = CashierStep.Intro
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(1600)
            note = null
        }
    }

    fun finishCustomer(changeCorrect: Boolean?) {
        val c = customers[index]
        results.add(
            CustomerResult(
                items = c.items.size,
                doubleScans = slips,
                changeCorrect = changeCorrect,
                millis = System.currentTimeMillis() - startedAt,
                parMillis = ShopWork.parMillisCashier(c.items.size, rank.withChange)
            )
        )
        if (index + 1 < customers.size) {
            index += 1
            scanned.clear()
            slips = 0
            picked = null
            startedAt = System.currentTimeMillis()
            step = CashierStep.Playing
        } else {
            val all = results.toList()
            val mistakes = all.sumOf { it.doubleScans }
            val wrong = all.count { it.changeCorrect == false }
            val details = buildList {
                add("Обслужено покупателей: ${all.size}.")
                if (mistakes > 0) add("Раз нажато «Готово», когда пробито не всё: $mistakes.")
                if (rank.withChange) add(if (wrong > 0) "Ошибок в сдаче: $wrong." else "Сдача посчитана верно.")
            }
            vm.finishShopShift(ShopGame.CASHIER, ShopWork.cashierPerformance(all), details)
            onExit()
        }
    }

    if (step == CashierStep.Loading) {
        // Кадр «Загрузка миниигры "касса"»: Монтик держит товар у сканера, на мониторе — «Загрузка задания....».
        DesignCanvas(background = { StoreBackground("fg_shop_load_cashier") }) {
            Scene("fg_shop_load_cashier", -49f, -7f, 520f, 924f) { CashierFallback() }
            Box(Modifier.at(87f, 166f, 251f, 207f).background(Color.Black), contentAlignment = Alignment.Center) {
                Text(
                    "Загрузка задания....",
                    color = LoadGreen,
                    fontSize = fs(20f, false),
                    fontFamily = MontikFont,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        return
    }

    val customer: Customer = customers[index]
    DesignCanvas(background = { StoreBackground("fg_cashier_bg") }) {
        Scene("fg_cashier_bg") { CashierFallback() }
        Box(Modifier.at(17f, 10f)) { BackCircle({ onExit() }) }

        when (step) {
            CashierStep.Intro -> {
                // Кадр «Миниигра касса2».
                Bubble("Жми на продукты и пробивай их!", 49f, 78f, 253f, 122f, size = 20f, align = TextAlign.Start)
                DPill("Начать", 121f, 828f, 169f, 59f, fill = PillBlack) {
                    startedAt = System.currentTimeMillis()
                    step = CashierStep.Playing
                }
            }
            else -> {
                // Экран кассы: под шапкой «Товар / Цена» — строки по 20 px, внизу «Итого:».
                scanned.forEachIndexed { row, itemIndex ->
                    val p = customer.items[itemIndex]
                    val rowY = 287f + row * 20f
                    Text(
                        p.name,
                        modifier = Modifier.at(283f, rowY).requiredWidth(d(66f)),
                        color = DisplayInk,
                        fontSize = fs(10.5f, false),
                        fontFamily = MontikFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${p.price} ₽",
                        modifier = Modifier.at(349f, rowY).requiredWidth(d(48f)),
                        color = DisplayInk,
                        fontSize = fs(10.5f, false),
                        fontFamily = MontikFont,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
                val runningTotal = scanned.sumOf { customer.items[it].price }
                Box(Modifier.at(320f, 420f, 77f, 23f), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "$runningTotal ₽",
                        color = DisplayInk,
                        fontSize = fs(16f, false),
                        fontFamily = MontikFont,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Лента: два ближних товара на местах из макета, остальные ждут очереди.
                val queue = customer.items.indices.filter { it !in scanned }
                queue.getOrNull(1)?.let { i -> BeltItem(customer.items[i], BELT_NEXT, step == CashierStep.Playing) { fb.scan(); scanned.add(i) } }
                queue.getOrNull(0)?.let { i -> BeltItem(customer.items[i], BELT_FRONT, step == CashierStep.Playing) { fb.scan(); scanned.add(i) } }
                if (queue.size > 2) {
                    Bubble("ещё ${queue.size - 2}", 146f, 292f, 110f, 34f, size = 14f)
                }

                Bubble(
                    note ?: "Покупатель ${index + 1} из ${customers.size}",
                    80f, 12f, 252f, 50f, size = 15f,
                    color = if (note != null) MontikColors.Bad else BubbleText
                )

                if (step == CashierStep.Playing) {
                    DPill("Готово", 121f, 828f, 169f, 59f, fill = PillGreen) {
                        val left = customer.items.size - scanned.size
                        when {
                            left > 0 -> {
                                fb.bad()
                                slips += 1
                                note = "Пробиты не все товары: осталось $left"
                            }
                            rank.withChange -> {
                                fb.good()
                                picked = null
                                step = CashierStep.Change
                            }
                            else -> {
                                fb.good()
                                finishCustomer(null)
                            }
                        }
                    }
                } else {
                    ChangeQuestion(customer, picked, onPick = {
                        picked = it
                        if (it == customer.correctChangeIndex) fb.good() else fb.bad()
                    }) { correct -> finishCustomer(correct) }
                }
            }
        }
    }
}

/** Товар на ленте: размер как в макете, поворот места на ленте. Нажатие — «пробить». */
@Composable
private fun DesignScope.BeltItem(product: ShopProduct, slot: FloatArray, active: Boolean, onScan: () -> Unit) {
    val art = artOf(product)
    ProductSprite(
        product,
        cx = slot[0],
        cy = slot[1],
        w = art?.beltW ?: 100f,
        h = art?.beltH ?: 110f,
        rot = slot[2],
        onClick = if (active) onScan else null
    )
}

/** Вопрос про сдачу: покупатель дал купюру — сколько вернуть. Верный ответ — часть оценки. */
@Composable
private fun DesignScope.ChangeQuestion(
    customer: Customer,
    picked: Int?,
    onPick: (Int) -> Unit,
    onNext: (Boolean) -> Unit
) {
    Box(Modifier.at(-40f, -40f, DESIGN_W + 80f, DESIGN_H + 80f).background(Color.Black.copy(alpha = 0.45f)))
    Box(Modifier.at(24f, 190f, 364f, 520f).clip(RoundedCornerShape(d(41f))).background(Color.White))
    DText("Покупатель дал ${customer.paid} ₽", 40f, 214f, 332f, 22f, mono = false, color = BubbleText, align = TextAlign.Center)
    DText(
        "Сумма покупки — ${customer.total} ₽. Сколько сдачи вернуть?",
        40f, 254f, 332f, 17f, bold = false, mono = false, color = BubbleText, align = TextAlign.Center
    )
    customer.changeOptions.forEachIndexed { i, option ->
        val isRight = i == customer.correctChangeIndex
        val fill = when {
            picked == null -> PillBlack
            isRight -> PillGreen
            i == picked -> MontikColors.Bad
            else -> Color(0xFF9AA3A0)
        }
        DPill("$option ₽", 60f, 322f + i * 76f, 292f, 59f, fill = fill, size = 22f, enabled = picked == null) { onPick(i) }
    }
    if (picked != null) {
        val correct = picked == customer.correctChangeIndex
        DText(
            if (correct) "Верно! ${customer.paid} − ${customer.total} = ${customer.change} ₽"
            else "Сдача — ${customer.change} ₽: ${customer.paid} − ${customer.total}.",
            40f, 556f, 332f, 16f, mono = false,
            color = if (correct) MontikColors.Good else MontikColors.Bad, align = TextAlign.Center
        )
        DPill("Дальше", 121f, 626f, 169f, 59f, fill = PillGreen) { onNext(correct) }
    }
}

// ───────────────────────── Мини-игра «Стенд» (выкладка товаров) ─────────────────────────

/**
 * Пустые места на стеллаже — пунктирные рамки на картинке кадра «Мини игра стенд», сверху вниз.
 * У последнего места (полка с чаем) категории нет: товаров для него в корзине не бывает.
 */
private class ShelfSlot(val accepts: Set<ShelfCategory>, val title: String, val x: Float, val y: Float, val w: Float, val h: Float)

private val SHELF_SLOTS = listOf(
    ShelfSlot(setOf(ShelfCategory.BREAKFAST), "гранола и хлопья", 200f, 142.5f, 75f, 78.5f),
    ShelfSlot(setOf(ShelfCategory.PASTA), "макароны и рис", 195.5f, 273.5f, 68f, 76.5f),
    ShelfSlot(setOf(ShelfCategory.SAUCES), "соусы", 193.5f, 399f, 78f, 68.5f),
    ShelfSlot(setOf(ShelfCategory.OIL), "масло", 194.5f, 517.5f, 76f, 88.5f),
    ShelfSlot(setOf(ShelfCategory.SALT), "молоко и сахар", 208.5f, 658.5f, 63f, 63f),
    ShelfSlot(emptySet(), "чай", 259.5f, 771.5f, 54f, 64.5f)
)

/**
 * Большой стеллаж «Вместе к лучшему» (картинка fg_shelf2_bg, 839×1875 → 412×921): шесть пустых мест,
 * обведённых пунктиром, по одному на полке. Места — как в макете (пиксели картинки × 412/839).
 */
private val SHELF_SLOTS_BIG = listOf(
    ShelfSlot(setOf(ShelfCategory.BREAKFAST), "хлопья и орехи", 182.7f, 141.9f, 61.4f, 77.6f),
    ShelfSlot(setOf(ShelfCategory.PASTA, ShelfCategory.SAUCES), "соусы и макароны", 286.8f, 265.2f, 53f, 69.2f),
    ShelfSlot(setOf(ShelfCategory.BEANS), "бобовые и оливки", 297.1f, 378.6f, 57f, 68.3f),
    ShelfSlot(setOf(ShelfCategory.OIL), "масло, яйца и рис", 179.2f, 491.1f, 56f, 68.7f),
    ShelfSlot(setOf(ShelfCategory.JAMS), "маринады и джемы", 340.8f, 602f, 56f, 61.9f),
    ShelfSlot(setOf(ShelfCategory.DRIED), "сухофрукты и семечки", 227.4f, 713.5f, 57.9f, 68.7f)
)

private enum class ShelfStep { Loading, Basket, Shelf }

@Composable
private fun ShelfGame(vm: GameViewModel, onExit: () -> Unit) {
    val layout = remember { ShopWork.standLayout(vm.state) }
    val slots = if (layout == StandLayout.BIG) SHELF_SLOTS_BIG else SHELF_SLOTS
    val basket = remember { mutableStateListOf<ShopProduct>().apply { addAll(ShopWork.shelfBasket(vm.state, layout)) } }
    val total = remember { basket.size }
    val placed = remember { mutableStateListOf<ShopProduct>() }
    var hand by remember { mutableStateOf<ShopProduct?>(null) }
    var step by remember { mutableStateOf(ShelfStep.Loading) }
    var mistakes by remember { mutableStateOf(0) }
    var startedAt by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteGood by remember { mutableStateOf(false) }
    var wrongSlot by remember { mutableStateOf<Int?>(null) }
    val fb = rememberFeedback()
    val shake = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(1300)
        startedAt = System.currentTimeMillis()
        step = ShelfStep.Basket
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(1700)
            note = null
        }
    }
    LaunchedEffect(wrongSlot) {
        if (wrongSlot != null) {
            // Рамка «не сюда» дрожит, как в играх: влево-вправо и на место.
            repeat(3) {
                shake.animateTo(6f, tween(45))
                shake.animateTo(-6f, tween(45))
            }
            shake.animateTo(0f, tween(45))
            delay(300)
            wrongSlot = null
        }
    }
    // Всё расставлено — небольшая пауза, чтобы ребёнок увидел последний товар на полке, и смена закончена.
    LaunchedEffect(placed.size) {
        if (placed.size == total && total > 0) {
            delay(900)
            val elapsed = System.currentTimeMillis() - startedAt
            val details = buildList {
                add("Расставлено товаров: $total.")
                add(if (mistakes > 0) "Ошибок при выкладке: $mistakes." else "Все товары встали на свои полки с первого раза.")
            }
            vm.finishShopShift(ShopGame.SHELVES, ShopWork.shelvesPerformance(total, mistakes, elapsed), details)
            onExit()
        } else if (placed.isNotEmpty()) {
            // Товар на месте — обратно к корзине за следующим.
            delay(900)
            if (hand == null) step = ShelfStep.Basket
        }
    }

    when (step) {
        ShelfStep.Loading -> {
            // Кадр «Загрузка миниигры "стенд"».
            DesignCanvas(background = { StoreBackground("fg_shelf_load_bg") }) {
                Scene("fg_shelf_load_bg", -12f, 0f, 424f, 943f) { ShelfFallback() }
                Box(Modifier.at(61f, 391f, 298f, 68f).clip(RoundedCornerShape(d(34f))).background(Color.White.copy(alpha = 0.9f)))
                Text(
                    "Загрузка задания....",
                    modifier = Modifier.at(117f, 413f),
                    color = LoadInk,
                    fontSize = fs(20f, false),
                    fontWeight = FontWeight.Bold,
                    fontFamily = MontikFont,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        ShelfStep.Basket -> {
            // Кадр «Корзина с продуктами для стенда»: товары лежат там же и так же повёрнуты, как в макете.
            DesignCanvas(background = { StoreBackground("fg_shelf_basket_bg") }) {
                Scene("fg_shelf_basket_bg") { BasketFallback() }
                basket.sortedBy { artOf(it)?.z ?: 0 }.forEach { product ->
                    val a = artOf(product)
                    ProductSprite(
                        product,
                        cx = a?.bx ?: 206f, cy = a?.by ?: 460f,
                        w = a?.bw ?: 120f, h = a?.bh ?: 150f,
                        rot = a?.rot ?: 0f,
                        lifted = product == hand
                    ) {
                        fb.tap()
                        hand = product
                        step = ShelfStep.Shelf
                    }
                }
                if (placed.isEmpty() && hand == null) {
                    Bubble("Возьми товар из корзины и поставь его на свою полку!", 49f, 60f, 314f, 104f, size = 18f, align = TextAlign.Start)
                }
                Box(Modifier.at(17f, 10f)) { BackCircle({ onExit() }) }
                DPill("Стеллажи", 122f, 821f, 169f, 59f, fill = PillGreen) { step = ShelfStep.Shelf }
            }
        }

        ShelfStep.Shelf -> {
            // Кадр «Мини игра стенд»: пустые места обведены пунктиром, товар из руки ставится в рамку.
            val shelfArt = if (layout == StandLayout.BIG && rememberHasArt("fg_shelf2_bg")) "fg_shelf2_bg" else "fg_shelf_bg"
            DesignCanvas(background = { StoreBackground(shelfArt) }) {
                if (shelfArt == "fg_shelf2_bg") Art(shelfArt, 0f, 0f, DESIGN_W, 1875f * DESIGN_W / 839f, ContentScale.FillBounds)
                else Scene("fg_shelf_bg") { ShelfFallback() }
                slots.forEachIndexed { i, slot ->
                    placed.firstOrNull { it.shelf in slot.accepts }?.let { StandingProduct(it, slot.x, slot.y, slot.w, slot.h) }
                    if (wrongSlot == i) {
                        Box(
                            Modifier
                                .at(slot.x - 3f + shake.value, slot.y - 3f, slot.w + 6f, slot.h + 6f)
                                .border(d(3f), MontikColors.Bad, RoundedCornerShape(d(6f)))
                                .background(MontikColors.Bad.copy(alpha = 0.12f))
                        )
                    }
                    Hit(slot.x - 6f, slot.y - 6f, slot.w + 12f, slot.h + 12f) {
                        val item = hand
                        when {
                            slot.accepts.isNotEmpty() && placed.any { it.shelf in slot.accepts } -> Unit
                            item == null -> {
                                noteGood = false
                                note = "Сначала возьми товар из корзины."
                            }
                            item.shelf in slot.accepts -> {
                                fb.good()
                                placed.add(item)
                                basket.remove(item)
                                hand = null
                                noteGood = true
                                note = "Верно! «${item.name}» — на своём месте."
                            }
                            else -> {
                                fb.bad()
                                mistakes += 1
                                wrongSlot = i
                                noteGood = false
                                note = "Не сюда: здесь ${slot.title}. Найди место для «${item.name}»."
                            }
                        }
                    }
                }
                // Товар «в руке» — над корзиной в левом нижнем углу кадра.
                hand?.let { StandingProduct(it, 12f, 772f, 70f, 76f, tallness = 1.3f) }
                note?.let { Bubble(it, 20f, 14f, 372f, 84f, size = 16f, color = if (noteGood) BubbleText else MontikColors.Bad) }
                if (note == null && placed.isEmpty() && layout == StandLayout.BIG) {
                    Bubble("Новый большой стеллаж! Ставь товар на полку, где стоят похожие.", 20f, 14f, 372f, 84f, size = 16f)
                }
                DPill("Корзина", 122f, 824f, 169f, 59f, fill = PillGreen) { step = ShelfStep.Basket }
            }
        }
    }
}
