package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.game.Story

/**
 * Картинка из макета: если файл с таким именем лежит в res/drawable-nodpi, он показывается,
 * иначе рисуется запасной вариант ([fallback]). Так игра работает и без иллюстраций,
 * а оформление из Figma подключается копированием PNG в папку (см. docs/DESIGN_ASSETS.md).
 */
@Composable
fun ArtImage(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val resId = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (resId != 0) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier) { fallback() }
    }
}

/** Логотип «Монтик»: голубые буквы с белой обводкой, как на заставке макета. */
@Composable
fun MontikLogo(modifier: Modifier = Modifier, fontSize: TextUnit = 56.sp) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "Монтик",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = fontSize,
                color = Color.White,
                drawStyle = Stroke(width = 24f, join = StrokeJoin.Round)
            )
        )
        Text(
            "Монтик",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize),
            color = Color(0xFF39A7FF)
        )
    }
}

/** Кнопка-пилюля из макета: 323×73, полностью скруглённая, текст 32sp. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MontikColors.Lime
) {
    Surface(
        modifier = modifier
            .widthIn(max = MontikSizes.ButtonMaxWidth)
            .fillMaxWidth()
            .height(MontikSizes.ButtonHeight)
            .clip(MontikShapes.Button)
            .clickable(onClick = onClick),
        shape = MontikShapes.Button,
        color = color,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.headlineMedium, color = MontikColors.Ink)
        }
    }
}

/** Метка места в левом верхнем углу страницы истории: булавка, город и год. */
@Composable
private fun PinBadge(city: String, year: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(39.dp)) {
            val w = size.width
            val pin = Path().apply {
                moveTo(w * 0.5f, w * 0.95f)
                cubicTo(w * 0.05f, w * 0.55f, w * 0.12f, w * 0.05f, w * 0.5f, w * 0.05f)
                cubicTo(w * 0.88f, w * 0.05f, w * 0.95f, w * 0.55f, w * 0.5f, w * 0.95f)
                close()
            }
            drawPath(pin, Color.White)
            drawPath(pin, MontikColors.Ink, style = Stroke(width = w * 0.07f))
            drawCircle(MontikColors.Ink, radius = w * 0.14f, center = Offset(w * 0.5f, w * 0.42f))
        }
        Spacer(Modifier.size(8.dp))
        Column {
            Text(city, style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
            if (year != null) {
                Text(
                    year,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                    color = MontikColors.Ink
                )
            }
        }
    }
}

/** Точки-страницы внизу и круглая кнопка «дальше» справа — как в макете. */
@Composable
private fun StoryFooter(index: Int, total: Int, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            for (i in 0 until total) {
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (i == index) Color.White else Color.White.copy(alpha = 0.5f))
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(50.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("→", fontSize = 26.sp, color = MontikColors.Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Вступление: заставка → история → «Готов начать?». */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    // Если в проект добавлены иллюстрации из Figma (fg_*), показываем экраны точно по макету,
    // иначе — прежние нарисованные экраны.
    val figma = rememberHasArt("fg_splash_bg", "fg_story2_bg", "fg_ready_bg")
    when (step) {
        0 -> if (figma) FigmaSplash(onStart = { step = 1 }) else SplashScreen(onStart = { step = 1 })
        1 -> if (figma) FigmaStory(onDone = { step = 2 }) else StoryScreen(onDone = { step = 2 })
        else -> if (figma) FigmaReady(onGo = onFinished) else ReadyScreen(onGo = onFinished)
    }
}

@Composable
private fun SkyBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(MontikColors.SkyTop, MontikColors.SkyMid, MontikColors.SkyLow))
        )
    )
}

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ArtImage("bg_splash", Modifier.fillMaxSize()) { SkyBackdrop(Modifier.fillMaxSize()) }
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            MontikLogo()
            Spacer(Modifier.height(8.dp))
            Text(
                "Твой путь к финансовой свободе начинается с маленького шага",
                style = MaterialTheme.typography.titleMedium,
                color = MontikColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 240.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                ArtImage("mascot_splash", Modifier.size(260.dp), ContentScale.Fit) {
                    MontikBunny(Modifier.size(260.dp))
                }
            }
            PillButton("Начать", onStart, color = Color.White)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun StoryScreen(onDone: () -> Unit) {
    val pages = Story.pages
    var index by rememberSaveable { mutableStateOf(0) }
    val page = pages[index.coerceIn(0, pages.lastIndex)]
    Box(Modifier.fillMaxSize()) {
        ArtImage(page.art, Modifier.fillMaxSize()) { StoryBackdrop(index) }
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 17.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                if (page.header != null) PinBadge(page.header, page.subheader)
                if (page.centered) {
                    Spacer(Modifier.height(60.dp))
                    Text(
                        page.text,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MontikColors.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    )
                }
            }
            Column {
                if (!page.centered) {
                    Surface(
                        shape = MontikShapes.Card,
                        color = Color.White,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            page.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MontikColors.Ink,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                StoryFooter(index, pages.size) {
                    if (index < pages.lastIndex) index += 1 else onDone()
                }
            }
        }
    }
}

