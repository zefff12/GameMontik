package ru.montik.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.montik.app.GameViewModel
import ru.montik.app.R
import ru.montik.app.game.Keepsakes
import ru.montik.app.game.GameState
import ru.montik.app.game.Progress
import ru.montik.app.game.Tip
import kotlin.math.sin
import kotlin.random.Random

/*
 * То, что делает игру живой:
 *   звуки и вибрация (Sfx, Feedback), конфетти и летящие монетки;
 *   шкала «Имя / Уровень» — слой Group 15 из макета;
 *   карточка «Совет от Лобачевского» — слой Group 14 из макета (лампочка и портрет — fg_tip_*);
 *   реплики Монтика в облачке, поздравление с новым уровнем, сообщение от взрослого,
 *   магниты из поездок на кухне и вещи из копилки в комнате.
 * Там, где макета нет, всё собрано из тех же цветов и форм, что и в Figma:
 * светло-зелёные карточки #DAF1DA со скруглением 20, белые пилюли, тёмно-зелёный текст #0D5049.
 */

// Цвета из макета (Group 14, Group 15, концепты экранов).
val TipGreen = Color(0xFFDAF1DA)
val PillWhite = Color(0xFFF9FDF9)
val NameInk = Color(0xFF0D5049)
val LevelGreen = Color(0xFF4AAD6F)
val BarGreen = Color(0xFFD8F1D9)
private val StarGold = Color(0xFFFFC928)

// ───────────────────────── Звуки и вибрация ─────────────────────────

/** Короткие звуки игры (res/raw). Громкость — системная громкость медиа; выключаются в разделе для взрослых. */
object Sfx {
    val BEEP = R.raw.sfx_beep
    val COIN = R.raw.sfx_coin
    val SUCCESS = R.raw.sfx_success
    val ERROR = R.raw.sfx_error
    val POP = R.raw.sfx_pop
    val FANFARE = R.raw.sfx_fanfare

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Int, Int>()

    /** Звук включён (зеркало настройки GameState.soundOn). */
    @Volatile var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        for (res in listOf(BEEP, COIN, SUCCESS, ERROR, POP, FANFARE)) ids[res] = p.load(context, res, 1)
        pool = p
    }

    fun play(res: Int, volume: Float = 0.8f) {
        if (!enabled) return
        val id = ids[res] ?: return
        pool?.play(id, volume, volume, 1, 0, 1f)
    }
}

