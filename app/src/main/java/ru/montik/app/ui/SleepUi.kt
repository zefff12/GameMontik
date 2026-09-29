package ru.montik.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.data.TrustedClock
import ru.montik.app.WakeUi
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Rules
import ru.montik.app.game.SleepPlace
import ru.montik.app.game.SleepQuality
import ru.montik.app.game.SleepStatus
import ru.montik.app.game.VirtualClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * Сон и пробуждение Монтика. Три экрана, все — в координатах макета 412×917:
 *   1. SleepingScreen — ночная комната из макета (Монтик спит в кровати), таймер сна;
 *   2. AlarmScreen    — «Просыпайся!»: будильник, кнопки «Выключить» и «Отложить на 15 минут»;
 *   3. WakeScreen     — утренняя комната, Монтик уже стоит возле кровати, итоги сна.
 * Время идёт по виртуальным часам (1 настоящая минута = 50 виртуальных), см. game/Sleep.kt.
 */

/** Настоящее время в миллисекундах, обновляется раз в [periodMs]: от него считаются виртуальные часы. */
@Composable
fun rememberNowMs(periodMs: Long = 1000L): Long {
    // «Честные» часы: перевод времени в настройках телефона не ускоряет игру.
    val now = produceState(initialValue = TrustedClock.now()) {
        while (true) {
            delay(periodMs)
            value = TrustedClock.now()
        }
    }
    return now.value
}

/** Пока Монтик спит: таймер сна, а когда пришло время — будильник. */
@Composable
fun SleepFlow(vm: GameViewModel) {
    val nowMs = rememberNowMs()
    val session = vm.state.sleep ?: return
    val status = GameEngine.sleepStatus(vm.state, nowMs) ?: return
    if (status.ringing) {
        AlarmScreen(
            status = status,
            snoozes = session.snoozes,
            onOff = { vm.wakeUp() },
            onSnooze = { vm.snoozeAlarm() }
        )
    } else {
        SleepingScreen(
            status = status,
            place = session.place,
            art = sleepArt(vm.state),
            vm = vm,
            onWakeEarly = { vm.wakeUp() }
        )
    }
}

// ───────────────────────── 1. Монтик спит ─────────────────────────

