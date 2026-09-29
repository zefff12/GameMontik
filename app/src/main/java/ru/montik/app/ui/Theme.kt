package ru.montik.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.R

/**
 * Единственное место, где заданы цвета, скругления и шрифты.
 * Значения взяты из макета Figma «Монтик»: тёмно-синий текст #0A3652, лаймовая кнопка #C1FA86,
 * кремовый фон #FEFFFA, белые карточки со скруглением 20.
 * Размеры совпадают с макетом один к одному: кадр макета 412×917 — это обычный телефон, поэтому
 * числа из Figma используются как dp без пересчёта.
 */
object MontikColors {
    // Основные цвета макета
    val Ink = Color(0xFF0A3652)          // весь текст и обводки
    val InkSoft = Color(0xFF5E7D92)      // подписи и пояснения
    val Lime = Color(0xFFC1FA86)         // главная кнопка
    val LimeDeep = Color(0xFF7ED957)     // нажатая кнопка, акценты
    val Cream = Color(0xFFFEFFFA)        // фон экранов
    val Surface = Color(0xFFFFFFFF)      // белые карточки
    val SurfaceTint = Color(0xFFEFF8E4)  // светло-лаймовая карточка-пояснение

    // Экран телефона
    val PhoneTop = Color(0xFFC6F792)
    val PhoneBottom = Color(0xFF9FE96A)

    // Игровые цвета
    val Coin = Color(0xFFF5B31B)
    val CoinDark = Color(0xFFD98B0A)
    val Plus = Color(0xFF3FBF46)
    val Good = Color(0xFF3FAE49)
    val Warn = Color(0xFFF5A80F)
    val Bad = Color(0xFFE5554A)
    val Track = Color(0xFFE2EBE0)

    // Круглые кнопки на главном экране (как в макете: цветной шар с белой обводкой)
    val Hunger = Color(0xFFFF8A3D)
    val HungerDeep = Color(0xFFF4552B)
    val Sleep = Color(0xFFB06BE8)
    val SleepDeep = Color(0xFF8A3FD1)
    val Energy = Color(0xFFFFD84D)
    val EnergyDeep = Color(0xFFF5A80F)
    val Phone = Color(0xFF6FD95B)
    val PhoneDeep = Color(0xFF35A83A)
    val Wallet = Color(0xFFFF7EC0)
    val WalletDeep = Color(0xFFE0409A)

    // Запасные фоны, пока иллюстрации из макета не добавлены (см. docs/DESIGN_ASSETS.md)
    val SkyTop = Color(0xFF8FC6F0)
    val SkyMid = Color(0xFFCDE6F7)
    val SkyLow = Color(0xFFF3DFC0)
    val RoomWall = Color(0xFFE8E4DC)
    val RoomFloor = Color(0xFFC9A074)

    /** Заливка круглой кнопки: сверху светлее, снизу насыщеннее. */
    fun ball(light: Color, deep: Color) = Brush.verticalGradient(listOf(light, deep))
}

object MontikShapes {
    /** Белая карточка из макета. */
    val Card = RoundedCornerShape(20.dp)
    /** Кнопка-пилюля: в макете 323×73 со скруглением 36.5. */
    val Button = RoundedCornerShape(50)
    val Chip = RoundedCornerShape(50)
    val Panel = RoundedCornerShape(28.dp)
    /** Плитка приложения на экране телефона. */
    val Tile = RoundedCornerShape(22.dp)
}

/** Размеры из макета: кнопка 323×73, отступ по краям 44. */
object MontikSizes {
    val ButtonHeight = 73.dp
    val ButtonMaxWidth = 323.dp
    val ScreenPadding = 24.dp
    val CardWidth = 378.dp
}

/**
 * Шрифт всей игры — как Arial. Сам Arial — платный шрифт Microsoft/Monotype, вшивать его в приложение нельзя,
 * поэтому взят Liberation Sans: он сделан по размерам Arial буква в букву и выглядит так же
 * (res/font/liberation_sans*.ttf, лицензия SIL OFL 1.1 — docs/fonts/OFL-LiberationSans.txt).
 */
val MontikFont: FontFamily = FontFamily(
    Font(R.font.liberation_sans, FontWeight.Normal),
    Font(R.font.liberation_sans_bold, FontWeight.Bold)
)

private val Mono = MontikFont

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp)
)

private val scheme = lightColorScheme(
    primary = MontikColors.Lime,
    onPrimary = MontikColors.Ink,
    secondary = MontikColors.LimeDeep,
    onSecondary = MontikColors.Ink,
    background = MontikColors.Cream,
    onBackground = MontikColors.Ink,
    surface = MontikColors.Surface,
    onSurface = MontikColors.Ink,
    surfaceVariant = MontikColors.SurfaceTint,
    onSurfaceVariant = MontikColors.Ink,
    outline = MontikColors.Ink,
    error = MontikColors.Bad
)

private val shapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = MontikShapes.Card,
    large = MontikShapes.Panel
)

@Composable
fun MontikTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
