package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.ConsoleGame
import kotlin.random.Random

/*
 * Игровая приставка Монтика — кадр макета «Android Compact - 39»: комната, телевизор с гонкой,
 * геймпад в лапках, сверху «Время игры» и «Лимит». На экране телевизора — своя гонка:
 * синяя машинка Монтика объезжает красных соперников и шины. Руль — стрелки геймпада
 * (левая часть — влево, правая с кнопками — вправо) или касание левой/правой половины экрана.
 * Заезд длится [ConsoleGame.RACE_SECONDS] секунд, в день — не больше [ConsoleGame.PLAYS_PER_DAY] заездов.
 */

private const val CK = 412f / 841f

/** Экран телевизора в кадре (пиксели картинки 841×1870 → dp). */
private val TV_X = 148f * CK
private val TV_Y = 381f * CK
private val TV_W = (832f - 148f) * CK
private val TV_H = (947f - 381f) * CK

private const val LANES = 3
private const val CAR_W = 34f
private const val CAR_H = 62f

private class Rival(var lane: Int, var y: Float, val tyres: Boolean)

private enum class RaceStep { Ready, Countdown, Racing, Finished }

@Composable
fun ConsoleScreen(vm: GameViewModel) {
    BackHandler { vm.back() }
    val blocker = ConsoleGame.blocker(vm.state)
    var step by remember { mutableStateOf(RaceStep.Ready) }
    var count by remember { mutableIntStateOf(3) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var bonus by remember { mutableIntStateOf(0) }
    var dist by remember { mutableFloatStateOf(0f) }
    var carX by remember { mutableFloatStateOf(0.5f) }
    var steer by remember { mutableIntStateOf(0) }
    var crash by remember { mutableFloatStateOf(0f) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(1f) }
    val rivals = remember { mutableStateListOf<Rival>() }
    val fb = rememberFeedback()
    val rnd = remember { Random(System.nanoTime()) }

    LaunchedEffect(step) {
        if (step == RaceStep.Countdown) {
            count = 3
            while (count > 0) {
                fb.tap()
                delay(700)
                count -= 1
            }
            step = RaceStep.Racing
        }
        if (step == RaceStep.Racing) {
            var last = withFrameNanos { it }
            var spawn = 0f
            while (step == RaceStep.Racing) {
                withFrameNanos { now ->
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    elapsed += dt
                    // Скорость растёт со временем, после аварии — ненадолго падает.
                    val target = 1f + elapsed / 30f
                    speed += (target - speed) * dt * 1.5f
                    if (crash > 0f) crash = (crash - dt).coerceAtLeast(0f)
                    carX = (carX + steer * dt * 1.6f).coerceIn(0.08f, 0.92f)
                    val pxPerSec = 260f * speed
                    scroll = (scroll + pxPerSec * dt) % 80f
                    // Соперники едут медленнее — Монтик их догоняет.
                    val passed = mutableListOf<Rival>()
                    for (r in rivals) {
                        r.y += pxPerSec * (if (r.tyres) 1f else 0.55f) * dt
                        if (r.y > TV_H + CAR_H) passed += r
                    }
                    for (r in passed) {
                        rivals.remove(r)
                        bonus += if (r.tyres) 5 else 10
                    }
                    spawn -= dt
                    if (spawn <= 0f) {
                        val lane = rnd.nextInt(LANES)
                        if (rivals.none { it.lane == lane && it.y < CAR_H * 1.5f }) {
                            rivals.add(Rival(lane, -CAR_H, rnd.nextInt(4) == 0))
                        }
                        spawn = (1.1f - elapsed / 90f).coerceAtLeast(0.45f)
                    }
                    // Столкновение: машинка Монтика внизу экрана.
                    if (crash == 0f) {
                        val carCx = roadLeft() + carX * roadWidth()
                        val carTop = TV_H - CAR_H - 10f
                        for (r in rivals) {
                            val rx = laneX(r.lane)
                            if (kotlin.math.abs(rx - carCx) < CAR_W * 0.85f && r.y + CAR_H * 0.9f > carTop && r.y < carTop + CAR_H * 0.9f) {
                                crash = 1.2f
                                speed = 0.5f
                                bonus -= 15
                                fb.bad()
                                break
                            }
                        }
                    }
                    dist += dt * 10f * speed
                    score = (bonus + dist.toInt()).coerceAtLeast(0)
                    if (elapsed >= ConsoleGame.RACE_SECONDS) {
                        step = RaceStep.Finished
                    }
                }
            }
            if (step == RaceStep.Finished) {
                fb.fanfare()
                vm.finishRace(score)
            }
        }
    }

    DesignCanvas(background = {
        ArtImage("fg_console_bg", Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF6B4E3A), Color(0xFF3B2A20)))))
        }
    }) {
        if (rememberHasArt("fg_console_bg")) Art("fg_console_bg", 0f, 0f, DESIGN_W, 1870f * CK, ContentScale.FillBounds)
        HeroTint("fg_console_bg", 0f, 0f, DESIGN_W, 1870f * CK, vm.state)

        // Экран телевизора: дорога, соперники, машинка Монтика.
        Box(Modifier.at(TV_X, TV_Y, TV_W, TV_H).clipToBounds()) {
            Canvas(Modifier.fillMaxSize()) {
                val k = size.width / TV_W
                drawRect(Color(0xFF5CB445), size = size)
                // Полосы травы бегут назад.
                var gy = -80f + scroll
                while (gy < TV_H) {
                    drawRect(Color(0xFF4FA23B), Offset(0f, gy * k), Size(size.width, 40f * k))
                    gy += 80f
                }
                val l = roadLeft() * k
                val w = roadWidth() * k
                drawRect(Color(0xFF4B5058), Offset(l, 0f), Size(w, size.height))
                // Бордюры: красно-белые.
                var cy = -40f + (scroll * 2f) % 40f
                var red = true
                while (cy < TV_H) {
                    val c = if (red) Color(0xFFE23B3B) else Color.White
                    drawRect(c, Offset(l - 10f * k, cy * k), Size(10f * k, 20f * k))
                    drawRect(c, Offset(l + w, cy * k), Size(10f * k, 20f * k))
                    cy += 20f
                    red = !red
                }
                // Разметка полос.
                for (lane in 1 until LANES) {
                    val x = l + w * lane / LANES
                    var dy = -40f + scroll % 40f
                    while (dy < TV_H) {
                        drawRect(Color.White, Offset(x - 2f * k, dy * k), Size(4f * k, 22f * k))
                        dy += 40f
                    }
                }
            }
            for (r in rivals) {
                if (r.tyres) {
                    Box(Modifier.at(laneX(r.lane) - 14f, r.y + 16f, 28f, 28f).clip(RoundedCornerShape(50)).background(Color(0xFF222222)))
                    Box(Modifier.at(laneX(r.lane) - 7f, r.y + 23f, 14f, 14f).clip(RoundedCornerShape(50)).background(Color(0xFF555555)))
                } else {
                    Art("fg_game_car_red", laneX(r.lane) - CAR_W / 2f, r.y, CAR_W, CAR_H, ContentScale.Fit)
                }
            }
            val carCx = roadLeft() + carX * roadWidth()
            Box(
                Modifier.at(carCx - CAR_W / 2f, TV_H - CAR_H - 10f, CAR_W, CAR_H).graphicsLayer {
                    rotationZ = steer * 8f + if (crash > 0f) kotlin.math.sin(crash * 30f) * 12f else 0f
                    alpha = if (crash > 0f && (crash * 10).toInt() % 2 == 0) 0.5f else 1f
                }
            ) { ArtImage("fg_game_car_blue", Modifier.fillMaxSize(), ContentScale.Fit) { Box(Modifier.fillMaxSize().background(Color(0xFF2F6BFF))) } }
            // Табло в углах, как в макете.
            TvPanel("ОЧКИ\n$score", 6f, 6f)
            TvPanel("ВРЕМЯ\n${formatSec((ConsoleGame.RACE_SECONDS - elapsed).coerceAtLeast(0f))}", TV_W - 76f, 6f)
            TvPanel("${(120 + 90 * speed).toInt()}\nкм/ч", TV_W - 76f, TV_H - 52f)
            when (step) {
                RaceStep.Countdown -> Box(Modifier.at(0f, 0f, TV_W, TV_H), contentAlignment = Alignment.Center) {
                    Text(if (count > 0) "$count" else "Старт!", color = Color.White, fontSize = fs(64f, false), fontWeight = FontWeight.Bold)
                }
                RaceStep.Finished -> Box(Modifier.at(0f, 0f, TV_W, TV_H).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Text("🏁 Финиш!\n$score очков", color = Color.White, fontSize = fs(30f, false), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                else -> Unit
            }
        }

        // «Время игры» и «Лимит» — плашки из макета.
        Box(
            Modifier.at(52f, 29f, 308f, 63f).clip(RoundedCornerShape(d(32f))).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Время игры : ${formatSec(elapsed)}",
                color = Color(0xFF1D1D1D), fontSize = fs(17f, false), fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier.at(222f, 85f, 160f, 31f).clip(RoundedCornerShape(50)).background(Color(0xFFE53935)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Лимит: ${ConsoleGame.RACE_SECONDS} с · ещё ${ConsoleGame.playsLeft(vm.state)}",
                color = Color.White, fontSize = fs(12f, false), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
        Box(Modifier.at(8f, 26f)) { BackCircle({ vm.back() }) }

        // Геймпад: левая часть (крестовина) — влево, правая (кнопки) — вправо. Держи, чтобы рулить.
        SteerZone(29f, 630f, 118f, 110f, -1) { steer = it }
        SteerZone(265f, 630f, 118f, 110f, 1) { steer = it }
        // Касание левой/правой половины телевизора — тоже руль.
        SteerZone(TV_X, TV_Y, TV_W / 2f, TV_H, -1) { steer = it }
        SteerZone(TV_X + TV_W / 2f, TV_Y, TV_W / 2f, TV_H, 1) { steer = it }

        when {
            blocker != null && step == RaceStep.Ready -> {
                Bubble(blocker, 30f, 480f, 352f, 120f, size = 16f)
                DPill("Назад", 121f, 828f, 169f, 59f, fill = Color(0xFF070707)) { vm.back() }
            }
            step == RaceStep.Ready -> {
                Bubble("Гонка на ${ConsoleGame.RACE_SECONDS} секунд! Объезжай красные машинки и шины. Держи левую или правую часть геймпада, чтобы рулить.", 30f, 480f, 352f, 120f, size = 15f)
                DPill("Старт!", 121f, 828f, 169f, 59f, fill = Color(0xFF070707)) { step = RaceStep.Countdown }
            }
            step == RaceStep.Finished -> {
                val left = ConsoleGame.playsLeft(vm.state)
                if (left > 0) {
                    DPill("Ещё заезд ($left)", 40f, 828f, 180f, 59f, fill = Color(0xFF070707), size = 17f) {
                        rivals.clear(); elapsed = 0f; score = 0; bonus = 0; dist = 0f; speed = 1f; carX = 0.5f; crash = 0f
                        step = RaceStep.Countdown
                    }
                }
                DPill("Выйти", if (left > 0) 232f else 121f, 828f, 140f, 59f, fill = Color(0xFF8AD742)) { vm.back() }
            }
            else -> Unit
        }
    }
}

private fun roadLeft(): Float = TV_W * 0.2f
private fun roadWidth(): Float = TV_W * 0.6f
private fun laneX(lane: Int): Float = roadLeft() + roadWidth() * (lane + 0.5f) / LANES

private fun formatSec(sec: Float): String {
    val s = sec.toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun DesignScope.TvPanel(text: String, x: Float, y: Float) {
    Box(
        Modifier.at(x, y, 70f, 44f).clip(RoundedCornerShape(d(6f))).background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = fs(12f, false), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = fs(13f, false))
    }
}

/** Зона «держи, чтобы рулить»: пока палец на ней, машинка едет в сторону [dir]. */
@Composable
private fun DesignScope.SteerZone(x: Float, y: Float, w: Float, h: Float, dir: Int, onSteer: (Int) -> Unit) {
    Box(
        Modifier.at(x, y, w, h).pointerInput(dir) {
            detectTapGestures(onPress = {
                onSteer(dir)
                tryAwaitRelease()
                onSteer(0)
            })
        }
    )
}
