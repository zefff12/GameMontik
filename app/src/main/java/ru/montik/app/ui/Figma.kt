package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * Оформление «как в Figma». Кадр макета — 412×917. Экраны ниже рисуются в координатах Figma
 * (x, y, ширина, высота — те же числа, что в макете), а DesignCanvas сам подгоняет кадр под
 * экран телефона или планшета. Фоновые иллюстрации растягиваются на весь экран.
 *
 * Иллюстрации лежат в res/drawable-nodpi под именами fg_*.webp / fg_*.png (список — в
 * docs/DESIGN_ASSETS.md). Пока их нет, игра показывает прежние нарисованные экраны.
 */

const val DESIGN_W = 412f
const val DESIGN_H = 917f

/**
 * Поправка кегля для надписей, которые в макете набраны Ubuntu Mono (буква шириной 0,5 кегля).
 * Теперь вся игра пишет шрифтом Pangolin (MontikFont): его буква в среднем 0,47 кегля — строки
 * переносятся так же; кегль чуть уменьшен (0,92), потому что буквы Pangolin выше.
 */
private const val MONO_SCALE = 0.9f

/** Arial шире рукописного Pangolin, под который подбирались размеры: чуть уменьшаем, чтобы строки не вылезали. */
private const val SANS_SCALE = 0.94f

@Stable
class DesignScope(
    val u: Float,
    private val density: Density,
    /** Сколько (в единицах макета) экран шире холста с каждой стороны. */
    val bleed: Float = 0f
) {
    /** Число из Figma → размер в dp. */
    fun d(v: Float): Dp = (v * u).dp

    /** Размер шрифта из Figma → sp (не зависит от масштаба шрифта в настройках телефона). */
    fun fs(v: Float, mono: Boolean = true): TextUnit =
        with(density) { (v * u * (if (mono) MONO_SCALE else SANS_SCALE)).dp.toSp() }

    /**
     * Рамка x, y, w, h из макета. wrapContentSize(TopStart, unbounded) нужен для картинок больше
     * кадра (улица 768×1152, касса 520×924…): без него Compose центрирует такой элемент и он
     * съезжает влево-вверх на половину «лишнего» размера, а надписи поверх — нет.
     */
    fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier {
        // Фон во всю ширину кадра (картинка сцены, затемнение) продлеваем на поля по бокам экрана,
        // чтобы не было полос. Персонажи и кнопки не трогаем — они не растягиваются.
        val full = bleed > 0f && x <= 0.5f && x + w >= DESIGN_W - 0.5f
        val xx = if (full) x - bleed else x
        val ww = if (full) w + 2f * bleed else w
        return this.absoluteOffset(d(xx), d(y))
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(d(ww), d(h))
    }

    fun Modifier.at(x: Float, y: Float): Modifier = this.absoluteOffset(d(x), d(y))
}

/**
 * Холст 412×917. [background] занимает весь экран (небо, комната), [content] рисуется
 * в координатах Figma по центру, внутри безопасной области экрана.
 */
@Composable
fun DesignCanvas(
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit = {},
    content: @Composable DesignScope.() -> Unit
) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        background()
        // Холст — на весь экран (как кадр 412×917 в макете, строка состояния поверх картинки),
        // а не только в «безопасной» области: иначе картинка кадра становится уже экрана и по бокам
        // видны швы фона. Если пропорции экрана почти как у макета (современные телефоны), холст
        // заполняет экран целиком («cover»), обрезая по краям не больше ~3%; иначе вписывается целиком.
        // Низ холста — над системной панелью навигации (кнопки «◁ ○ ▢»), чтобы нижние кнопки игры
        // не уходили под неё. Фон ([background]) по-прежнему на весь экран.
        val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = navBottom), contentAlignment = Alignment.Center) {
            val fitW = maxWidth.value / DESIGN_W
            val fitH = maxHeight.value / DESIGN_H
            val u = if (fitW >= fitH && fitW / fitH <= 1.15f) {
                // Экран чуть шире макета (так бывает из-за панели навигации): холст занимает всю высоту,
                // пропорции не меняются (персонажи не сплющиваются), а фон сцены продлевается на поля.
                fitH
            } else {
                val fit = minOf(fitW, fitH)
                val cover = maxOf(fitW, fitH)
                if (cover / fit <= 1.06f) cover else fit
            }
            val bleed = ((maxWidth.value / u - DESIGN_W) / 2f).coerceAtLeast(0f)
            val scope = remember(u, density, bleed) { DesignScope(u, density, bleed) }
            Box(Modifier.requiredSize((DESIGN_W * u).dp, (DESIGN_H * u).dp)) {
                scope.content()
            }
        }
    }
}

/** Есть ли в приложении все перечисленные картинки из макета. */
@Composable
fun rememberHasArt(vararg names: String): Boolean {
    val context = LocalContext.current
    val key = names.toList()
    return remember(key) {
        key.all { context.resources.getIdentifier(it, "drawable", context.packageName) != 0 }
    }
}

/** Картинка из макета в рамке x, y, w, h. */
@Composable
fun DesignScope.Art(
    name: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    scale: ContentScale = ContentScale.Crop
) {
    // Фон, продлённый на поля экрана, растягиваем по рамке: так нарисованное на картинке остаётся
    // на своих местах относительно кнопок поверх неё.
    val full = bleed > 0f && x <= 0.5f && x + w >= DESIGN_W - 0.5f
    ArtImage(name, Modifier.at(x, y, w, h), if (full) ContentScale.FillBounds else scale) {}
}

/** Невидимая область нажатия в координатах макета. */
@Composable
fun DesignScope.Hit(x: Float, y: Float, w: Float, h: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .at(x, y, w, h)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

/** Текст макета: Ubuntu Mono Bold (запасной — системный моноширинный) или обычный. */
@Composable
fun DesignScope.DText(
    text: String,
    x: Float,
    y: Float,
    w: Float,
    size: Float,
    bold: Boolean = true,
    color: Color = MontikColors.Ink,
    align: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    mono: Boolean = true,
    lineHeight: Float = 1.15f
) {
    Text(
        text = text,
        modifier = Modifier.at(x, y).requiredWidth(d(w)),
        color = color,
        fontSize = fs(size, mono),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = MontikFont,
        textAlign = align,
        lineHeight = fs(size * lineHeight, false),
        softWrap = softWrap
    )
}

/**
 * Мягкое пятно (в макете это размытые эллипсы: свечение за логотипом, тень под героем).
 * Центр в (cx, cy), размер w×h, можно повернуть.
 */
@Composable
fun DesignScope.SoftGlow(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    color: Color,
    peak: Float = 1f,
    rotate: Float = 0f
) {
    Canvas(Modifier.at(cx - w / 2f, cy - h / 2f, w, h).graphicsLayer { rotationZ = rotate }) {
        val r = size.width / 2f
        val squash = size.height / size.width
        scale(1f, squash, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = peak),
                    0.55f to color.copy(alpha = peak * 0.9f),
                    1f to color.copy(alpha = 0f),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }
    }
}
