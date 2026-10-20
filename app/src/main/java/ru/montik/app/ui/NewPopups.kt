package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.montik.app.GameViewModel
import ru.montik.app.ScenarioResultUi
import ru.montik.app.ShiftUi
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medal
import ru.montik.app.game.Scenario
import ru.montik.app.game.Tip

/*
 * Плашки-уведомления из нового макета. Картинка плашки показывается как есть — без наложенного текста;
 * под ней основной экран затемняется (как на рисунке «Жильё и уровень»). Плашка плавно проявляется.
 * Нажимаемые зоны — нарисованные в картинке кнопки (координаты — доли картинки).
 */

/** Зона нажатия в долях картинки плашки. */
class PopZone(val x: Float, val y: Float, val w: Float, val h: Float, val onClick: () -> Unit)

/**
 * Плашка с затемнением. [aspect] — ширина/высота картинки. [onScrim] — что делать при нажатии
 * мимо плашки (null — ничего).
 */
@Composable
fun DimPopup(
    art: String,
    aspect: Float,
    zones: List<PopZone>,
    onBack: () -> Unit,
    maxWidth: Dp = 400.dp,
    onScrim: (() -> Unit)? = null,
    overlay: @Composable (Dp, Dp) -> Unit = { _, _ -> }
) {
    BackHandler(enabled = true) { onBack() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onScrim?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .aspectRatio(aspect)
                .graphicsLayer {
                    val v = appear.value
                    scaleX = 0.94f + 0.06f * v
                    scaleY = 0.94f + 0.06f * v
                }
        ) {
            ArtImage(art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
            val w = maxWidth
            val h = maxHeight
            for (z in zones) {
                Box(
                    Modifier
                        .offset(w * z.x, h * z.y)
                        .size(w * z.w, h * z.h)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = z.onClick)
                )
            }
            overlay(w, h)
        }
    }
}

/** Новый уровень: у каждого из 20 уровней своя плашка из макета (fg4_lvl_2 … fg4_lvl_20). */
@Composable
fun NewLevelPopup(vm: GameViewModel, level: Int) {
    val fb = rememberFeedback()
    LaunchedEffect(level) { fb.fanfare() }
    val n = level.coerceIn(2, ru.montik.app.game.Progress.MAX_LEVEL)
    Box {
        DimPopup(
            "fg4_lvl_$n", 1536f / 1024f,
            listOf(PopZone(0f, 0f, 1f, 1f) { vm.markLevelSeen() }),
            onBack = { vm.markLevelSeen() },
            onScrim = { vm.markLevelSeen() }
        )
        Confetti(level)
    }
}

/** Новая медаль: у каждой медали своя картинка в стиле «Путешественника» (fg4_medal_<id>). */
@Composable
fun NewMedalPopup(medal: Medal, onDismiss: () -> Unit) {
    val art = if (medal.id == "first_pay") "fg3_p_medal" else "fg4_medal_${medal.id.lowercase()}"
    if (!rememberHasArt(art)) {
        MedalDialog(medal, onDismiss)
        return
    }
    val fb = rememberFeedback()
    LaunchedEffect(medal.id) { fb.fanfare() }
    Box {
        DimPopup(
            art, 1080f / 1456f,
            listOf(PopZone(0.08f, 0.77f, 0.84f, 0.18f, onDismiss)),
            onBack = onDismiss, maxWidth = 320.dp, onScrim = onDismiss
        )
        Confetti(medal.id.hashCode())
    }
}

/** «Монтик объясняет»: «Сон и силы» (image 557) и «Налог 13%» (image 594). Другие уроки — карточкой. */
@Composable
fun NewLessonPopup(lesson: Lesson, onDismiss: () -> Unit) {
    when (lesson) {
        Lesson.TAX -> DimPopup(
            "fg3_p_594", 1313f / 1198f,
            listOf(PopZone(0.54f, 0.77f, 0.42f, 0.17f, onDismiss)),
            onBack = onDismiss
        )
        Lesson.ENERGY -> DimPopup(
            "fg3_p_lesson_sleep", 1277f / 1232f,
            listOf(PopZone(0.13f, 0.77f, 0.76f, 0.19f, onDismiss)),
            onBack = onDismiss
        )
        // Новые плашки «Монтик объясняет» из макета.
        Lesson.NEEDS, Lesson.DIARY, Lesson.CHANGE, Lesson.DEPOSIT, Lesson.PLAN -> DimPopup(
            when (lesson) {
                Lesson.NEEDS -> "fg5_l_needs"
                Lesson.DIARY -> "fg5_l_diary"
                Lesson.CHANGE -> "fg5_l_change"
                Lesson.DEPOSIT -> "fg5_l_deposit"
                else -> "fg5_l_plan"
            },
            1313f / 1198f,
            listOf(PopZone(0.04f, 0.76f, 0.92f, 0.23f, onDismiss)),
            onBack = onDismiss, onScrim = onDismiss
        )
        Lesson.WORK, Lesson.GOAL, Lesson.CUSHION, Lesson.ADS -> DimPopup(
            when (lesson) {
                Lesson.WORK -> "fg4_l_work"
                Lesson.GOAL -> "fg4_l_goal"
                Lesson.CUSHION -> "fg4_l_cushion"
                else -> "fg4_l_ads"
            },
            1313f / 1198f,
            listOf(PopZone(0.50f, 0.78f, 0.48f, 0.18f, onDismiss)),
            onBack = onDismiss, onScrim = onDismiss
        )
        else -> LessonDialog(lesson, onDismiss)
    }
}