/** Звук + лёгкая вибрация (без разрешений: встроенный отклик экрана). */
class Feedback(private val haptic: HapticFeedback) {
    fun scan() {
        Sfx.play(Sfx.BEEP)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun tap() {
        Sfx.play(Sfx.POP, 0.6f)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun good() {
        Sfx.play(Sfx.SUCCESS)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun bad() {
        Sfx.play(Sfx.ERROR)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun coin() = Sfx.play(Sfx.COIN, 0.7f)

    fun fanfare() {
        Sfx.play(Sfx.FANFARE)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberFeedback(): Feedback {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { Feedback(haptic) }
}

// ───────────────────────── Конфетти ─────────────────────────

private val CONFETTI_COLORS = listOf(
    Color(0xFF4AAD6F), Color(0xFF8AD742), StarGold, Color(0xFFFF7EC0), Color(0xFF2BA8F0), Color(0xFFC1FA86), Color(0xFFFF8A3D)
)

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/** Конфетти на весь экран: сыплется, когда меняется [trigger] (0 — не сыплется). Нажатия не перехватывает. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier.fillMaxSize(), count: Int = 90, durationMs: Long = 2600) {
    if (trigger == 0) return
    val pieces = remember(trigger) {
        val rnd = Random(trigger * 31 + 7)
        List(count) {
            Piece(
                x = rnd.nextFloat(),
                vx = (rnd.nextFloat() - 0.5f) * 0.35f,
                vy = 0.25f + rnd.nextFloat() * 0.45f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
                color = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)],
                w = 8f + rnd.nextFloat() * 8f,
                h = 5f + rnd.nextFloat() * 6f
            )
        }
    }
    var elapsed by remember(trigger) { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsed = (now - start) / 1_000_000
            if (elapsed > durationMs) break
        }
    }
    if (elapsed > durationMs) return
    Canvas(modifier) {
        val t = elapsed / 1000f
        val fade = if (elapsed > durationMs - 500) (durationMs - elapsed) / 500f else 1f
        for (p in pieces) {
            val x = (p.x + p.vx * t) * size.width
            val y = (-0.08f + p.vy * t + 0.35f * t * t) * size.height
            if (y > size.height + 20f) continue
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    p.color.copy(alpha = fade.coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.w, y - p.h),
                    size = Size(p.w * 2f, p.h * 2f)
                )
            }
        }
    }
}

// ───────────────────────── Летящие монетки ─────────────────────────

/** Монетки летят из точки ([fromX], [fromY]) в кошелёк ([toX], [toY]) — при каждом новом [trigger]. */
@Composable
fun DesignScope.CoinBurst(trigger: Int, fromX: Float, fromY: Float, toX: Float, toY: Float, coins: Int = 7) {
    if (trigger == 0) return
    val progress = remember(trigger) { List(coins) { Animatable(0f) } }
    LaunchedEffect(trigger) {
        progress.forEachIndexed { i, a ->
            launch {
                delay(i * 70L)
                a.animateTo(1f, tween(650))
            }
        }
    }
    progress.forEachIndexed { i, a ->
        val t = a.value
        // Без return@forEachIndexed: ранний выход из лямбды с composable-вызовами надёжнее заменить условием.
        if (t > 0f && t < 1f) {
            // Дуга: монетки взлетают вверх и чуть в сторону.
            val spread = (i - coins / 2f) * 14f
            val x = fromX + (toX - fromX) * t + spread * sin(t * Math.PI).toFloat()
            val y = fromY + (toY - fromY) * t - 90f * sin(t * Math.PI).toFloat()
            Box(Modifier.at(x - 13f, y - 13f, 26f, 26f).graphicsLayer { rotationY = t * 540f }) {
                CoinIcon(Modifier.fillMaxSize())
            }
        }
    }
}

// ───────────────────────── Шкала «Имя / Уровень» (Group 15) ─────────────────────────

/**
 * Плашка героя из макета: круг с Монтиком, имя, «Уровень N» и зелёная шкала опыта.
 * Координаты слоя Group 15 (296×92), умноженные на [k].
 */
@Composable
fun DesignScope.LevelBadge(vm: GameViewModel, x: Float, y: Float, k: Float = 0.7f) {
    val s = vm.state
    val level = Progress.levelOf(s.xp)
    val (have, need) = Progress.inLevel(s.xp)
    fun X(v: Float) = x + v * k
    fun Y(v: Float) = y + v * k

    // Белая пилюля 234×68 (скругление 25).
    Box(
        Modifier
            .at(X(62f), Y(13f), 234f * k, 68.35f * k)
            .shadow(d(3f), RoundedCornerShape(d(25f * k)))
            .clip(RoundedCornerShape(d(25f * k)))
            .background(PillWhite)
            .clickable {
                vm.say("⭐ Уровень $level: опыта $have из $need. Опыт дают смены, задания, копилка, звёзды привычек и отказ от рекламы.")
            }
    )
    // Большой светлый круг с тенью и зелёный круг внутри.
    Box(
        Modifier
            .at(X(0f), Y(0f), 100f * k, 92.37f * k)
            .shadow(d(5f), CircleShape)
            .clip(CircleShape)
            .background(Color(0xFFF9FEF9))
    )
    Box(
        Modifier
            .at(X(16f), Y(15.2f), 67f * k, 61.9f * k)
            .clip(CircleShape)
            .background(LevelGreen),
        contentAlignment = Alignment.BottomCenter
    ) {
        MontikView(vm.sprite, s, d(58f * k), heroPalette(s))
    }
    // Имя, «Уровень N», шкала.
    DText(s.heroName, X(109f), Y(15f), 180f * k, 24f * k, color = NameInk, softWrap = false)
    DText("Уровень $level", X(109f), Y(46f), 170f * k, maxOf(13f * k, 10.5f), color = LevelGreen, softWrap = false)
    Box(
        Modifier
            .at(X(109f), Y(64f), 168f * k, 12.93f * k)
            .clip(RoundedCornerShape(d(20f)))
            .background(BarGreen)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(Progress.fraction(s.xp).coerceIn(0.17f, 1f))
                .clip(RoundedCornerShape(d(20f)))
                .background(LevelGreen)
        )
    }
}

// ───────────────────────── Облачко с репликой ─────────────────────────

/** Облачко реплики как в концептах макета: белое, скругление 22, хвостик к герою. */
@Composable
fun DesignScope.SpeechBubble(text: String, x: Float, y: Float, w: Float, h: Float, tailX: Float, tailDown: Boolean = true) {
    val tail = 14f
    val shape = GenericShape { size, _ ->
        val r = minOf(22f * size.height / (h + tail), size.height / 2f)
        val body = size.height - tail * size.height / (h + tail)
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, body, r, r))
        val tx = (tailX - x) / w * size.width
        if (tailDown) {
            moveTo(tx - 10f * size.width / w, body - 1f)
            lineTo(tx + 12f * size.width / w, size.height)
            lineTo(tx + 12f * size.width / w, body - 1f)
            close()
        }
    }
    Box(
        Modifier
            .at(x, y, w, h + tail)
            .shadow(d(4f), shape)
            .clip(shape)
            .background(Color.White)
    ) {
        Box(Modifier.at(0f, 0f, w, h), contentAlignment = Alignment.Center) {
            Text(
                text,
                color = MontikColors.Ink,
                fontSize = fs(14f, false),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = fs(17f, false),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.requiredWidth(d(w - 24f))
            )
        }
    }
}

/**
 * Монтик, который отвечает на нажатие: подпрыгивает, говорит «поп» и произносит реплику по ситуации.
 * При входе на экран важная реплика (голод, силы, квартира) появляется сама.
 */
@Composable
fun DesignScope.TalkingMontik(
    vm: GameViewModel,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    heroSize: Float,
    bubbleX: Float,
    bubbleY: Float,
    bubbleW: Float = 206f
) {
    val s = vm.state
    val fb = rememberFeedback()
    val jump = remember { Animatable(0f) }
    var phrase by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val unit = u

    // Сама говорит только о важном — один раз при входе на экран.
    LaunchedEffect(Unit) {
        val first = vm.phrases().firstOrNull()
        if (first != null && (s.food < 30 || s.water < 30 || s.energy < 30 || s.rentDebt > 0)) {
            delay(700)
            phrase = first
            shown++
        }
    }
    LaunchedEffect(shown) {
        if (phrase != null) {
            delay(4200)
            phrase = null
        }
    }

    Box(
        Modifier
            .at(x, y, w, h)
            .graphicsLayer { translationY = jump.value * unit * density }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val list = vm.phrases()
                phrase = list[index % list.size]
                index++
                shown++
                fb.tap()
                scope.launch {
                    jump.animateTo(-26f, tween(140))
                    jump.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        MontikView(vm.sprite, s, d(heroSize), heroPalette(s))
    }
    phrase?.let {
        SpeechBubble(it, bubbleX, bubbleY, bubbleW, 66f, tailX = (x + w / 2f - 20f).coerceIn(bubbleX + 24f, bubbleX + bubbleW - 30f))
    }
}

// ───────────────────────── «Совет от Лобачевского» (Group 14) ─────────────────────────

/** Карточка из макета 370×207: светло-зелёная, лампочка, заголовок Ubuntu Mono, портрет справа внизу. */
@Composable
fun DesignScope.TipCard(tip: Tip, x: Float, y: Float, onClose: (() -> Unit)? = null) {
    // Новый макет: готовая карточка с лампочкой, заголовком, портретом и крестиком (fg_tip_card),
    // текст совета рисуется кодом в её пустой части.
    if (rememberHasArt("fg_tip_card")) {
        val w = 380f
        val h = w * 938f / 1677f
        val cx = x - 5f
        Art("fg_tip_card", cx, y, w, h, ContentScale.FillBounds)
        Text(
            tip.text,
            modifier = Modifier.at(cx + w * 0.085f, y + h * 0.33f).requiredWidth(d(w * 0.55f)),
            color = MontikColors.Ink,
            fontSize = fs(14.5f, false),
            lineHeight = fs(18f, false),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
        if (onClose != null) Hit(cx + w * 0.86f, y + h * 0.72f, w * 0.1f, h * 0.15f, onClose)
        return
    }
    Box(
        Modifier
            .at(x, y, 370f, 207f)
            .clip(RoundedCornerShape(d(20f)))
            .background(TipGreen)
    )
    ArtImage("fg_tip_lamp", Modifier.at(x, y + 14f, 70f, 63f), ContentScale.FillBounds) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("💡", fontSize = fs(34f, false)) }
    }
    DText("Совет от Лобачевского:", x + 75f, y + 18f, 280f, 20f, color = MontikColors.Ink, softWrap = false)
    ArtImage("fg_tip_lobachevsky", Modifier.at(x + 267f, y + 104f, 85f, 93f), ContentScale.FillBounds) {}
    Text(
        tip.text,
        modifier = Modifier.at(x + 20f, y + 84f).requiredWidth(d(244f)),
        color = MontikColors.Ink,
        fontSize = fs(15f, false),
        lineHeight = fs(19f, false),
        maxLines = 5,
        overflow = TextOverflow.Ellipsis
    )
}

/** Совет дня: затемнение, карточка из макета и пилюля «Понятно». */
@Composable
fun TipOverlay(vm: GameViewModel, tip: Tip) {
    val fb = rememberFeedback()
    LaunchedEffect(tip.id) { fb.tap() }
    BackHandler(enabled = true) { vm.dismissTip(tip) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            TipCard(tip, 21f, 300f) { vm.dismissTip(tip) }
            LivePill("Понятно!", 121f, 530f) { vm.dismissTip(tip) }
        }
    }
}

/** Зелёная пилюля 169×59 — как «Готово» в макете. */
@Composable
fun DesignScope.LivePill(text: String, x: Float, y: Float, w: Float = 169f, fill: Color = Color(0xFF8AD742), onClick: () -> Unit) {
    Box(
        Modifier
            .at(x, y, w, 59f)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = fs(20f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
    }
}

// ───────────────────────── Новый уровень ─────────────────────────

/** Поздравление с новым уровнем: плашка Group 15 крупно, конфетти и фанфары. */
@Composable
fun LevelUpOverlay(vm: GameViewModel, level: Int) {
    val fb = rememberFeedback()
    LaunchedEffect(level) { fb.fanfare() }
    BackHandler(enabled = true) { vm.markLevelSeen() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            Box(Modifier.at(21f, 250f, 370f, 330f).clip(RoundedCornerShape(d(28f))).background(PillWhite))
            DText("Новый уровень!", 21f, 272f, 370f, 28f, color = NameInk, align = TextAlign.Center)
            LevelBadge(vm, 58f, 322f, k = 1f)
            DText(
                "Монтик растёт вместе с тобой: смены, задания, копилка и звёзды привычек дают опыт.",
                45f, 432f, 322f, 15f, bold = false, mono = false, color = MontikColors.Ink, align = TextAlign.Center
            )
            LivePill("Ура!", 121f, 500f) { vm.markLevelSeen() }
        }
        Confetti(level)
    }
}

// ───────────────────────── Сообщение от взрослого ─────────────────────────

/** Похвала от взрослого: карточка в стиле макета с сердечком. */
@Composable
fun ParentNoteOverlay(vm: GameViewModel) {
    val fb = rememberFeedback()
    LaunchedEffect(Unit) { fb.good() }
    BackHandler(enabled = true) { vm.readPraise() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            Box(Modifier.at(21f, 270f, 370f, 250f).clip(RoundedCornerShape(d(20f))).background(TipGreen))
            Box(
                Modifier.at(41f, 290f, 56f, 56f).clip(RoundedCornerShape(d(16f))).background(LevelGreen),
                contentAlignment = Alignment.Center
            ) { Text("💌", fontSize = fs(28f, false)) }
            DText("Сообщение от взрослого", 110f, 300f, 270f, 19f, color = NameInk)
            DText("Тебя похвалили!", 110f, 326f, 270f, 13f, color = LevelGreen)
            Box(
                Modifier.at(41f, 362f, 330f, 100f).clip(RoundedCornerShape(d(18f))).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "«${vm.state.parentNote}»",
                    color = MontikColors.Ink,
                    fontSize = fs(18f, false),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = d(14f))
                )
            }
            LivePill("Спасибо! ♥", 121f, 480f) { vm.readPraise() }
        }
    }
}

