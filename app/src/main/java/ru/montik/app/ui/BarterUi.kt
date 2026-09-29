package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.game.Barter
import ru.montik.app.game.Grocery

/*
 * Рынок енота Сергеевича — по кадрам Figma «при первом приходе на рынок», Android Compact 41–63
 * и «Поздравляем! Успешный бартер». Все координаты — из макета 412×917.
 *
 * Картинки (выгружены из Figma):
 *   fg_market_s1 — енот за прилавком (рассказ о Сергеевиче)
 *   fg_market_s2 — енот приветствует          fg_market_s3 — енот объясняет
 *   fg_market_s4 — енот рассказывает          fg_market_s5 — енот думает
 *   fg_market_s6 — енот скрестил лапы         fg_market_win — «Поздравляем! Успешный бартер»
 *   fg_market_honey, fg_market_tea — товары енота, fg_market_bag — рюкзак-инвентарь
 */

private val RaccoonGreen = Color(0xFF62B140)
private val MarketLime = Color(0xFF87CF44)
private val MarketRed = Color(0xFFDD2E2E)
private val MarketInk = Color(0xFF1D1D1D)

/** Сцена с енотом и где она стоит в кадре (картинки разного размера, как в макете). */
private enum class Pose(val art: String, val x: Float, val y: Float, val w: Float, val h: Float) {
    STORY("fg_market_s1", -17f, -72f, 445f, 989f),
    HELLO("fg_market_s2", -66f, -67f, 544f, 1209f),
    TALK("fg_market_s3", -101f, -89f, 653f, 1161f),
    TELL("fg_market_s4", -101f, -89f, 653f, 1161f),
    THINK("fg_market_s5", -101f, -89f, 653f, 1161f),
    ARMS("fg_market_s6", -101f, -89f, 653f, 1161f)
}

/** Шаги разговора на рынке. */
private enum class Step {
    STORY, HELLO, DOTS, ASK, NO, YES, EXPLAIN1, EXPLAIN2, EXPLAIN3, EXPLAIN4, THINK,
    CHOOSE, CHOSEN, OFFER, BAG, INVENTORY, REPLY, WIN
}

