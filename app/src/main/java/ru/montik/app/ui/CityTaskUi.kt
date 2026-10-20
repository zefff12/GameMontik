package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.montik.app.GameViewModel
import ru.montik.app.game.CityTasks

/*
 * Задание при приезде в город (кадры «Москва», «Казань», «Санкт-Петербург», «Сочи» из макета).
 * В Москве — карта с шестью местами, в других городах — три карточки. Нажми на вариант —
 * появится его карточка из макета: доход и сколько конкурентов. «Далее» — проверить выбор.
 */

/** Разметка кадра города: варианты (x, y, w, h), круглая отметка выбора, «Далее», монеты. */
private class CityFrame(
    val art: String,
    val spots: List<FloatArray>,
    val checks: List<Pair<Float, Float>>,
    val next: FloatArray,
    val coin: Pair<Float, Float>
)

private val CITY_FRAMES = mapOf(
    // Москва: булавки на карте (МГУ, Останкино/ВДНХ, Красная площадь, ТЦ Афимолл, Парк Горького, Арбат).
    "moscow" to CityFrame(
        "fg3_city_moscow",
        listOf(
            floatArrayOf(150f, 352f, 130f, 80f),  // red — Красная площадь
            floatArrayOf(255f, 300f, 140f, 70f),  // vdnh — Останкинская башня
            floatArrayOf(40f, 300f, 110f, 70f),   // mgu
            floatArrayOf(140f, 555f, 110f, 56f),  // arbat
            floatArrayOf(20f, 440f, 130f, 80f),   // mall
            floatArrayOf(290f, 470f, 110f, 72f)   // park
        ),
        emptyList(),
        floatArrayOf(152f, 785f, 231f, 44f),
        372f to 78f
    ),
    "kazan" to CityFrame(
        "fg3_city_kazan",
        listOf(floatArrayOf(13f, 530f, 125f, 135f), floatArrayOf(145f, 530f, 125f, 135f), floatArrayOf(276f, 530f, 125f, 135f)),
        listOf(119f to 548f, 250f to 548f, 381f to 548f),
        floatArrayOf(157f, 770f, 232f, 44f),
        367f to 72f
    ),
    "spb" to CityFrame(
        "fg3_city_spb",
        listOf(floatArrayOf(10f, 490f, 128f, 153f), floatArrayOf(144f, 490f, 128f, 153f), floatArrayOf(277f, 490f, 128f, 153f)),
        listOf(116f to 510f, 249f to 510f, 382f to 510f),
        floatArrayOf(150f, 753f, 238f, 43f),
        374f to 82f
    ),
    "sochi" to CityFrame(
        "fg3_city_sochi",
        listOf(floatArrayOf(15f, 510f, 125f, 165f), floatArrayOf(146f, 510f, 122f, 165f), floatArrayOf(276f, 510f, 124f, 165f)),
        listOf(118f to 530f, 248f to 530f, 380f to 530f),
        floatArrayOf(157f, 772f, 232f, 42f),
        369f to 84f
    )
)

/** Подписи на карте Москвы (центры подписей из кадра) — вокруг выбранной рисуется зелёная рамка. */
private val MOSCOW_LABELS = listOf(
    floatArrayOf(184f, 362f, 82f, 34f),   // Красная площадь
    floatArrayOf(256f, 308f, 96f, 38f),   // Останкинская башня
    floatArrayOf(66f, 317f, 58f, 30f),    // МГУ
    floatArrayOf(160f, 567f, 66f, 30f),   // Арбат
    floatArrayOf(36f, 455f, 94f, 32f),    // ТЦ Афимолл
    floatArrayOf(306f, 486f, 76f, 38f)    // Парк Горького
)

@Composable
fun hasCityTask(city: String): Boolean =
    CITY_FRAMES[city]?.let { rememberHasArt(it.art) } == true

@Composable
fun CityTaskScreen(vm: GameViewModel, task: CityTasks.Task) {
    val frame = CITY_FRAMES.getValue(task.city)
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    val fb = rememberFeedback()
    var picked by rememberSaveable(task.city) { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = shown != null) { shown = null }
    DesignCanvas {
        FrameArt(frame.art)
        // Стрелка «назад»: задание нужно решить — Монтик напоминает.
        PressHit(14f, 30f, 56f, 56f, 28f) { vm.say("Сначала помоги Монтику выбрать — потом прогулка по городу!") }
        // Число монет — по центру пилюли справа от монеты.
        Box(Modifier.at(frame.coin.first - 23f, frame.coin.second - 12f, 48f, 24f), contentAlignment = Alignment.Center) {
            Text(spaced(coins), color = Color(0xFF14246B), fontSize = fs(if (coins >= 10000) 13f else 16f, false), fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
        }
        task.options.forEachIndexed { i, option ->
            val r = frame.spots.getOrNull(i) ?: return@forEachIndexed
            val selected = picked == option.id
            if (task.city == "moscow") {
                // На карте — светящееся кольцо вокруг выбранной булавки.
                if (selected) {
                    val l = MOSCOW_LABELS[i]
                    Box(
                        Modifier.at(l[0] - 3f, l[1] - 3f, l[2] + 6f, l[3] + 6f).clip(RoundedCornerShape(d(12f)))
                            .border(d(3f), Color(0xFF39C24A), RoundedCornerShape(d(16f)))
                            .background(Color(0x3339C24A))
                    )
                }
            } else {
                // Круглая отметка в углу карточки становится зелёной галочкой.
                val (cx, cy) = frame.checks[i]
                if (selected) {
                    Box(
                        Modifier.at(cx - 11f, cy - 11f, 22f, 22f).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF5BD85E), Color(0xFF22A12D))))
                            .border(d(2f), Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("✓", color = Color.White, fontSize = fs(13f, false), fontWeight = FontWeight.Black) }
                }
            }
            Hit(r[0], r[1], r[2], r[3]) {
                fb.tap()
                picked = option.id
                shown = option.id
            }
        }
        // «Далее»: серая, пока ничего не выбрано; зелёная, когда выбор есть.
        val n = frame.next
        if (picked != null) {
            Box(
                Modifier.at(n[0], n[1], n[2], n[3]).clip(RoundedCornerShape(d(n[3] / 2f)))
                    .background(Brush.verticalGradient(listOf(Color(0xFF63CF52), Color(0xFF2E9E3A)))),
                contentAlignment = Alignment.Center
            ) { Text("Далее  ›", color = Color.White, fontSize = fs(17f, false), fontWeight = FontWeight.Bold) }
        }
        PressHit(n[0], n[1], n[2], n[3], n[3] / 2f) {
            val id = picked
            if (id == null) vm.say("Сначала выбери вариант — нажми на него.")
            else vm.solveCityTask(id)
        }
    }
    shown?.let { id ->
        val option = task.options.firstOrNull { it.id == id }
        if (option != null) OptionCard(option.art) { shown = null }
    }
}

/** Карточка варианта из макета поверх затемнённого экрана: доход и значок конкурентов. Нажатие — закрыть. */
@Composable
private fun OptionCard(art: String, onClose: () -> Unit) {
    val appear = remember(art) { Animatable(0f) }
    LaunchedEffect(art) { appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }.background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.padding(horizontal = 18.dp).widthIn(max = 380.dp).fillMaxWidth().aspectRatio(1.18f)
                .graphicsLayer { val k = 0.94f + 0.06f * appear.value; scaleX = k; scaleY = k }
        ) { ArtImage(art, Modifier.fillMaxSize(), ContentScale.Fit) {} }
    }
}