// ───────────────────────── Коллекция ─────────────────────────

/** Доска «Магниты из поездок» на кухне: собранные — яркие, остальные — пунктир с вопросом. */
@Composable
fun DesignScope.MagnetBoard(state: GameState, x: Float, y: Float) {
    val magnets = Keepsakes.magnets(state)
    val got = magnets.count { it.collected }
    Box(Modifier.at(x, y, 206f, 94f).clip(RoundedCornerShape(d(20f))).background(TipGreen.copy(alpha = 0.95f)))
    DText("Магниты из поездок  $got/${magnets.size}", x + 12f, y + 8f, 190f, 11.5f, color = NameInk, softWrap = false)
    magnets.forEachIndexed { i, m ->
        val mx = x + 12f + i * 48f
        val my = y + 30f
        if (m.collected) {
            Box(
                Modifier
                    .at(mx, my, 42f, 54f)
                    .rotate(if (i % 2 == 0) -6f else 5f)
                    .shadow(d(2f), RoundedCornerShape(d(8f)))
                    .clip(RoundedCornerShape(d(8f)))
                    .background(Color.White)
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Text(m.emoji, fontSize = fs(22f, false), modifier = Modifier.padding(bottom = d(12f)))
                Text(
                    m.city.take(7),
                    color = NameInk,
                    fontSize = fs(7.5f, false),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = d(4f))
                )
            }
        } else {
            Canvas(Modifier.at(mx, my, 42f, 54f)) {
                drawRoundRect(
                    NameInk.copy(alpha = 0.45f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp2px(this)),
                    style = Stroke(width = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * density, 4f * density)))
                )
            }
            Box(Modifier.at(mx, my, 42f, 54f), contentAlignment = Alignment.Center) {
                Text("?", color = NameInk.copy(alpha = 0.5f), fontSize = fs(20f, false), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Int.dp2px(scope: androidx.compose.ui.graphics.drawscope.DrawScope): Float = this * scope.density

/** Вещи, купленные на накопленное, стоят в комнате: белые наклейки с галочкой, как карточки в концептах макета. */
@Composable
fun DesignScope.RoomThings(vm: GameViewModel, x: Float, y: Float) {
    val things = Keepsakes.things(vm.state)
    things.forEachIndexed { i, goal ->
        val tx = x + i * 72f
        Box(
            Modifier
                .at(tx, y, 64f, 64f)
                .shadow(d(4f), CircleShape)
                .clip(CircleShape)
                .background(PillWhite)
                .border(d(2f), BarGreen, CircleShape)
                .clickable {
                    // Приставка открывает гонки; остальные вещи просто радуют.
                    if (goal.id == ru.montik.app.game.ConsoleGame.GOAL_ID) vm.goTo(ru.montik.app.Screen.Console)
                    else vm.say("${goal.emoji} «${goal.title}» — куплено на накопленные деньги. Терпение окупилось!")
                },
            contentAlignment = Alignment.Center
        ) { Text(goal.emoji, fontSize = fs(32f, false)) }
        Box(
            Modifier.at(tx + 44f, y - 2f, 22f, 22f).clip(CircleShape).background(LevelGreen),
            contentAlignment = Alignment.Center
        ) { Text("✓", color = Color.White, fontSize = fs(13f, false), fontWeight = FontWeight.Bold) }
    }
}

// ───────────────────────── Звёзды ─────────────────────────

/** Строка звёзд «★★☆» для смены в магазине. */
fun starsText(stars: Int, max: Int = 3): String = "★".repeat(stars.coerceIn(0, max)) + "☆".repeat((max - stars).coerceAtLeast(0))
