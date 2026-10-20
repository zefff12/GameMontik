package ru.montik.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.BankWork
import ru.montik.app.game.BusinessEngine
import ru.montik.app.game.FinTask
import ru.montik.app.game.Goals
import ru.montik.app.game.Housing
import ru.montik.app.game.Job
import ru.montik.app.game.Life
import ru.montik.app.game.Rating
import ru.montik.app.game.Rules
import ru.montik.app.game.ShopWork
import ru.montik.app.game.SleepPlace
import ru.montik.app.game.Tasks

/*
 * Экраны нового макета: «Работа» (Android Compact - 98), «Копилка» (Frame 62), «Задания» (Frame 59),
 * «Жильё» (94 и 90), «Банк» (97), «Пора спать» (99). Кадр — фон, всё живое поверх, суммы из игры.
 */

/** Часть кадра в координатах макета без растяжения на поля экрана. */
@Composable
private fun DesignScope.Part(name: String, x: Float, y: Float, w: Float, h: Float, modifier: Modifier = Modifier) {
    // Модификатор at растягивает элементы во всю ширину кадра на поля экрана — для карточек это не нужно.
    // Рамка ставится точно по макету (даже если картинка шире кадра) — без растяжения на поля.
    Box(
        Modifier.absoluteOffset(d(x), d(y))
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(d(w), d(h))
            .then(modifier)
    ) { ArtImage(name, Modifier.fillMaxSize(), ContentScale.FillBounds) {} }
}

/** Карточка въезжает справа по очереди. */
@Composable
internal fun rememberSlide(index: Int): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(70L * index)
        p.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 260f))
    }
    return p
}

internal fun Modifier.slide(p: Animatable<Float, *>): Modifier = graphicsLayer {
    translationX = (1f - p.value) * size.width * 0.6f
    alpha = p.value.coerceIn(0f, 1f)
}

private fun shiftsWordN(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "смен"
        m10 == 1 -> "смену"
        m10 in 2..4 -> "смены"
        else -> "смен"
    }
}

private fun daysWordN(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "дней"
        m10 == 1 -> "день"
        m10 in 2..4 -> "дня"
        else -> "дней"
    }
}

// ───────────────────────── Работа ─────────────────────────

@Composable
fun NewWorkList(vm: GameViewModel) {
    val s = vm.state
    val canWork = vm.workBlocker == null
    val storeOpen = ShopWork.unlocked(s)
    val bankOpen = BankWork.unlocked(s)
    val businessOpen = BusinessEngine.canStart(s)
    val coins = rememberCountUp(s.coins)
    DesignCanvas {
        // «Работа» из нового макета: фон, заголовок, стрелка назад и монеты в синей пилюле.
        FrameArt("fg4_work_bg", -47f, -52f, 459f, 1022f)
        val cards = listOf(
            Triple("fg4_work_c1", -1f to 200f, 413f to 143f),
            Triple("fg4_work_c2", -1f to 343f, 414f to 144f),
            Triple("fg4_work_c3", -1f to 487f, 414f to 143f),
            Triple(if (businessOpen) "fg3_work_biz_open" else "fg4_work_c4", -3f to 630f, 418f to 145f)
        )
        cards.forEachIndexed { i, (art, pos, size) ->
            val p = rememberSlide(i)
            Part(art, pos.first, pos.second, size.first, size.second, Modifier.slide(p))
        }
        Part("fg4_work_title", 70f, 25f, 225f, 75f)
        Part("fg4_back", 15f, 31f, 70f, 69f)
        PressHit(20f, 36f, 60f, 60f, 30f) { vm.back() }
        CoinPill(coins, 282f, 41f)

        // Ставки за смену — из правил игры, на месте нарисованных.
        val ink = Color(0xFF0F2A63)
        WorkRate("+${Job.HELPER.base}", 224f, 288f, ink)
        WorkRate("+${ShopWork.rank(s).base}", 220f, 434f, ink)
        WorkRate("+${BankWork.base(s)}", 212f, 579f, ink)

        // Карточки нажимаются целиком (и зелёная «>»). Пока работа закрыта, Монтик объясняет почему.
        PressHit(8f, 214f, 396f, 116f, 24f, enabled = canWork) { vm.startShift(Job.HELPER) }
        PressHit(8f, 357f, 396f, 116f, 24f, enabled = canWork) {
            if (storeOpen) vm.goTo(Screen.Store)
            else vm.say("Работа в магазине откроется через ${ShopWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWordN(ShopWork.UNLOCK_SHIFTS - s.shifts)} у мастера.")
        }
        PressHit(8f, 501f, 396f, 116f, 24f, enabled = canWork) {
            if (bankOpen) vm.goTo(Screen.BankJob)
            else vm.say("Работа в банке откроется через ${BankWork.UNLOCK_SHIFTS - s.shifts} ${shiftsWordN(BankWork.UNLOCK_SHIFTS - s.shifts)}.")
        }
        PressHit(8f, 644f, 396f, 118f, 24f) {
            if (businessOpen) vm.goTo(Screen.Business) else vm.say("Бизнес можно открыть, когда накопишь 20 000 монет.")
        }
    }
    if (!canWork) NoWorkPopup(onShop = { vm.goTo(Screen.Grocery) }, onSleep = { vm.goTo(Screen.Sleep) }, onClose = { vm.back() })
}

