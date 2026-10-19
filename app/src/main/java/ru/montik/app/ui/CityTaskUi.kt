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
            floatArrayOf(186f, 383f, 92f, 42f),  // red
            floatArrayOf(262f, 328f, 112f, 42f), // vdnh (Останкинская башня)
            floatArrayOf(60f, 334f, 78f, 34f),   // mgu
            floatArrayOf(166f, 600f, 76f, 34f),  // arbat
            floatArrayOf(34f, 482f, 100f, 36f),  // mall
            floatArrayOf(310f, 518f, 82f, 46f)   // park
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
        Box(Modifier.at(frame.coin.first - 18f, frame.coin.second - 10f, 40f, 20f), contentAlignment = Alignment.Center) {
            Text("$coins", color = Color(0xFF14246B), fontSize = fs(if (coins >= 1000) 13f else 16.5f, false), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        task.options.forEachIndexed { i, option ->
            val r = frame.spots.getOrNull(i) ?: return@forEachIndexed
            val selected = picked == option.id
            if (task.city == "moscow") {
                // На карте — светящееся кольцо вокруг выбранной булавки.
                if (selected) {
                    Box(
                        Modifier.at(r[0] - 4f, r[1] - 4f, r[2] + 8f, r[3] + 8f).clip(RoundedCornerShape(d(16f)))
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