/** Запасной фон истории, если иллюстрации из макета ещё не добавлены. */
@Composable
private fun StoryBackdrop(index: Int) {
    val tints = listOf(
        MontikColors.SkyTop to Color(0xFFE8C99A),
        Color(0xFF9CC2E0) to Color(0xFFCFB08A),
        Color(0xFF9CC2E0) to Color(0xFFC2A084),
        Color(0xFFF0C070) to Color(0xFFFCE6B8),
        Color(0xFF8FB8DE) to Color(0xFFD9C3A0),
        Color(0xFFF2B978) to Color(0xFFFBE3BC),
        Color(0xFF9FCBE8) to Color(0xFFE3D2B4),
        MontikColors.SkyTop to MontikColors.SkyLow
    )
    val (top, bottom) = tints[index.coerceIn(0, tints.lastIndex)]
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, MontikColors.SkyMid, bottom)))) {
        MontikBunny(Modifier.align(Alignment.Center).size(220.dp))
    }
}

@Composable
fun ReadyScreen(onGo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MontikColors.Cream)) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "Готов начать?",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = Color.White,
                        drawStyle = Stroke(width = 18f, join = StrokeJoin.Round)
                    ),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Готов начать?",
                    style = MaterialTheme.typography.displayLarge,
                    color = MontikColors.Ink,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Помоги Монтику принимать правильные решения и сделай его жизнь лучше!",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Sparkles(Modifier.fillMaxSize())
                ArtImage("mascot_ready", Modifier.size(300.dp), ContentScale.Fit) {
                    MontikBunny(Modifier.size(300.dp), waving = true)
                }
            }
            PillButton("Вперёд!", onGo)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Зелёные росчерки вокруг героя на экране «Готов начать?» — как в макете. */
@Composable
private fun Sparkles(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.012f)
        val color = MontikColors.LimeDeep
        fun tick(cx: Float, cy: Float, r: Float, turn: Float) {
            val path = Path().apply {
                moveTo(cx - r, cy)
                quadraticBezierTo(cx, cy - r * 1.4f, cx + r, cy)
            }
            rotate(turn, Offset(cx, cy)) { drawPath(path, color, style = stroke) }
        }
        tick(w * 0.12f, h * 0.18f, w * 0.06f, 20f)
        tick(w * 0.88f, h * 0.14f, w * 0.05f, -160f)
        tick(w * 0.10f, h * 0.68f, w * 0.05f, 200f)
        tick(w * 0.90f, h * 0.60f, w * 0.06f, -20f)
        drawCircle(color.copy(alpha = 0.35f), radius = w * 0.022f, center = Offset(w * 0.22f, h * 0.40f))
        drawCircle(color.copy(alpha = 0.35f), radius = w * 0.018f, center = Offset(w * 0.80f, h * 0.36f))
        drawOval(
            color = color.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.06f, h * 0.86f),
            size = Size(w * 0.18f, h * 0.06f)
        )
    }
}
