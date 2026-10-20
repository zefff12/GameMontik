package ru.montik.app.ui

import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Story
import ru.montik.app.game.VirtualClock

/* Экраны, нарисованные по макету Figma: заставка, история, «Готов начать?» и комната. */

// ───────────────────────── Заставка ─────────────────────────

@Composable
fun FigmaSplash(onStart: () -> Unit) {
    // Заставка по кадру «история начало» нового макета: фон (поля экрана — его зеркальное продолжение,
    // без второго фона), мягкое белое свечение за логотипом, логотип со звездой, подпись, Монтик и «Начать».
    DesignCanvas {
        FrameArt("fg3_sp_bg")
        SoftGlow(206f, 204.5f, 380f, 300f, Color.White, peak = 0.85f)
        Art("fg3_sp_title", 56f, 122f, 300f, 100f, ContentScale.FillBounds)
        Art("fg3_sp_star", 166f, 94f, 79f, 56f, ContentScale.FillBounds)
        DText(
            "Твой путь к финансовой свободе начинается с маленького шага",
            96f, 222f, 220f, 20f, color = Color(0xFF0A3652), align = TextAlign.Center, lineHeight = 1.0f
        )
        Art("fg3_sp_hero", 0f, 476f, 237f, 237f, ContentScale.FillBounds)
        Art("fg3_sp_btn", 18f, 702f, 376f, 125f, ContentScale.FillBounds)
        PressHit(36f, 722f, 340f, 86f, 43f, onClick = onStart)
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

    // Нажатие в любом месте экрана — следующая страница (кнопки «→» и подсказки внизу больше нет).
    // Свайп тоже листает: провёл пальцем влево или вправо — следующая страница.
    val next = { if (last) onDone() else index = i + 1 }
    val swipe = Modifier
        .pointerInput(i) {
            var dragged = 0f
            var fired = false
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f; fired = false },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragged += amount
                    if (!fired && kotlin.math.abs(dragged) > 60.dp.toPx()) {
                        fired = true
                        next()
                    }
                }
            )
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() }

    // Страница истории (фон вместе с текстом) выезжает целиком, как лист книги: старая уходит за край,
    // новая встаёт на её место. Две картинки не накладываются друг на друга — без «сдвоения» экрана.
    AnimatedContent(
        targetState = i,
        modifier = Modifier.fillMaxSize().then(swipe),
        transitionSpec = {
            val forward = targetState > initialState
            (androidx.compose.animation.slideInHorizontally(tween(MOTION_MS + 60)) { if (forward) it else -it }) togetherWith
                (androidx.compose.animation.slideOutHorizontally(tween(MOTION_MS + 60)) { if (forward) -it else it })
        },
        label = "storyPage"
    ) { idx ->
        Box(Modifier.fillMaxSize().clipToBounds()) {
            DesignCanvas {
                StoryLayer(idx, pages.size)
            }
        }
    }
}

/** Содержимое одной страницы истории: картинка, текст, точки и Монтик. Номер страницы — [idx]. */
@Composable
private fun DesignScope.StoryLayer(idx: Int, pageCount: Int) {
    val pages = Story.pages
    val frame = storyFrames[idx.coerceIn(0, storyFrames.lastIndex)]
    val activeDot = when (idx) {
        0 -> 0
        pageCount - 1 -> 2
        else -> 1
    }
    run {
        // Картинка истории — в координатах холста, как в макете: тогда Монтик стоит там же, где в Figma
        // (на набережной, а не в воде), даже если холст меньше экрана из-за панели навигации.
        if (frame.cropBg) {
            FrameArt(frame.bg, -0.2646f * DESIGN_W, -0.2631f * DESIGN_H, 1.2646f * DESIGN_W, 1.2633f * DESIGN_H)
        } else {
            // Поля экрана вокруг кадра — зеркальное продолжение той же картинки (без второго фона).
            FrameArt(frame.bg)
        }
        // Холст уже стоит над системной панелью навигации (см. DesignCanvas), поэтому ничего не поднимаем.
        val lift = 0f
        // Метка места: булавка, город и год.
        val pin = frame.pin
        val head = frame.head
        val page = pages[idx]
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
            // Карточка растёт вверх, если текст не помещается, и стоит над кнопкой и точками.
            Box(Modifier.at(card.x, 0f, card.w, card.y + card.h - lift), contentAlignment = Alignment.BottomStart) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = d(card.h))
                        .clip(RoundedCornerShape(d(20f)))
                        .background(Color.White)
                        .padding(
                            start = d(frame.textX - card.x),
                            end = d((card.x + card.w) - (frame.textX + frame.textW)).coerceAtLeast(d(8f)),
                            top = d(frame.textY - card.y),
                            bottom = d(12f)
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        page.text,
                        color = MontikColors.Ink,
                        fontSize = fs(20f),
                        lineHeight = fs(23f, false),
                        fontWeight = FontWeight.Bold,
                        fontFamily = MontikFont
                    )
                }
            }
        } else {
            // Последняя страница: крупная надпись сверху, без карточки.
            DText(
                page.text,
                x = 56f, y = 167f, w = 300f, size = 24f,
                align = TextAlign.Center, softWrap = false
            )
        }

        StoryDots(frame.dotsX, frame.dotsY - lift, activeDot)

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
            // Монтик стоит над белой карточкой с текстом (текст шрифтом Arial бывает на строку длиннее).
            val cardTop = card?.let { it.y - 34f } ?: DESIGN_H
            val charY = minOf(char.y, cardTop - char.h)
            Art(charArt, char.x, charY, char.w, char.h, ContentScale.Fit)
        }

    }
}

/** Три точки-страницы 13×13 с промежутком 12, как в макете. */
@Composable
private fun DesignScope.StoryDots(x: Float, y: Float, active: Int) {
    for (n in 0 until 3) {
        // Активная точка плавно становится белой, соседние — тусклыми.
        val dot by animateColorAsState(
            if (n == active) Color.White else Color(0xCCD9D9D9),
            animationSpec = tween(MOTION_MS),
            label = "dot"
        )
        Box(
            Modifier
                .at(x + n * 25f, y, 13f, 13f)
                .clip(CircleShape)
                .background(dot)
        )
    }
}

// ───────────────────────── «Готов начать?» ─────────────────────────

@Composable
fun FigmaReady(onGo: () -> Unit) {
    DesignCanvas(background = { Box(Modifier.fillMaxSize().background(MontikColors.Cream)) }) {
        // Кадр «история (начать игру)» нового макета: заголовок, текст и Монтик нарисованы на картинке.
        FrameArt("fg3_ready_bg", 0f, 35f, 412f, 787f)
        // Кнопка «Вперёд!» (image 455, 313×104).
        Art("fg3_ready_btn", 49f, 735f, 313f, 104f, ContentScale.FillBounds)
        PressHit(60f, 745f, 291f, 84f, 42f, onClick = onGo)
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
        TalkingMontik(vm, 96f, 396f, 220f, 220f, heroSize = 205f, bubbleX = 98f, bubbleY = 300f, bubbleW = 190f)
        HomeHud(vm, minutes)
    }
}
