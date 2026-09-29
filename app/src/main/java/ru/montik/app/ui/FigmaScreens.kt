package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Story
import ru.montik.app.game.VirtualClock

/* Экраны, нарисованные по макету Figma: заставка, история, «Готов начать?» и комната. */

// ───────────────────────── Заставка ─────────────────────────

@Composable
fun FigmaSplash(onStart: () -> Unit) {
    DesignCanvas(background = { ArtImage("fg_splash_bg", Modifier.fillMaxSize(), ContentScale.Crop) {} }) {
        // Белое свечение за логотипом (эллипс 322×253 с размытием).
        SoftGlow(cx = 206f, cy = 204.5f, w = 522f, h = 453f, color = Color.White, peak = 0.95f)
        Art("fg_splash_title", 56f, 122f, 300f, 100f, ContentScale.Fit)
        Art("fg_splash_star", 166f, 94f, 79f, 56f, ContentScale.Fit)
        DText(
            "Твой путь к финансовой\nсвободе начинается\nс маленького шага",
            x = 56f, y = 222f, w = 300f, size = 20f,
            align = TextAlign.Center, softWrap = false
        )
        Art("fg_splash_hero", 0f, 444f, 280f, 280f, ContentScale.Fit)

        // Кнопка «Начать»: белая слева, голубая справа.
        Box(
            Modifier
                .at(44f, 774f, 323f, 73f)
                .clip(RoundedCornerShape(d(36.5f)))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.White,
                        0.444f to Color.White,
                        0.711f to Color(0xFF57B7FF),
                        1f to Color(0xFF57B7FF)
                    )
                )
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Начать",
                color = MontikColors.Ink,
                fontSize = fs(32f),
                fontFamily = MontikFont,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ───────────────────────── История ─────────────────────────

private class Rect4(val x: Float, val y: Float, val w: Float, val h: Float)

/** Раскладка одной страницы истории: числа взяты из кадров «история 2» … «история 9». */
private class StoryFrame(
    val bg: String,
    val card: Rect4?,
    val textX: Float,
    val textY: Float,
    val textW: Float,
    val dotsX: Float,
    val dotsY: Float,
    val nextX: Float,
    val nextY: Float,
    val pin: Pair<Float, Float>? = null,
    val head: Pair<Float, Float>? = null,
    val char: Rect4? = null,
    val charArt: String? = null,
    val shadow: Boolean = false,
    val cropBg: Boolean = false,
    val limeNext: Boolean = false
)

private val storyFrames = listOf(
    StoryFrame(
        "fg_story2_bg", Rect4(17f, 735f, 378f, 113f), 34f, 751f, 355f, 174f, 877f, 345f, 858f,
        pin = 18f to 42f, head = 62f to 54f,
        char = Rect4(-20f, 510f, 244f, 203f), charArt = "fg_story2_char", cropBg = true
    ),
    StoryFrame("fg_story3_bg", Rect4(17f, 735f, 378f, 113f), 28f, 751f, 355f, 175f, 877f, 345f, 858f),
    StoryFrame(
        "fg_story4_bg", Rect4(17f, 707f, 378f, 141f), 29f, 727f, 355f, 175f, 877f, 345f, 858f,
        pin = 8f to 36f, head = 52f to 48f,
        char = Rect4(29f, 434f, 207f, 207f), charArt = "fg_story4_char"
    ),
    StoryFrame("fg_story5_bg", Rect4(17f, 730f, 378f, 117f), 29f, 758f, 355f, 175f, 876f, 345f, 857f),
    StoryFrame("fg_story6_bg", Rect4(15f, 730f, 384f, 117f), 21f, 758f, 372f, 175f, 877f, 349f, 858f),
    StoryFrame(
        "fg_story7_bg", Rect4(14f, 732f, 384f, 117f), 20f, 760f, 372f, 175f, 877f, 348f, 858f,
        char = Rect4(88f, 457f, 222f, 225f), charArt = "fg_story7_char", shadow = true
    ),
    StoryFrame("fg_story8_bg", Rect4(17f, 707f, 378f, 141f), 28f, 728f, 355f, 174f, 876f, 345f, 858f),
    StoryFrame(
        "fg_story9_bg", null, 0f, 0f, 0f, 182f, 874f, 353f, 856f,
        char = Rect4(54f, 458f, 257f, 261f), charArt = "fg_story7_char", limeNext = true
    )
)

/** Первая история в макете обрезана: картинка увеличена в 1,26 раза и сдвинута влево-вверх. */
@Composable
private fun StoryBackground(frame: StoryFrame) {
    if (!frame.cropBg) {
        ArtImage(frame.bg, Modifier.fillMaxSize(), ContentScale.Crop) {}
    } else {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            ArtImage(
                frame.bg,
                Modifier
                    // Без wrapContentSize картинка больше экрана встаёт по центру и съезжает влево.
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(maxWidth * 1.2646f, maxHeight * 1.2633f)
                    .offset(maxWidth * -0.2646f, maxHeight * -0.2631f),
                ContentScale.FillBounds
            ) {}
        }
    }
}