@Composable
fun BarterScreen(vm: GameViewModel) {
    val first = remember { Barter.firstVisit(vm.state) }
    var step by remember { mutableStateOf(if (first) Step.STORY else Step.HELLO) }
    var want by remember { mutableStateOf(Barter.goods.first()) }
    var offer by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var reply by remember { mutableStateOf("") }
    var traded by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    val fb = rememberFeedback()

    BackHandler {
        when (step) {
            Step.INVENTORY -> step = Step.BAG
            Step.WIN -> vm.leaveMarket(true)
            else -> vm.leaveMarket(traded)
        }
    }

    fun next(to: Step) {
        fb.tap()
        step = to
    }

    val pose = when (step) {
        Step.STORY -> Pose.STORY
        Step.HELLO, Step.YES, Step.EXPLAIN3 -> Pose.HELLO
        Step.DOTS, Step.ASK, Step.CHOOSE, Step.BAG, Step.INVENTORY -> Pose.ARMS
        Step.NO, Step.EXPLAIN2, Step.CHOSEN, Step.OFFER -> Pose.TALK
        Step.EXPLAIN1, Step.EXPLAIN4 -> Pose.TELL
        Step.THINK, Step.REPLY, Step.WIN -> Pose.THINK
    }

    DesignCanvas(background = {
        ArtImage(pose.art, Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Color(0xFF6B4A2B)))
        }
    }) {
        Art(pose.art, pose.x, pose.y, pose.w, pose.h, ContentScale.FillBounds)

        when (step) {
            Step.STORY -> {
                Story(
                    "Енота Сергеевича в городе знали почти все.\n" +
                        "Уже много лет он держал небольшой магазинчик на местном базаре. " +
                        "Сергеевич всегда следил за своими товарами, умел торговаться и никогда " +
                        "не соглашался на сделку, не подумав."
                ) { next(Step.HELLO) }
            }
            Step.HELLO -> Says("Привет! У меня сегодня отличный урожай. Хочешь что-нибудь?", big = true) {
                next(if (first) Step.DOTS else Step.CHOOSE)
            }
            Step.DOTS -> Says("...") { next(Step.ASK) }
            Step.ASK -> {
                Says("Слышал когда-нибудь слово «бартер»?", tapToContinue = false) {}
                // Кнопки «Нет» (зелёная) и «Да» (белая) — как в кадре 48.
                YesNo("Нет", 66f, 748f, MarketLime, Color.White) { next(Step.NO) }
                YesNo("Да", 246f, 748f, Color.White, Color.Black) { next(Step.YES) }
            }
            Step.NO -> Says("Понятно... Не знаешь... Сейчас расскажу!") { next(Step.EXPLAIN1) }
            Step.YES -> Says("Отлично! Тогда напомню самое главное.") { next(Step.EXPLAIN3) }
            Step.EXPLAIN1 -> Says("Бартер — это когда люди меняются товарами или услугами напрямую, без денег.") {
                next(Step.EXPLAIN2)
            }
            Step.EXPLAIN2 -> Says(
                "Например, ты отдаёшь то, что тебе не нужно, а взамен получаешь то, что тебе действительно пригодится."
            ) { next(Step.EXPLAIN3) }
            Step.EXPLAIN3 -> Says(
                "Но запомни главное: хорошая сделка должна быть выгодной и удобной для обеих сторон."
            ) { next(Step.EXPLAIN4) }
            Step.EXPLAIN4 -> Says("Если условия подходят всем — можно смело заключать сделку.") { next(Step.THINK) }
            Step.THINK -> Says(".....") { next(Step.CHOOSE) }

            Step.CHOOSE -> {
                Says("Ну что предложишь или что ты хочешь?", tapToContinue = false) {}
                // Товары енота на белых карточках (кадр 56): мёд слева, чай справа.
                Barter.goods.forEachIndexed { i, id ->
                    val x = if (i == 0) 47f else 230f
                    GoodCard(id, x, 723f, selected = false) {
                        fb.tap()
                        want = id
                        offer = emptyMap()
                        page = 0
                        step = Step.CHOSEN
                    }
                }
                Hint("Выбери, что хочешь получить", 866f)
            }
            Step.CHOSEN -> Says("Отлично... Хороший выбор! Это ${goodName(want).lowercase()} — он стоит около ${Barter.goodValue(want)} монет.") {
                next(Step.OFFER)
            }
            Step.OFFER -> Says("Ну что предложишь? Посмотри в своём рюкзаке.") { next(Step.BAG) }

            Step.BAG -> {
                // «Открыть инвентарь»: рюкзак в зелёном круге (кадр 62).
                OutlinedLabel("Открыть инвентарь", 110f, 580f, 209f, 20f)
                Box(
                    Modifier.at(110f, 610f, 191f, 191f).clip(CircleShape).background(MarketLime.copy(alpha = 0.9f))
                        .border(d(4f), Color.White, CircleShape)
                        .clickable { next(Step.INVENTORY) }
                )
                Art("fg_market_bag", 128f, 631f, 144f, 138f, ContentScale.Fit)
                Hit(110f, 610f, 191f, 191f) { next(Step.INVENTORY) }
                WantChip(want, 106f, 820f)
            }

            Step.INVENTORY -> Inventory(
                vm = vm,
                want = want,
                offer = offer,
                page = page,
                onPage = { page = it },
                onChange = { fb.tap(); offer = it },
                onClose = { next(Step.BAG) },
                onPropose = {
                    val ok = vm.barterTrade(want, offer)
                    if (ok) {
                        fb.fanfare()
                        traded = true
                        step = Step.WIN
                    } else {
                        fb.bad()
                        reply = Barter.reply(Barter.judge(want, Barter.offerValue(vm.state, offer)))
                        step = Step.REPLY
                    }
                }
            )

            Step.REPLY -> Says(reply) { next(Step.INVENTORY) }

            Step.WIN -> {
                Box(Modifier.at(-40f, -40f, 492f, 997f).background(Color.Black.copy(alpha = 0.3f)))
                Art("fg_market_win", 27f, 180f, 358f, 422f, ContentScale.Fit)
                Box(
                    Modifier.at(35f, 620f).requiredWidth(d(341f)).clip(RoundedCornerShape(d(28f)))
                        .background(Color.White).padding(horizontal = d(20f), vertical = d(14f))
                ) {
                    Text(
                        "Ты получил: ${goodEmoji(want)} ${goodName(want)} — он уже в холодильнике.\n" +
                            "Деньги не понадобились: вы честно обменялись, и сделка выгодна вам обоим!\n" +
                            "Понимание бартера: ${Barter.levelTitle(vm.state.barterUnderstanding)}.",
                        color = MarketInk,
                        fontSize = fs(15f, false),
                        lineHeight = fs(19f, false)
                    )
                }
                DPill("Отлично!", 121f, 800f, 170f, 55f, fill = MarketLime) { vm.leaveMarket(true) }
                Confetti(trigger = 1)
            }
        }

        // «Назад» — уйти с рынка (кроме итогового экрана, где есть своя кнопка).
        if (step != Step.WIN && step != Step.INVENTORY) {
            Box(Modifier.at(8f, 26f)) { BackCircle({ vm.leaveMarket(traded) }) }
        }
    }
}