/** Ставка на карточке работы: левый край [x], середина строки [cy]. */
@Composable
private fun DesignScope.WorkRate(text: String, x: Float, cy: Float, color: Color) {
    Box(Modifier.at(x, cy - 14f, 70f, 28f), contentAlignment = Alignment.CenterStart) {
        Text(text, color = color, fontSize = fs(20f, false), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
    }
}

/**
 * Монеты в синей пилюле из макета (image 718 + монета 47×47): монета слева, число — ровно по центру
 * оставшейся части пилюли. [x], [y] — левый верхний угол монеты.
 */
@Composable
fun DesignScope.CoinPill(coins: Int, x: Float, y: Float) {
    Box(
        Modifier.absoluteOffset(d(x + 9f), d(y + 1f))
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(d(111f), d(46f))
    ) { ArtImage("fg4_coinpill", Modifier.fillMaxSize(), ContentScale.FillBounds) {} }
    Box(
        Modifier.absoluteOffset(d(x), d(y))
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(d(47f), d(47f))
    ) { ArtImage("fg4_coin", Modifier.fillMaxSize(), ContentScale.Fit) {} }
    val text = spaced(coins)
    Box(
        Modifier.absoluteOffset(d(x + 47f), d(y + 1f))
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(d(70f), d(46f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = Color(0xFF0F2A63), fontSize = fs(if (text.length > 5) 16f else 20f, false),
            fontWeight = FontWeight.Black, maxLines = 1, softWrap = false
        )
    }
}

// ───────────────────────── Копилка ─────────────────────────

@Composable
fun NewPiggyScreen(vm: GameViewModel, onWithdraw: () -> Unit) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val fb = rememberFeedback()
    var balance by rememberSaveable { mutableStateOf(false) }
    // Подсказка с ценой цели: какая строка и когда показана (через 2 с исчезает).
    var tip by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(tip) {
        if (tip != null) {
            kotlinx.coroutines.delay(2200)
            tip = null
        }
    }
    DesignCanvas {
        // «Копилка» из нового макета: комната (image 488), шапка с копилкой (image 489) и цели (image 594).
        FrameArt("fg3_piggy_bg", 0f, 0f, 418f, 917f)
        Part("fg3_piggy_top", 45f, 21f, 305f, 343f)
        PressHit(59f, 48f, 40f, 38f, 19f) { vm.back() }
        LiveText("$coins", 296f, 55f, 44f, 24f, Color(0xFFFDFDFE), 19f)
        // Нажми на копилку — окно «Моя копилка» с балансом.
        Hit(70f, 100f, 150f, 120f) { fb.tap(); balance = true }

        val adds = listOf(Triple(55f, 69f, 10), Triple(129f, 69f, 20), Triple(201f, 70f, 50))
        for ((x, w, amount) in adds) {
            PressHit(x, 236f, w, 41f, 16f) {
                if (s.coins >= amount) { fb.coin(); vm.piggyIn(amount) }
                else vm.say("В кошельке ${s.coins} ${coinWord(s.coins)} — не хватает, чтобы отложить $amount.")
            }
        }
        PressHit(275f, 236f, 68f, 41f, 16f) {
            if (s.coins > 0) { fb.coin(); vm.piggyIn(s.coins) } else vm.say("Кошелёк пуст — сначала заработай на смене.")
        }
        PressHit(57f, 285f, 278f, 48f, 24f) {
            if (s.piggy > 0) onWithdraw() else vm.say("Копилка пока пустая.")
        }

        Part("fg3_piggy_goals", -104f, 353f, 606f, 353f)
        val rows = listOf(
            Triple("trip_moscow", 363f, 377f), Triple("trip_kazan", 431f, 444f), Triple("trip_spb", 497f, 510f),
            Triple("trip_sochi", 566f, 577f), Triple("console", 632f, 641f)
        )
        for ((id, top, btnY) in rows) {
            val g = Goals.byId(id) ?: continue
            val chosen = g.id == s.goalId
            // Выбранная цель — в золотой рамке.
            if (chosen) {
                Box(
                    Modifier.at(10f, top + 2f, 392f, 64f).clip(RoundedCornerShape(d(22f)))
                        .border(d(3f), Color(0xFFFFC928), RoundedCornerShape(d(22f)))
                )
            }
            val showTip = { tip = g.id to System.currentTimeMillis() }
            Hit(12f, top + 2f, 262f, 62f) { fb.tap(); showTip() }
            PressHit(282f, btnY, 118f, 43f, 21f) {
                showTip()
                val blocker = Goals.blocker(s, g)
                when {
                    chosen && s.piggy >= g.cost -> vm.reachGoal()
                    chosen -> vm.say("Копим на «${g.title}»: ${s.piggy} из ${g.cost}.")
                    blocker == null -> { fb.good(); vm.chooseGoal(g.id) }
                    else -> vm.say(blocker)
                }
            }
            // Маленькая подсказка с ценой над кнопкой «Копить».
            if (tip?.first == g.id) {
                val text = if (chosen) "${s.piggy} / ${g.cost}" else "${g.cost}"
                Box(
                    Modifier.at(168f, btnY + 7f, 108f, 29f).shadow(d(4f), RoundedCornerShape(d(14f)))
                        .clip(RoundedCornerShape(d(14f))).background(Color.White)
                        .border(d(1.5f), Color(0xFFF2B21B), RoundedCornerShape(d(14f))),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtImage("fg2_coin", Modifier.size(d(18f)), ContentScale.Fit) {}
                        Box(Modifier.size(d(4f)))
                        Text(text, color = Color(0xFF14246B), fontSize = fs(15f, false), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
    if (balance) PiggyBalancePopup(s.piggy) { balance = false }
}

/** «Моя копилка» (image 572 + 573): копилка, баланс и «Понятно!». */
@Composable
private fun PiggyBalancePopup(piggy: Int, onClose: () -> Unit) {
    DimPopup(
        "fg3_piggy_pop_clean", 1312f / 1199f,
        listOf(
            PopZone(0.18f, 0.82f, 0.64f, 0.14f, onClose),
            PopZone(0.79f, 0.13f, 0.10f, 0.11f, onClose)
        ),
        onBack = onClose, onScrim = onClose
    ) { w, h ->
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(Modifier.offset(w * 0.375f, h * 0.71f).size(w * 0.42f, h * 0.10f), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$piggy", color = Color(0xFF0E1A33), fontSize = with(density) { (h * 0.075f).toSp() }, fontWeight = FontWeight.Black, maxLines = 1)
                Box(Modifier.size(w * 0.015f))
                Text(coinWord(piggy), color = Color(0xFF55657E), fontSize = with(density) { (h * 0.045f).toSp() }, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ───────────────────────── Задания ─────────────────────────

/** Карточки заданий в кадре Frame 59: id → рамка (x, y, w, h). */
private val TASK_CARDS = listOf(
    "t_split_week" to floatArrayOf(32f, 340f, 168f, 108f),
    "t_split_salary" to floatArrayOf(220f, 340f, 168f, 108f),
    "t_count_goal" to floatArrayOf(32f, 504f, 116f, 96f),
    "t_count_cushion" to floatArrayOf(160f, 504f, 112f, 96f),
    "t_pick_piggy" to floatArrayOf(280f, 504f, 108f, 96f),
    "t_count_change" to floatArrayOf(32f, 656f, 168f, 68f),
    "t_basket_list" to floatArrayOf(220f, 656f, 168f, 68f),
    "t_pick_ad" to floatArrayOf(32f, 732f, 168f, 72f),
    "t_basket_price" to floatArrayOf(220f, 732f, 168f, 72f)
)

@Composable
fun NewTasksList(vm: GameViewModel, onOpen: (FinTask) -> Unit) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val unlocked = Tasks.unlocked(s)
    DesignCanvas {
        FrameArt("fg4_tasks_bg")
        PressHit(22f, 100f, 46f, 40f, 20f) { vm.back() }
        // Монеты: белая пилюля из макета (image 719), монета слева, число — по центру.
        Part("fg4_tasks_coin", 273f, 85f, 139f, 69f)
        Part("fg4_coin", 286f, 100f, 38f, 38f)
        Box(Modifier.at(326f, 104f, 74f, 30f), contentAlignment = Alignment.Center) {
            Text(spaced(coins), color = Color(0xFF14246B), fontSize = fs(if (coins >= 10000) 15f else 19f, false), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
        }
        TASK_CARDS.forEachIndexed { i, (id, r) ->
            val t = Tasks.byId(id) ?: return@forEachIndexed
            val open = t in unlocked
            val result = s.taskResults[id]
            val (x, y, w, h) = listOf(r[0], r[1], r[2], r[3])
            // Полоска и подпись внизу карточки — настоящее состояние задания.
            val label = when {
                !open -> "скоро"
                result == Rating.GREAT -> "✓ отлично"
                result != null -> "✓ пройдено"
                else -> "+${t.reward}"
            }
            val enter = rememberEnter(MOTION_MS + i * 50)
            Box(Modifier.at(x + 3f, y + h - 21f, w - 6f, 18f).enterPop(enter).clip(RoundedCornerShape(50)).background(Color(0xFFFFFFFF))) {
                val part = when {
                    result == Rating.GREAT -> 1f
                    result != null -> 0.66f
                    else -> 0.04f
                }
                Box(Modifier.fillMaxHeight().fillMaxWidth(0.55f * part).clip(RoundedCornerShape(50)).background(Color(0xFF41C94B)))
            }
            DText(label, x + w * 0.55f, y + h - 19f, w * 0.42f, 10.5f, color = Color(0xFF34466B), align = androidx.compose.ui.text.style.TextAlign.End, softWrap = false, mono = false)
            if (!open) Box(Modifier.at(x, y, w, h).clip(RoundedCornerShape(d(16f))).background(Color(0x77FFFFFF)))
            PressHit(x, y, w, h, 16f) {
                if (open) onOpen(t) else vm.say("Это задание откроется через ${Tasks.all.indexOf(t) - unlocked.size + 1} ${daysWordN(Tasks.all.indexOf(t) - unlocked.size + 1)}.")
            }
        }
    }
}

// ───────────────────────── Жильё ─────────────────────────

@Composable
fun NewHousingScreen(vm: GameViewModel) {
    var detail by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = detail) { detail = false }
    androidx.compose.animation.AnimatedContent(targetState = detail, transitionSpec = { slideStep(targetState) }, label = "house") { d ->
        // Сначала — текущее жильё и уровень, по кнопке «Варианты жилья» — список квартир.
        if (d) NewHousingList(vm) { detail = false } else NewHousingDetail(vm) { detail = true }
    }
}

@Composable
private fun NewHousingList(vm: GameViewModel, onDetail: () -> Unit) {
    val s = vm.state
    DesignCanvas {
        // «Варианты жилья» из нового макета: аренда нарисована на карточке, звёзды с картинки стёрты —
        // вместо них уровень, с которого квартира открывается (замок, пока уровень не дорос).
        FrameArt("fg3_house_bg", 0f, 0f, 412f, 922f)
        Part("fg3_house_head_clean", 35f, 40f, 342f, 117f)
        LiveText("${rememberCountUp(s.coins)}", 331f, 89f, 34f, 24f, Color(0x00000000), 17f, Color(0xFF14246B))
        PressHit(43f, 76f, 46f, 44f, 22f) { onDetail() }
        val cards = listOf(
            Triple(Housing.MODEST, "fg3_house_l1_nostar", floatArrayOf(13f, 146f, 383f, 216f)),
            Triple(Housing.COZY, "fg3_house_l2_nostar", floatArrayOf(12f, 339f, 384f, 216f)),
            Triple(Housing.PENTHOUSE, "fg3_house_l3_nostar", floatArrayOf(15f, 539f, 381f, 218f))
        )
        cards.forEachIndexed { i, (h, art, r) ->
            val p = rememberSlide(i)
            Part(art, r[0], r[1], r[2], r[3], Modifier.slide(p))
            // На месте звезды — уровень, с которого открывается квартира.
            val myLevel = ru.montik.app.game.Progress.levelOf(s.xp)
            val open = myLevel >= h.levelNeeded
            val sx = r[0] + (if (h == Housing.MODEST) 118f else 116f) * r[2] / 384f
            Row(
                Modifier.at(sx, r[1] + 143f * r[3] / 216f, 80f, 36f).slide(p),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (open) "✓" else "🔒", color = if (open) Color(0xFF2E9B2E) else Color(0xFF14246B), fontSize = fs(15f, false), fontWeight = FontWeight.Black)
                Box(Modifier.size(d(3f)))
                Text("${h.levelNeeded} ур.", color = Color(0xFF14246B), fontSize = fs(18f, false), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
            }
            val blocker = Life.moveBlocker(s, h)
            val tap = {
                when {
                    h.level == s.housing -> onDetail()
                    blocker == null -> vm.moveTo(h.level)
                    else -> vm.say(blocker)
                }
            }
            // Зелёная «›» на карточке и сама карточка.
            PressHit(r[0] + r[2] * 0.79f, r[1] + r[3] * 0.78f, r[2] * 0.19f, r[3] * 0.17f, 14f, onClick = tap)
            Hit(r[0], r[1], r[2] * 0.78f, r[3], tap)
        }
    }
}

@Composable
private fun NewHousingDetail(vm: GameViewModel, onList: () -> Unit) {
    val s = vm.state
    val home = s.home
    val coins = rememberCountUp(s.coins)
    DesignCanvas {
        // «Жильё и уровень» (новый макет): текущее жильё, аренда, следующий платёж, уровень Монтика.
        FrameArt("fg3_house2_bg_nostar")
        PressHit(20f, 52f, 48f, 46f, 23f) { vm.back() }
        LiveText("$coins", 350f, 62f, 46f, 26f, Color(0xFFFDFCF8), 18f)
        if (home.level != 1) {
            LiveText("Уровень ${home.level}", 40f, 137f, 90f, 24f, Color(0xFFD9F4D6), 13f)
            LiveText(home.title, 36f, 170f, 142f, 54f, Color(0xFFF6FBF2), 19f, align = Alignment.CenterStart, maxLines = 2)
            Box(Modifier.at(181f, 127f, 205f, 122f).clip(RoundedCornerShape(d(16f)))) {
                ArtImage(if (home.level == 2) "fg_room2_day" else "fg_room3_day", Modifier.fillMaxSize(), ContentScale.Crop) {}
            }
        }
        LiveText("${home.rent}", 333f, 271f, 44f, 26f, Color(0xFFFDF8EA), 17f, align = Alignment.CenterStart)
        val days = Life.daysUntilRent(s)
        LiveText(
            if (s.rentDebt > 0) "долг ${s.rentDebt}" else "через $days ${daysWordN(days)}",
            103f, 339f, 120f, 22f, Color(0xFFE8F4FB), 12.5f,
            if (s.rentDebt > 0) Color(0xFFD84A3C) else Color(0xFF7A8AA3), align = Alignment.CenterStart
        )
        // Строка уровня: значок с номером уровня, подпись и до какой квартиры осталось.
        val level = ru.montik.app.game.Progress.levelOf(s.xp)
        Box(
            Modifier.at(40f, 368f, 42f, 42f).clip(RoundedCornerShape(d(12f)))
                .background(Brush.verticalGradient(listOf(Color(0xFF7ED957), Color(0xFF2E9B2E)))),
            contentAlignment = Alignment.Center
        ) { Text("$level", color = Color.White, fontSize = fs(20f, false), fontWeight = FontWeight.Black) }
        Box(Modifier.at(98f, 380f, 196f, 30f), contentAlignment = Alignment.CenterStart) {
            Text("Уровень Монтика", color = Color(0xFF14246B), fontSize = fs(15f, false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        val next = Life.nextHousing(s)
        Box(
            Modifier.at(298f, 375f, 68f, 36f).clip(RoundedCornerShape(d(18f))).background(Color(0xFFFFF4D9)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when { next == null -> "макс."; level >= next.levelNeeded -> "переезд!"; else -> "до ${next.levelNeeded}" }, color = Color(0xFF14246B),
                fontSize = fs(14f, false), fontWeight = FontWeight.Black, maxLines = 1
            )
        }
        // Платёж: если есть долг — нажать на строку «Следующий платёж», чтобы погасить.
        Hit(36f, 315f, 350f, 56f) {
            when {
                s.rentDebt > 0 -> vm.payRentDebt()
                s.rentPaidAhead -> vm.say("Аренда уже внесена заранее.")
                Life.rentCountdown(s) && s.coins >= home.rent -> vm.payRentAhead()
                else -> vm.say("Следующий платёж — через $days ${daysWordN(days)}, ${home.rent} монет.")
            }
        }
        // «Варианты жилья» (image 622).
        Part("fg3_house2_i622", 34f, 591f, 344f, 164f)
        PressHit(52f, 616f, 310f, 110f, 40f) { onList() }
    }
}

// ───────────────────────── Банк ─────────────────────────

@Composable
fun NewBankScreen(vm: GameViewModel, onCredits: () -> Unit) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val deposit = rememberCountUp(s.deposit)
    val fb = rememberFeedback()
    DesignCanvas {
        // Окно «Банк» из нового макета. Строка про проценты исправлена под правило игры: +5% в неделю.
        FrameArt("fg3_bank_clean", 0f, -1f, 412f, 918f)
        PressHit(16f, 62f, 58f, 58f, 29f) { vm.back() }
        LiveText("$coins", 344f, 79f, 50f, 28f, Color(0xFFFDF5E4), 19f)
        DText(
            "Каждую неделю банк добавляет 5% к твоему вкладу.",
            40f, 280f, 132f, 14f, bold = false, color = Color(0xFF253A63), mono = false, lineHeight = 1.2f
        )
        LiveText("$deposit", 80f, 459f, 90f, 28f, Color(0xFFFDFBF1), 21f, align = Alignment.CenterStart)
        if (s.deposit <= 0) {
            LiveText(
                "Положи от ${Rules.MIN_DEPOSIT} монет — через неделю их станет больше!",
                40f, 502f, 226f, 44f, Color(0xFFEEF6E3), 13.5f, Color(0xFF253A63), align = Alignment.CenterStart, maxLines = 2,
                weight = FontWeight.Normal
            )
        } else {
            val days = ru.montik.app.game.GameEngine.daysToInterest(s)
            LiveText(
                "Через $days ${daysWordN(days)} банк добавит ${ru.montik.app.game.GameEngine.weeklyInterest(s)} ${coinWord(ru.montik.app.game.GameEngine.weeklyInterest(s)).replace("монета", "монету")}",
                40f, 502f, 226f, 44f, Color(0xFFEEF6E3), 13.5f, Color(0xFF253A63), align = Alignment.CenterStart, maxLines = 2,
                weight = FontWeight.Normal
            )
        }
        val blockedByDebt = s.debt > 0 || s.credits.isNotEmpty()
        fun put(amount: Int) {
            when {
                blockedByDebt -> vm.say("Сначала верни кредит: держать долг и вклад одновременно невыгодно.")
                amount < Rules.MIN_DEPOSIT || s.coins < amount -> vm.say("На вклад кладут от ${Rules.MIN_DEPOSIT} монет, а в кошельке ${s.coins}.")
                else -> { fb.coin(); vm.bankDeposit(amount) }
            }
        }
        PressHit(34f, 562f, 110f, 46f, 22f) { put(10) }
        PressHit(152f, 562f, 109f, 46f, 22f) { put(50) }
        PressHit(270f, 562f, 109f, 46f, 22f) { put(s.coins) }
        // «Забрать вклад целиком»: серая, пока вклада нет; зелёная, когда есть что забрать.
        if (s.deposit > 0) LiveText("Забрать вклад целиком", 34f, 620f, 345f, 48f, Color(0xFF55B946), 16f, Color.White, radius = 24f)
        PressHit(34f, 620f, 345f, 48f, 24f) {
            if (s.deposit > 0) { fb.coin(); vm.bankWithdraw(s.deposit) } else vm.say("На вкладе пока ничего нет.")
        }
        // «Кредиты ›».
        PressHit(16f, 697f, 384f, 120f, 24f) { onCredits() }
    }
}

// ───────────────────────── Пора спать ─────────────────────────

@Composable
fun NewSleepScreen(vm: GameViewModel) {
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(Color(0xFFF2F9E8))) }) {
        // Кадр «Будильник» из нового макета: фон и кнопка «Лечь спать».
        FrameArt("fg3_alarm_bg", 0f, -10f, 427f, 938f)
        Part("fg3_alarm_btn", 15f, 666f, 397f, 134f)
        PressHit(26f, 60f, 54f, 50f, 26f) { vm.back() }
        PressHit(48f, 690f, 330f, 92f, 46f) { vm.goToBed(SleepPlace.CABIN) }
    }
}