@Composable
fun SleepingScreen(
    status: SleepStatus,
    place: SleepPlace,
    art: String = "fg_room_night",
    vm: GameViewModel? = null,
    onWakeEarly: () -> Unit
) {
    val hasArt = rememberHasArt(art)
    val spot = BED_SPOTS[art]
    DesignCanvas(
        background = {
            ArtImage(art, Modifier.fillMaxSize(), ContentScale.Crop) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF16204A), Color(0xFF2A2F63))))
                )
            }
        }
    ) {
        if (!hasArt) {
            Text("😴", modifier = Modifier.at(150f, 470f), fontSize = fs(96f, false))
        }

        // Твой Монтик спит в кровати: комната (белый человечек из неё стёрт) → Монтик на подушке →
        // одеяло из той же картинки поверх него (<art>_front), чтобы он был «под одеялом».
        if (hasArt && spot != null && vm != null && place != SleepPlace.BENCH) {
            Art(art, 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.FillBounds)
            SleepingMontik(vm, spot)
            if (rememberHasArt(art + "_front")) Art(art + "_front", 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.FillBounds)
            if (spot.night != Color.Transparent) Box(Modifier.at(0f, 0f, DESIGN_W, DESIGN_H).background(spot.night))
        }

        // «Z-z-z» поднимаются над кроватью.
        val transition = rememberInfiniteTransition(label = "zzz")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
            label = "zzzPhase"
        )
        for (i in 0..2) {
            val p = (phase + i / 3f) % 1f
            Text(
                "Z",
                modifier = Modifier
                    .at(150f + p * 34f, 452f - p * 90f)
                    .graphicsLayer { alpha = sin(PI.toFloat() * p) * 0.9f },
                color = Color.White,
                fontSize = fs(18f + p * 16f, false),
                fontWeight = FontWeight.Bold
            )
        }

        // Плашка с таймером сна.
        Box(
            Modifier
                .at(16f, 28f, 380f, 296f)
                .clip(RoundedCornerShape(d(28f)))
                .background(Color(0xE60E1A3C))
        )
        DText("Монтик спит", 36f, 44f, 340f, 26f, color = Color.White)
        val place1 = if (place == SleepPlace.BENCH) "  ·  🪑 скамейка" else ""
        DText(
            "Сейчас ${VirtualClock.formatV(status.virtualNow)}  ·  будильник на ${VirtualClock.format(status.alarmTimeOfDay)}$place1",
            36f, 82f, 340f, 14f, bold = false, color = Color(0xFFBFD0F5), mono = false
        )

        DText("Проспал", 36f, 122f, 170f, 14f, bold = false, color = Color(0xFFBFD0F5), mono = false)
        DText(VirtualClock.duration(status.sleptMinutes), 36f, 142f, 170f, 26f, color = Color.White)
        DText("До пробуждения", 214f, 122f, 170f, 14f, bold = false, color = Color(0xFFBFD0F5), mono = false)
        DText(VirtualClock.duration(status.remainingMinutes), 214f, 142f, 170f, 26f, color = Color.White)

        // Полоска сна.
        Box(
            Modifier
                .at(36f, 196f, 340f, 16f)
                .clip(RoundedCornerShape(d(8f)))
                .background(Color.White.copy(alpha = 0.2f))
        )
        Box(
            Modifier
                .at(36f, 196f, maxOf(16f, 340f * status.progress), 16f)
                .clip(RoundedCornerShape(d(8f)))
                .background(MontikColors.Lime)
        )

        val hint = when {
            status.sleptMinutes < Rules.OK_SLEEP_MINUTES ->
                "Если разбудить сейчас, Монтик не выспится и будет уставать быстрее."
            status.sleptMinutes < Rules.GOOD_SLEEP_MINUTES ->
                "Ещё немного — и Монтик выспится по-настоящему."
            else -> "Монтик уже выспался. Можно спать до будильника."
        }
        DText(hint, 36f, 226f, 340f, 14f, bold = false, color = Color.White, mono = false)
        DText(
            "Можно закрыть игру: время идёт и без неё.",
            36f, 282f, 340f, 12f, bold = false, color = Color(0xFFBFD0F5), mono = false
        )

        // Разбудить раньше.
        Box(
            Modifier
                .at(44f, 840f, 323f, 56f)
                .clip(RoundedCornerShape(d(28f)))
                .background(Color.White.copy(alpha = 0.16f))
                .border(d(1.5f), Color.White.copy(alpha = 0.55f), RoundedCornerShape(d(28f)))
                .clickable(onClick = onWakeEarly),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Разбудить раньше",
                color = Color.White,
                fontSize = fs(18f, false),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Где на картинке комнаты лежит голова Монтика: рамка скина в координатах макета и ночная тень. */
private class BedSpot(val x: Float, val y: Float, val size: Float, val shade: Color, val rotation: Float = -38f, val night: Color = Color.Transparent)

private val BED_SPOTS = mapOf(
    "fg_room_night" to BedSpot(10.8f, 425.2f, 132.3f, Color(0x54182048)),
    "fg_room2_sleep" to BedSpot(21.5f, 370.4f, 88.1f, Color(0x99121630)),
    "fg_room3_sleep" to BedSpot(18.1f, 396.3f, 100.3f, Color(0x8C121630)),
    // Гостиница в путешествии: подушки справа, в комнате включён вечерний свет.
    "fg_trip_hotel" to BedSpot(282f, 430f, 128f, Color(0x33121630), rotation = 38f, night = Color(0x66101830))
)

/** Монтик лежит на подушке: повёрнут на бок и чуть затенён, как вся комната ночью. */
@Composable
private fun DesignScope.SleepingMontik(vm: GameViewModel, spot: BedSpot) {
    Box(
        Modifier
            .at(spot.x, spot.y, spot.size, spot.size)
            .graphicsLayer {
                rotationZ = spot.rotation
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(spot.shade, blendMode = BlendMode.SrcAtop)
            }
    ) {
        MontikView(
            sprite = vm.sprite,
            state = vm.state,
            boxSize = d(spot.size),
            palette = heroPalette(vm.state),
            still = true
        )
    }
}

// ───────────────────────── 2. Будильник ─────────────────────────

@Composable
fun AlarmScreen(status: SleepStatus, snoozes: Int, onOff: () -> Unit, onSnooze: () -> Unit) {
    DesignCanvas(
        background = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(MontikColors.PhoneTop, MontikColors.PhoneBottom)))
            )
        }
    ) {
        SoftGlow(360f, 130f, 200f, 200f, Color.White, peak = 0.35f)
        SoftGlow(60f, 820f, 260f, 200f, Color.White, peak = 0.3f)

        DText("Просыпайся!", 24f, 62f, 340f, 34f)
        DText("Сегодня отличный день ☺", 24f, 108f, 340f, 15f, bold = false, mono = false)

        // Белая карточка с будильником.
        Box(
            Modifier
                .at(16f, 150f, 380f, 462f)
                .shadow(d(10f), RoundedCornerShape(d(36f)))
                .clip(RoundedCornerShape(d(36f)))
                .background(Color.White)
        )

        val transition = rememberInfiniteTransition(label = "alarm")
        val shake by transition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(90, easing = LinearEasing), RepeatMode.Reverse),
            label = "shake"
        )
        val wave by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "wave"
        )
        // Звуковые «волны» по бокам будильника.
        Canvas(Modifier.at(30f, 166f, 352f, 180f)) {
            val cx = size.width / 2f
            val cy = size.height * 0.56f
            for (side in listOf(-1f, 1f)) {
                for (k in 0..1) {
                    val r = size.height * (0.5f + 0.17f * k)
                    val a = 0.25f + 0.75f * abs(sin(PI.toFloat() * (wave + k * 0.3f)))
                    drawArc(
                        color = MontikColors.LimeDeep.copy(alpha = a.coerceIn(0f, 1f)),
                        startAngle = if (side < 0f) 150f else -30f,
                        sweepAngle = 60f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = size.height * 0.05f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Box(Modifier.at(126f, 172f, 160f, 160f).graphicsLayer { rotationZ = shake }) {
            AlarmClock(Modifier.fillMaxSize())
        }

        DText("Будильник", 16f, 346f, 380f, 24f, align = TextAlign.Center)
        DText(VirtualClock.format(status.alarmTimeOfDay), 16f, 380f, 380f, 76f, align = TextAlign.Center)
        DText(
            "Доброе утро! Монтику пора просыпаться",
            56f, 480f, 300f, 20f,
            align = TextAlign.Center, mono = false
        )
        val extra = if (snoozes > 0) "  ·  отложено: $snoozes" else ""
        DText(
            "Проспал: ${VirtualClock.duration(status.sleptMinutes)}$extra",
            16f, 552f, 380f, 14f,
            bold = false, color = MontikColors.InkSoft, align = TextAlign.Center, mono = false
        )

        // «Выключить».
        Box(
            Modifier
                .at(44f, 640f, 323f, 73f)
                .shadow(d(8f), RoundedCornerShape(d(36.5f)))
                .clip(RoundedCornerShape(d(36.5f)))
                .background(Brush.verticalGradient(listOf(Color(0xFF8BE05A), Color(0xFF3FAE49))))
                .clickable(onClick = onOff),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PowerIcon(d(28f), Color.White)
                Spacer(Modifier.width(d(12f)))
                Text(
                    "Выключить",
                    color = Color.White,
                    fontSize = fs(28f),
                    fontFamily = MontikFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // «Отложить на 15 минут».
        Box(
            Modifier
                .at(44f, 732f, 323f, 66f)
                .shadow(d(4f), RoundedCornerShape(d(33f)))
                .clip(RoundedCornerShape(d(33f)))
                .background(Color.White)
                .clickable(onClick = onSnooze),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockIcon(d(24f), MontikColors.Ink)
                Spacer(Modifier.width(d(10f)))
                Text(
                    "Отложить на ${Rules.SNOOZE_MINUTES} минут",
                    color = MontikColors.Ink,
                    fontSize = fs(21f),
                    fontFamily = MontikFont,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DText(
            "Монтик поспит ещё, а будильник зазвонит снова.",
            16f, 816f, 380f, 13f,
            bold = false, color = MontikColors.Ink.copy(alpha = 0.7f), align = TextAlign.Center, mono = false
        )
    }
}

/** Значок «выключить»: дуга с чёрточкой сверху. */
@Composable
private fun PowerIcon(iconSize: Dp, color: Color) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.11f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, w * 0.16f),
            size = Size(w * 0.76f, w * 0.76f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w / 2f, w * 0.05f), Offset(w / 2f, w * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Значок часов: кружок и две стрелки. */
@Composable
private fun ClockIcon(iconSize: Dp, color: Color) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.09f
        drawCircle(color, radius = w * 0.44f, center = center, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, w * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, center, Offset(w * 0.7f, w * 0.6f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

// ───────────────────────── 3. Монтик проснулся ─────────────────────────

@Composable
fun WakeScreen(vm: GameViewModel, ui: WakeUi) {
    val s = vm.state
    val art = wakeArt(s)
    DesignCanvas(
        background = {
            ArtImage(art, Modifier.fillMaxSize(), ContentScale.Crop) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(MontikColors.SkyTop, MontikColors.RoomWall)))
                )
            }
        }
    ) {
        // Монтик уже стоит возле кровати.
        Box(Modifier.at(160f, 610f, 240f, 240f), contentAlignment = Alignment.BottomCenter) {
            MontikView(vm.sprite, s, d(230f), heroPalette(s))
        }

        // Итоги сна.
        Column(
            Modifier
                .at(16f, 28f)
                .requiredWidth(d(380f))
                .shadow(d(8f), RoundedCornerShape(d(28f)))
                .clip(RoundedCornerShape(d(28f)))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(d(18f))
        ) {
            Text(
                "Доброе утро!",
                color = MontikColors.Ink,
                fontSize = fs(30f),
                fontFamily = MontikFont,
                fontWeight = FontWeight.Bold
            )
            Text(
                "День ${ui.day}",
                color = MontikColors.InkSoft,
                fontSize = fs(15f, false)
            )
            Spacer(Modifier.height(d(12f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d(10f))) {
                WakeChip("😴", "Проспал", VirtualClock.duration(ui.sleptMinutes), Modifier.weight(1f))
                WakeChip("⚡", "Силы", "${ui.energy}%", Modifier.weight(1f))
            }
            Spacer(Modifier.height(d(10f)))

            val (badge, badgeColor) = when (ui.quality) {
                SleepQuality.RESTED ->
                    "Выспался: смена отнимет ${Rules.RESTED_SHIFT_ENERGY} сил" to MontikColors.Lime
                SleepQuality.NORMAL ->
                    "Отдохнул: смена отнимет ${Rules.SHIFT_ENERGY} сил" to MontikColors.SurfaceTint
                SleepQuality.TIRED ->
                    "Не выспался: смена отнимет ${Rules.TIRED_SHIFT_ENERGY} сил" to Color(0xFFFFE2B8)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d(14f)))
                    .background(badgeColor)
                    .padding(horizontal = d(12f), vertical = d(8f))
            ) {
                Text(badge, color = MontikColors.Ink, fontSize = fs(15f, false), fontWeight = FontWeight.Bold)
            }

            val diary = ui.diary
            if (diary != null && (diary.earned > 0 || diary.spent > 0 || diary.saved > 0)) {
                Spacer(Modifier.height(d(10f)))
                Text(
                    "📒 День ${diary.day}: заработал ${diary.earned}, потратил ${diary.spent}, отложил ${diary.saved}",
                    color = MontikColors.Ink,
                    fontSize = fs(14f, false)
                )
            }
            // Остальные новости ночи (проценты, износ одежды, голод); рассказ про сон уже в плашках выше.
            val news = ui.messages.filterNot { it.startsWith("🌞") || it.startsWith("🥱") || it.startsWith("😌") }
            for (m in news.take(3)) {
                Spacer(Modifier.height(d(6f)))
                Text(m, color = MontikColors.Ink, fontSize = fs(13f, false))
            }
        }

        // «Начать день».
        Box(
            Modifier
                .at(44f, 850f, 323f, 56f)
                .clip(RoundedCornerShape(d(28f)))
                .background(MontikColors.Lime)
                .clickable { vm.dismissWake() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Начать день",
                color = MontikColors.Ink,
                fontSize = fs(24f),
                fontFamily = MontikFont,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DesignScope.WakeChip(emoji: String, label: String, value: String, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(d(16f)))
            .background(MontikColors.SurfaceTint)
            .padding(horizontal = d(10f), vertical = d(8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = fs(22f, false))
        Spacer(Modifier.width(d(8f)))
        Column {
            Text(label, color = MontikColors.InkSoft, fontSize = fs(12f, false))
            Text(value, color = MontikColors.Ink, fontSize = fs(17f, false), fontWeight = FontWeight.Bold)
        }
    }
}