// ───────────────────────── Реплики ─────────────────────────

/** Рассказчик (кадр «при первом приходе на рынок»): белое окно 340×175 и зелёный кружок «?». */
@Composable
private fun DesignScope.Story(text: String, onNext: () -> Unit) {
    Hit(0f, 0f, DESIGN_W, DESIGN_H, onNext)
    Box(
        Modifier.at(39f, 660f).requiredWidth(d(340f)).heightIn(min = d(175f))
            .clip(RoundedCornerShape(d(28f))).background(Color.White)
            .clickable(onClick = onNext)
            .padding(start = d(17f), end = d(17f), top = d(20f), bottom = d(16f))
    ) {
        Text(text, color = Color.Black, fontSize = fs(15f, false), lineHeight = fs(18.5f, false))
    }
    Box(
        Modifier.at(342f, 638f, 52f, 52f).clip(CircleShape).background(RaccoonGreen),
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = Color.White, fontSize = fs(30f, false))
    }
}

/**
 * Реплика енота: белое облако 341 шириной (скругление 62) и зелёная плашка «Енот Сергеевич».
 * [big] — крупный текст, как в кадре 41. Нажатие на экран — дальше.
 */
@Composable
private fun DesignScope.Says(
    text: String,
    big: Boolean = false,
    tapToContinue: Boolean = true,
    onNext: () -> Unit
) {
    if (tapToContinue) Hit(0f, 0f, DESIGN_W, DESIGN_H, onNext)
    val top = if (big) 631f else 588f
    Box(
        Modifier.at(35f, top).requiredWidth(d(341f)).heightIn(min = d(if (big) 124f else 95f))
            .clip(RoundedCornerShape(d(48f))).background(Color.White)
            .then(
                if (tapToContinue) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNext
                ) else Modifier
            )
            .padding(start = d(26f), end = d(26f), top = d(24f), bottom = d(if (tapToContinue) 22f else 18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.Black,
            fontSize = fs(if (big) 20f else 15f, false),
            lineHeight = fs(if (big) 24f else 19f, false),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (tapToContinue) {
            Text(
                "▸",
                color = RaccoonGreen,
                fontSize = fs(14f, false),
                modifier = Modifier.align(Alignment.BottomEnd).padding(top = d(4f))
            )
        }
    }
    Box(
        Modifier.at(87f, top - 15f, 170f, 26f).clip(RoundedCornerShape(d(13f))).background(RaccoonGreen),
        contentAlignment = Alignment.Center
    ) {
        Text("Енот Сергеевич", color = Color.White, fontSize = fs(15f, false))
    }
}

@Composable
private fun DesignScope.YesNo(text: String, x: Float, y: Float, fill: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, 100f, 54f).clip(RoundedCornerShape(d(27f))).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = fs(20f, false))
    }
}