/**
 * Расчётный листок из макета: звёзды (1–3), должность, «Начислено», «Налог 13%», «На руки».
 * Суммы и должность с картинки стёрты и пишутся игрой — для любой смены.
 */
@Composable
fun NewPayslipPopup(ui: ShiftUi, onDismiss: () -> Unit) {
    val slip = ui.slip
    val stars = slip.stars ?: when (ui.correct) {
        true -> 3
        null -> 2
        false -> 1
    }
    val fb = rememberFeedback()
    LaunchedEffect(slip) { fb.coin() }
    DimPopup(
        "fg4_slip${stars.coerceIn(1, 3)}", 1118f / 1407f,
        listOf(PopZone(0.08f, 0.78f, 0.84f, 0.16f, onDismiss)),
        onBack = onDismiss, maxWidth = 340.dp
    ) { w, h ->
        val density = LocalDensity.current
        fun sp(v: Float) = with(density) { (h * v).toSp() }
        Box(Modifier.offset(w * (318f / 1118f), h * (557f / 1407f)).size(w * (690f / 1118f), h * (120f / 1407f)), contentAlignment = Alignment.CenterStart) {
            Text(
                slip.jobTitle, color = Color(0xFF0E2A1A), fontSize = sp(0.044f), fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        val rows = listOf(
            Triple("${slip.gross}", 776f, Color(0xFF1767F2)),
            Triple("−${slip.tax}", 886f, Color(0xFFE81E1E)),
            Triple("${slip.net}", 1003f, Color(0xFF1C6E22))
        )
        rows.forEachIndexed { i, (text, cy, color) ->
            val big = i == 2
            val boxH = if (big) 100f else 70f
            Box(
                Modifier.offset(w * (560f / 1118f), h * ((cy - boxH / 2f) / 1407f)).size(w * (325f / 1118f), h * (boxH / 1407f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(text, color = color, fontSize = sp(if (big) 0.080f else 0.058f), fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

/** Совет от Лобачевского (image 487). */
@Composable
fun NewTipPopup(tip: Tip, onDismiss: () -> Unit) {
    DimPopup(
        "fg3_p_tip", 1315f / 1197f,
        listOf(PopZone(0.28f, 0.85f, 0.65f, 0.14f, onDismiss)),
        onBack = onDismiss, onScrim = onDismiss
    )
}

/** «Первый кошелёк Монтика» (image 572 + 573): три варианта — кнопки из макета. */
@Composable
fun NewWalletPopup(scenario: Scenario, onChoose: (Int) -> Unit) {
    // Верх окна (1200×1311) и кнопки (2089×753) — две картинки одна под другой.
    val topH = 1311f
    val btnH = 753f * 1200f / 2089f
    val total = topH - 60f + btnH
    BackHandler(enabled = true) { }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(260)) }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp).widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1200f / total)) {
            val w = maxWidth
            val h = maxHeight
            Box(Modifier.offset(0.dp, 0.dp).size(w, h * (topH / total))) {
                ArtImage("fg3_p_wallet_top", Modifier.fillMaxSize(), ContentScale.FillBounds) {}
            }
            val by = (topH - 60f) / total
            val bh = btnH / total
            Box(Modifier.offset(0.dp, h * by).size(w, h * bh)) {
                ArtImage("fg3_p_wallet_btns", Modifier.fillMaxSize(), ContentScale.FillBounds) {}
            }
            listOf(0.0f, 0.335f, 0.67f).forEachIndexed { i, r ->
                if (i < scenario.choices.size) {
                    Box(
                        Modifier.offset(0.dp, h * (by + bh * r)).size(w, h * bh * 0.33f)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChoose(i) }
                    )
                }
            }
        }
    }
}

/** Итог покупки в первом кошельке — плашки «Купить обед / рюкзак / игрушку» (image 591–593). */
@Composable
fun NewWalletResult(ui: ScenarioResultUi, coins: Int, onDismiss: () -> Unit) {
    val index = ui.scenario.choices.indexOf(ui.choice)
    val art = when (index) {
        0 -> "fg3_res_lunch"
        1 -> "fg3_res_toy"
        else -> "fg3_res_bag"
    }
    val fb = rememberFeedback()
    LaunchedEffect(ui) { fb.coin() }
    DimPopup(
        art, 0.81f,
        listOf(PopZone(0.40f, 0.84f, 0.58f, 0.15f, onDismiss)),
        onBack = onDismiss, maxWidth = 330.dp, onScrim = onDismiss
    )
}

/** «Сегодня работать нельзя» (image 400): в магазин за едой или поспать. */
@Composable
fun NoWorkPopup(onShop: () -> Unit, onSleep: () -> Unit, onClose: () -> Unit) {
    DimPopup(
        "fg3_p_nowork", 1671f / 941f,
        listOf(
            PopZone(0.0f, 0.70f, 0.49f, 0.26f, onShop),
            PopZone(0.50f, 0.70f, 0.48f, 0.26f, onSleep)
        ),
        onBack = onClose, onScrim = onClose
    )
}

/** «Звёзды нужны для улучшения твоего уровня» (image 615) — когда для переезда не хватает звёзд. */
@Composable
fun StarsPopup(onClose: () -> Unit) {
    DimPopup("fg3_house_stars_pop", 1907f / 825f, listOf(PopZone(0f, 0f, 1f, 1f, onClose)), onBack = onClose, onScrim = onClose)
}

/** «1 монета», «2 монеты», «5 монет». */
fun coinWord(n: Int): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> "монет"
        m10 == 1 -> "монета"
        m10 in 2..4 -> "монеты"
        else -> "монет"
    }
}
