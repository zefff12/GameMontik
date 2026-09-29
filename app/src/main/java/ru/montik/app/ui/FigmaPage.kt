package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Внутренние экраны в новом стиле макета Figma (кадры «Frame 19–25»: Копилка, Работа, Жильё, План бюджета,
 * Задания, Подушка): кремовый фон, белые карточки со скруглением 24 и тонкой зелёной рамкой, иконка слева,
 * круглая стрелка справа, светлые пилюли-кнопки и рисованный «хвостик» внизу страницы.
 * Иконки и рисунки — файлы fg_ui_* (вырезаны из кадров макета).
 */

object Page {
    val Bg = Color(0xFFFDFCF7)
    val CardBorder = Color(0xFFE3EDD8)
    val Mint = Color(0xFFF0F7E7)
    val Sky = Color(0xFFF3F9FE)
    val Leaf = Color(0xFFF6FBF3)
    val Sand = Color(0xFFFBFAF1)
    val Lilac = Color(0xFFF4F2FD)
    val Pink = Color(0xFFFFF4F1)
    val PillGreen = Color(0xFFBFE6AC)
    val PillBorder = Color(0xFF8CC578)
    val Outline = Color(0xFF7DB46C)
    val Arrow = Color(0xFFEDF2EA)
    val HighlightGreen = Color(0xFFDDF0DF)
    val HighlightLilac = Color(0xFFE8E2FA)
    val HighlightPeach = Color(0xFFFCE9DC)
}

/** Картинка из макета, а если файла нет — эмодзи того же размера. */
@Composable
fun FigIcon(name: String?, fallback: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (name == null) {
            Text(fallback, fontSize = (size.value * 0.55f).sp)
        } else {
            ArtImage(name, Modifier.size(size), ContentScale.Fit) {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    Text(fallback, fontSize = (size.value * 0.55f).sp)
                }
            }
        }
    }
}

/** Картинка-фото (город, комната) в скруглённой рамке. */
@Composable
fun FigPhoto(name: String, fallback: String, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width, height)
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Page.Mint),
        contentAlignment = Alignment.Center
    ) {
        ArtImage(name, Modifier.size(width, height), ContentScale.Crop) {
            Text(fallback, fontSize = (height.value * 0.45f).sp)
        }
    }
}

/** Круглая стрелка «>» справа на карточке. */
@Composable
fun ArrowCircle(modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).clip(CircleShape).background(Page.Arrow), contentAlignment = Alignment.Center) {
        Text("›", color = MontikColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Карточка макета: иконка слева, текст по центру, стрелка справа (если карточка нажимается).
 * [below] — то, что идёт на всю ширину под строкой (кнопки, шкалы).
 */
@Composable
fun IconCard(
    icon: String?,
    fallback: String,
    modifier: Modifier = Modifier,
    color: Color = MontikColors.Surface,
    iconSize: Dp = 64.dp,
    photo: Boolean = false,
    arrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    MontikCard(modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier, color = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo && icon != null) FigPhoto(icon, fallback, iconSize, iconSize)
            else FigIcon(icon, fallback, iconSize)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), content = content)
            if (arrow) {
                Spacer(Modifier.width(8.dp))
                ArrowCircle()
            }
        }
        if (below != null) {
            Spacer(Modifier.height(10.dp))
            below()
        }
    }
}

/** Заголовок раздела на цветной подложке с иконкой — как «Планирование бюджета» и «Сбережения» в макете. */
@Composable
fun SectionTitle(text: String, icon: String?, fallback: String, highlight: Color = Page.HighlightGreen) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FigIcon(icon, fallback, 44.dp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(highlight)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
        }
    }
}

/** Строка «иконка — подпись — значение», как «Аренда … 150» на экране «Жильё». */
@Composable
fun IconLine(icon: String?, fallback: String, left: String, right: String, rightColor: Color = MontikColors.Ink) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        FigIcon(icon, fallback, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MontikColors.Ink)
        Text(right, style = MaterialTheme.typography.titleMedium, color = rightColor, textAlign = TextAlign.End)
    }
}

/** Светлая пилюля-кнопка макета: зелёная (главная) или с зелёной обводкой. */
@Composable
fun PagePill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    icon: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = 46.dp)
            .shadow(if (enabled) 2.dp else 0.dp, shape)
            .clip(shape)
            .background(if (!enabled) MontikColors.Track else if (filled) Page.PillGreen else Color.White)
            .border(1.5.dp, if (!enabled) MontikColors.Track else if (filled) Page.PillBorder else Page.Outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            FigIcon(icon, "", 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            color = if (enabled) MontikColors.Ink else MontikColors.InkSoft,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

/** Ряд пилюль одинаковой ширины (+10 / +20 / +50 / Всё). */
@Composable
fun PillRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Рисованный низ страницы из макета (листики, самолётик и надпись), от края до края. */
@Composable
fun PageFooter(name: String) {
    ArtImage(
        name,
        Modifier
            .layout { measurable, constraints ->
                // Ширина не ограничена (например, в горизонтальной прокрутке) — рисуем как есть, без выхода за поля.
                if (!constraints.hasBoundedWidth) {
                    val p = measurable.measure(constraints)
                    return@layout layout(p.width, p.height) { p.place(0, 0) }
                }
                val extra = 17.dp.roundToPx() * 2
                val w = constraints.maxWidth + extra
                val p = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(constraints.maxWidth, p.height) { p.place(-extra / 2, 0) }
            }
            .fillMaxWidth(),
        ContentScale.FillWidth
    ) {}
}

// ───────────────────────── Какая картинка к чему ─────────────────────────

fun goalArt(id: String): String? = when (id) {
    "trip_moscow" -> "fg_ui_g_moscow"
    "trip_kazan" -> "fg_ui_g_kazan"
    "trip_spb" -> "fg_ui_g_spb"
    "trip_sochi" -> "fg_ui_g_sochi"
    "trip_nn" -> "fg_trip_thumb_nn"
    "bike" -> "fg_ui_g_bike"
    "console" -> "fg_ui_g_console"
    else -> null
}

/** Фотографии городов — в рамке, остальные цели — наклейки. */
fun goalIsPhoto(id: String): Boolean = id == "trip_moscow" || id == "trip_kazan" || id == "trip_spb" || id == "trip_nn"

fun goalTint(id: String): Color = when (id) {
    "trip_moscow", "trip_kazan" -> Page.Mint
    "trip_spb", "trip_nn" -> Page.Sky
    "trip_sochi" -> Page.Leaf
    "bike" -> Page.Sand
    "console" -> Page.Lilac
    else -> MontikColors.Surface
}

fun taskArt(id: String): String? = when (id) {
    "t_split_week" -> "fg_ui_t_env"
    "t_pick_birthday" -> "fg_ui_t_gift"
    "t_split_salary" -> "fg_ui_t_case"
    "t_count_goal" -> "fg_ui_t_bike"
    "t_count_cushion" -> "fg_ui_t_buoy"
    "t_pick_piggy" -> "fg_ui_t_pig"
    "t_basket_list", "t_basket_price" -> "fg_ui_b_cart"
    "t_count_change" -> "fg_ui_b_coins"
    "t_pick_ad" -> "fg_ui_b_gift"
    else -> null
}