/** Белая надпись с тенью поверх картинки. */
@Composable
private fun DesignScope.OutlinedLabel(text: String, x: Float, y: Float, w: Float, size: Float) {
    Text(
        text,
        modifier = Modifier.at(x, y).requiredWidth(d(w)),
        style = TextStyle(
            color = Color.White,
            fontSize = fs(size, false),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color.Black.copy(alpha = 0.75f), blurRadius = 8f)
        )
    )
}

@Composable
private fun DesignScope.Hint(text: String, y: Float) {
    Box(
        Modifier.at(56f, y, 300f, 34f).clip(RoundedCornerShape(d(17f))).background(Color.White.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MarketInk, fontSize = fs(14f, false), textAlign = TextAlign.Center)
    }
}

// ───────────────────────── Товары ─────────────────────────

private fun goodArt(id: String): String = if (id == "honey") "fg_market_honey" else "fg_market_tea"
private fun goodName(id: String): String = Grocery.item(id)?.name ?: id
private fun goodEmoji(id: String): String = Grocery.item(id)?.emoji ?: "🎁"

/** Белая карточка 116×115 с товаром енота (кадр 56) и ценой-подсказкой под ней. */
@Composable
private fun DesignScope.GoodCard(id: String, x: Float, y: Float, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, 116f, 115f).clip(RoundedCornerShape(d(27f))).background(Color.White)
            .border(if (selected) d(4f) else d(0f), MarketLime, RoundedCornerShape(d(27f)))
            .clickable(onClick = onClick)
    )
    Art(goodArt(id), x + 8f, y + 12f, 100f, 90f, ContentScale.Fit)
    Hit(x, y, 116f, 115f, onClick)
    Box(
        Modifier.at(x + 18f, y + 121f, 80f, 26f).clip(RoundedCornerShape(d(13f))).background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Text("≈ ${Barter.goodValue(id)} 🪙", color = MarketInk, fontSize = fs(13f, false))
    }
}

/** Что Монтик хочет получить — маленькая плашка под рюкзаком. */
@Composable
private fun DesignScope.WantChip(id: String, x: Float, y: Float) {
    Box(
        Modifier.at(x, y, 200f, 40f).clip(RoundedCornerShape(d(20f))).background(Color.White.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Хочу: ${goodEmoji(id)} ${goodName(id)} (≈${Barter.goodValue(id)})",
            color = MarketInk,
            fontSize = fs(13f, false),
            textAlign = TextAlign.Center
        )
    }
}

// ───────────────────────── Инвентарь (кадр 63) ─────────────────────────

private val SLOTS = listOf(63f to 324f, 219f to 324f, 61f to 457f, 217f to 457f, 63f to 590f, 219f to 590f)

