package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.game.AdOffer
import ru.montik.app.game.Ads

/*
 * «Рекламное предложение» — кадры макета с червяком-продавцом. Картинка показывается точно на том месте,
 * где она стоит в кадре 412×917, поверх затемнённого экрана. Цена в звезде «ВЫГОДА» — своя (зависит от уровня
 * Монтика): в картинке старое число стёрто, новое рисуется кодом тем же стилем. Крестик — закрыть и сберечь
 * деньги, «КУПИТЬ» — потратить. Файлы: fg_ad_car, fg_ad_car2, fg_ad_car3, fg_ad_headphones, fg_ad_thing, fg_ad_phone.
 */

/** Где что лежит на картинке рекламы — в долях её ширины и высоты (0..1). */
private class AdLayout(
    /** Рамка картинки в кадре макета. */
    val x: Float, val y: Float, val size: Float,
    /** Кнопка «КУПИТЬ». */
    val buy: FloatArray,
    /** Центр крестика на картинке (null — крестика нет, рисуем свой в углу). */
    val close: Pair<Float, Float>?,
    /** Где стоит цена в звезде и под каким наклоном. */
    val price: FloatArray,
    val tilt: Float
)

private val LAYOUTS = mapOf(
    // Доли сняты с картинок макета (1254×1254): кнопка «КУПИТЬ», крестик, место старых цифр в звезде «ВЫГОДА».
    "car" to AdLayout(28f, 260f, 356f, floatArrayOf(0.045f, 0.775f, 0.55f, 0.915f), 0.925f to 0.156f, floatArrayOf(0.625f, 0.180f, 0.940f, 0.338f), -5f),
    "car_mood" to AdLayout(23f, 265f, 365f, floatArrayOf(0.48f, 0.78f, 0.955f, 0.92f), 0.928f to 0.110f, floatArrayOf(0.088f, 0.622f, 0.394f, 0.772f), -6f),
    "car_worm" to AdLayout(19f, 254f, 374f, floatArrayOf(0.055f, 0.775f, 0.54f, 0.915f), null, floatArrayOf(0.100f, 0.494f, 0.428f, 0.660f), -7f),
    "headphones" to AdLayout(20f, 257f, 371f, floatArrayOf(0.50f, 0.765f, 0.93f, 0.905f), 0.940f to 0.120f, floatArrayOf(0.082f, 0.647f, 0.380f, 0.796f), -6f),
    "favourite" to AdLayout(25f, 255f, 362f, floatArrayOf(0.28f, 0.79f, 0.80f, 0.925f), null, floatArrayOf(0.084f, 0.487f, 0.380f, 0.660f), -8f),
    "phone" to AdLayout(35f, 266f, 342f, floatArrayOf(0.045f, 0.80f, 0.57f, 0.95f), null, floatArrayOf(0.077f, 0.492f, 0.376f, 0.657f), -8f)
)

private val FALLBACK = AdLayout(28f, 260f, 356f, floatArrayOf(0.08f, 0.78f, 0.60f, 0.92f), null, floatArrayOf(0.60f, 0.20f, 0.92f, 0.34f), -6f)

@Composable
fun AdOverlay(vm: GameViewModel, ad: AdOffer) {
    val price = Ads.price(vm.state, ad)
    val l = LAYOUTS[ad.id] ?: FALLBACK
    val hasArt = rememberHasArt(ad.art)
    val fb = rememberFeedback()
    // «Назад» работает как крестик: реклама закрыта, деньги на месте.
    BackHandler(enabled = true) { vm.declineAd() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            fun fx(f: Float) = l.x + f * l.size
            fun fy(f: Float) = l.y + f * l.size

            // Честная подпись: это реклама внутри игры, а не настоящая.
            HudChip("📣 Реклама · учебная ситуация", l.x, l.y - 46f, 250f, 34f)

            if (hasArt) {
                Art(ad.art, l.x, l.y, l.size, l.size, ContentScale.Fit)
            } else {
                // Запасной вариант, если картинки нет: простая яркая карточка.
                Box(
                    Modifier
                        .at(l.x, l.y, l.size, l.size)
                        .clip(RoundedCornerShape(d(28f)))
                        .background(Color(0xFFFFF36B))
                        .border(d(5f), Color.Black, RoundedCornerShape(d(28f)))
                )
                DText("КУПИ", l.x + 20f, l.y + 24f, 200f, 56f, color = Color(0xFFE53935), mono = false)
                DText(ad.slogan, l.x + 20f, l.y + 96f, l.size - 40f, 20f, mono = false)
                DText(ad.emoji, l.x + l.size - 150f, l.y + 150f, 130f, 90f, mono = false)
                Box(
                    Modifier
                        .at(fx(l.buy[0]), fy(l.buy[1]), (l.buy[2] - l.buy[0]) * l.size, (l.buy[3] - l.buy[1]) * l.size)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center
                ) { Text("🛒 КУПИТЬ", color = Color.White, fontSize = fs(24f, false), fontWeight = FontWeight.Black) }
            }

            // Цена в звезде «ВЫГОДА»: белые цифры с чёрной обводкой, как в макете.
            val pw = (l.price[2] - l.price[0]) * l.size
            val ph = (l.price[3] - l.price[1]) * l.size
            Box(
                Modifier.at(fx(l.price[0]), fy(l.price[1]), pw, ph).rotate(l.tilt),
                contentAlignment = Alignment.Center
            ) {
                // Высота цифр как у стёртых «1500»: четыре знака чуть мельче, чтобы влезли в звезду.
                val size = fs(if (price >= 1000) ph * 0.72f else ph * 0.86f, false)
                val strokePx = with(LocalDensity.current) { d(7f).toPx() }
                Text(
                    "$price",
                    style = TextStyle(
                        fontSize = size,
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "$price",
                    style = TextStyle(fontSize = size, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // «КУПИТЬ».
            Hit(fx(l.buy[0]), fy(l.buy[1]), (l.buy[2] - l.buy[0]) * l.size, (l.buy[3] - l.buy[1]) * l.size) { if (vm.state.coins >= price) fb.coin() else fb.bad(); vm.buyAd() }

            // Крестик: свой на картинке или нарисованный в углу.
            val close = l.close
            if (close != null && hasArt) {
                Hit(fx(close.first) - 26f, fy(close.second) - 26f, 52f, 52f) { fb.good(); vm.declineAd() }
            } else {
                Box(
                    Modifier
                        .at(l.x + l.size - 30f, l.y - 22f, 48f, 48f)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(d(3f), Color.Black, CircleShape)
                        .clickable { fb.good(); vm.declineAd() },
                    contentAlignment = Alignment.Center
                ) { Text("✕", color = Color.Black, fontSize = fs(24f, false), fontWeight = FontWeight.Black) }
            }

            DText(
                "Цена: $price монет · в кошельке ${vm.state.coins}",
                l.x, l.y + l.size + 14f, l.size, 16f, mono = false, color = Color.White, align = TextAlign.Center
            )
        }
    }
}