@Composable
fun FigmaStory(onDone: () -> Unit) {
    val pages = Story.pages
    var index by rememberSaveable { mutableStateOf(0) }
    val i = index.coerceIn(0, pages.lastIndex)
    val frame = storyFrames[i.coerceIn(0, storyFrames.lastIndex)]
    val last = i == pages.lastIndex
    val activeDot = when (i) {
        0 -> 0
        pages.lastIndex -> 2
        else -> 1
    }

    BackHandler(enabled = i > 0) { index = i - 1 }

    DesignCanvas(background = { StoryBackground(frame) }) {
        // Метка места: булавка, город и год.
        val pin = frame.pin
        val head = frame.head
        val page = pages[i]
        if (pin != null) Art("fg_story_pin", pin.first, pin.second, 39f, 39f, ContentScale.Fit)
        if (head != null && page.header != null) {
            DText(page.header, head.first, head.second, 260f, 24f, softWrap = false)
            if (page.subheader != null) {
                DText(page.subheader, head.first, head.second + 27.6f, 260f, 24f, bold = false, softWrap = false)
            }
        }

        // Белая карточка с текстом.
        val card = frame.card
        if (card != null) {
            Box(
                Modifier
                    .at(card.x, card.y, card.w, card.h)
                    .clip(RoundedCornerShape(d(20f)))
                    .background(Color.White)
            )
            DText(page.text, frame.textX, frame.textY, frame.textW, 20f)
        } else {
            // Последняя страница: крупная надпись сверху, без карточки.
            DText(
                page.text,
                x = 56f, y = 167f, w = 300f, size = 24f,
                align = TextAlign.Center, softWrap = false
            )
        }

        StoryDots(frame.dotsX, frame.dotsY, activeDot)

        val charArt = frame.charArt
        val char = frame.char
        if (frame.shadow) {
            // Тень под героем на вокзале: повёрнутый мягкий эллипс.
            SoftGlow(
                cx = 174.5f, cy = 682f, w = 173f, h = 136f,
                color = Color(0xFF1B2B3A), peak = 0.7f, rotate = -21.58f
            )
        }
        if (charArt != null && char != null) {
            Art(charArt, char.x, char.y, char.w, char.h, ContentScale.Fit)
        }

        StoryNext(frame.nextX, frame.nextY, frame.limeNext) {
            if (last) onDone() else index = i + 1
        }
    }
}

/** Три точки-страницы 13×13 с промежутком 12, как в макете. */
@Composable
private fun DesignScope.StoryDots(x: Float, y: Float, active: Int) {
    for (n in 0 until 3) {
        Box(
            Modifier
                .at(x + n * 25f, y, 13f, 13f)
                .clip(CircleShape)
                .background(if (n == active) Color.White else Color(0xCCD9D9D9))
        )
    }
}

/** Круглая кнопка «дальше» 50×50 со стрелкой 24×24. */
@Composable
private fun DesignScope.StoryNext(x: Float, y: Float, lime: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .at(x, y, 50f, 50f)
            .shadow(d(3f), CircleShape)
            .clip(CircleShape)
            .background(if (lime) MontikColors.Lime else Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ArtImage("fg_story_arrow", Modifier.size(d(24f)), ContentScale.Fit) {
            Text("→", color = MontikColors.Ink, fontWeight = FontWeight.Bold, fontSize = fs(22f, false))
        }
    }
}

// ───────────────────────── «Готов начать?» ─────────────────────────

@Composable
fun FigmaReady(onGo: () -> Unit) {
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(MontikColors.Cream)) }) {
        // Картинка целиком: заголовок, текст, росчерки и Монтик уже нарисованы в макете.
        Art("fg_ready_bg", 0f, 35f, 412f, 787f, ContentScale.Crop)
        Box(
            Modifier
                .at(44f, 774f, 323f, 73f)
                .clip(RoundedCornerShape(d(36.5f)))
                .background(MontikColors.Lime)
                .clickable(onClick = onGo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Вперёд!",
                color = MontikColors.Ink,
                fontSize = fs(32f),
                fontFamily = MontikFont,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ───────────────────────── Комната ─────────────────────────

/**
 * Главный экран — комната Монтика. Картинка зависит от уровня (жилья) и времени суток на виртуальных
 * часах (см. Scenery.kt). Поверх — всё, что по ТЗ видно сразу: баланс, накопления, цель, состояние,
 * активное задание, а ещё уровень, настроение и отсчёт до оплаты квартиры.
 */
@Composable
fun FigmaHome(vm: GameViewModel) {
    val s = vm.state
    val nowMs = rememberNowMs()
    val minutes = VirtualClock.timeOfDay(VirtualClock.now(s, nowMs))
    val bgName = roomArt(s, minutes / 60)

    DesignCanvas(background = { ArtImage(bgName, Modifier.fillMaxSize(), ContentScale.Crop) {} }) {
        // Вещи, купленные на накопленное (велосипед, приставка), стоят в комнате.
        RoomThings(vm, 10f, 548f)
        // Герой живёт в этой комнате, над нижней панелью. Нажми — ответит.
        TalkingMontik(vm, 96f, 396f, 220f, 220f, heroSize = 205f, bubbleX = 30f, bubbleY = 318f)
        HomeHud(vm, minutes)
        InfoPanel(vm, kitchen = false)
    }
}