@Composable
private fun DesignScope.Inventory(
    vm: GameViewModel,
    want: String,
    offer: Map<String, Int>,
    page: Int,
    onPage: (Int) -> Unit,
    onChange: (Map<String, Int>) -> Unit,
    onClose: () -> Unit,
    onPropose: () -> Unit
) {
    val items = Barter.inventory(vm.state)
    val pages = (items.size + SLOTS.size - 1) / SLOTS.size
    val shown = items.drop(page * SLOTS.size).take(SLOTS.size)
    val value = Barter.offerValue(vm.state, offer)
    val target = Barter.goodValue(want)

    // Подсказка сверху: сколько стоит товар енота и сколько набрано.
    Box(
        Modifier.at(28f, 150f, 355f, 80f).clip(RoundedCornerShape(d(24f))).background(Color.White.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Хочу: ${goodEmoji(want)} ${goodName(want)} ≈ $target 🪙\n" +
                "Моё предложение: ≈ $value 🪙 · честно — от ${target * Barter.FAIR_MIN_PERCENT / 100} до ${target * Barter.FAIR_MAX_PERCENT / 100}",
            color = MarketInk,
            fontSize = fs(14f, false),
            lineHeight = fs(18f, false),
            textAlign = TextAlign.Center
        )
    }

    // Белая панель с лаймовой рамкой 10 и плашкой «инвентарь».
    Box(
        Modifier.at(28f, 245f, 355f, 555f).clip(RoundedCornerShape(d(87f))).background(Color.White)
            .border(d(10f), MarketLime, RoundedCornerShape(d(87f)))
    )
    Box(
        Modifier.at(120f, 253f, 172f, 41f).clip(RoundedCornerShape(d(19f))).background(MarketLime),
        contentAlignment = Alignment.Center
    ) {
        Text("инвентарь", color = Color.White, fontSize = fs(20f, false))
    }

    SLOTS.forEachIndexed { i, (x, y) ->
        val item = shown.getOrNull(i)
        val chosen = item?.let { offer[it.id] } ?: 0
        Box(
            Modifier.at(x, y, 129f, 126f).clip(RoundedCornerShape(d(48f))).background(Color.White)
                .border(if (chosen > 0) d(4f) else d(2f), if (chosen > 0) MarketLime else Color.Black, RoundedCornerShape(d(48f)))
                .then(
                    if (item != null) Modifier.clickable {
                        if (chosen < item.count) onChange(offer + (item.id to chosen + 1))
                    } else Modifier
                )
        ) {
            if (item != null) {
                Column(
                    Modifier.fillMaxSize().padding(top = d(8f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (item.id == Barter.SERVICE_ID) {
                        Text(item.emoji, fontSize = fs(40f, false))
                    } else {
                        ArtImage(groceryArt(item.id), Modifier.padding(top = d(2f)).then(Modifier.requiredWidth(d(60f))).heightIn(max = d(52f)), ContentScale.Fit) {
                            Text(item.emoji, fontSize = fs(38f, false))
                        }
                    }
                    Text(
                        item.title,
                        color = MarketInk,
                        fontSize = fs(11f, false),
                        lineHeight = fs(12f, false),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = d(8f))
                    )
                    Text("≈${item.value} 🪙 · есть ${item.count}", color = Color(0xFF4A7A2A), fontSize = fs(10f, false))
                }
            }
        }
        // Сколько положено в предложение и кнопка «−».
        if (item != null && chosen > 0) {
            Box(
                Modifier.at(x + 96f, y - 6f, 36f, 36f).clip(CircleShape).background(MarketLime),
                contentAlignment = Alignment.Center
            ) {
                Text("×$chosen", color = Color.White, fontSize = fs(13f, false), fontWeight = FontWeight.Bold)
            }
            Box(
                Modifier.at(x - 4f, y - 6f, 36f, 36f).clip(CircleShape).background(MarketRed)
                    .clickable {
                        val n = chosen - 1
                        onChange(if (n <= 0) offer - item.id else offer + (item.id to n))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = Color.White, fontSize = fs(20f, false), fontWeight = FontWeight.Bold)
            }
        }
    }

    // Листать, если вещей больше шести.
    if (pages > 1) {
        if (page > 0) PageArrow("‹", 34f, 505f) { onPage(page - 1) }
        if (page < pages - 1) PageArrow("›", 346f, 505f) { onPage(page + 1) }
    }

    // «Закрыть» (красная, как в кадре 63) и «Предложить».
    DPill("Закрыть", 121f, 723f, 170f, 55f, fill = MarketRed) { onClose() }
    DPill("Предложить обмен", 76f, 815f, 260f, 58f, fill = MarketLime, enabled = offer.isNotEmpty()) { onPropose() }
}

@Composable
private fun DesignScope.PageArrow(text: String, x: Float, y: Float, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, 32f, 48f).clip(RoundedCornerShape(d(16f))).background(MarketLime).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = fs(26f, false), fontWeight = FontWeight.Bold)
    }
}
