package ru.montik.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.montik.app.ActiveShift
import ru.montik.app.GameViewModel

/*
 * «Работа у мастера» — шесть кадров нового макета. Мастерская на фоне затемнена, сверху — карточка
 * задания (вопрос, коробки, три ответа и «Пропустить задание» нарисованы на картинке, текст не добавляем).
 * Нажимаются сами нарисованные кнопки: выбранный ответ подсвечивается зелёным или красным,
 * через миг смена засчитывается и приходит расчётный листок.
 */

/** Кадр задания: картинка, её рамка в макете и нажимаемые места (доли картинки: x, y, w, h). */
private class MasterFrame(
    val art: String,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val answers: List<FloatArray>,
    val skip: FloatArray,
    val back: FloatArray
)

private fun row3(y: Float, h: Float, xs: FloatArray, w: Float) = xs.map { floatArrayOf(it, y, w, h) }

private val MASTER_FRAMES = listOf(
    MasterFrame(
        "fg3_master1", -22f, 231f, 448f, 381f,
        row3(0.717f, 0.126f, floatArrayOf(0.086f, 0.369f, 0.65f), 0.268f),
        floatArrayOf(0.17f, 0.868f, 0.666f, 0.118f), floatArrayOf(0.12f, 0.067f, 0.11f, 0.118f)
    ),
    MasterFrame(
        "fg3_master2", -21f, 231f, 446f, 378f,
        row3(0.717f, 0.126f, floatArrayOf(0.086f, 0.369f, 0.65f), 0.268f),
        floatArrayOf(0.17f, 0.868f, 0.666f, 0.118f), floatArrayOf(0.12f, 0.067f, 0.11f, 0.118f)
    ),
    MasterFrame(
        "fg3_master3", -3f, 241f, 418f, 354f,
        row3(0.725f, 0.125f, floatArrayOf(0.05f, 0.357f, 0.654f), 0.292f),
        floatArrayOf(0.157f, 0.868f, 0.693f, 0.107f), floatArrayOf(0.093f, 0.084f, 0.093f, 0.093f)
    ),
    MasterFrame(
        "fg3_master4", -5f, 242f, 422f, 358f,
        row3(0.725f, 0.125f, floatArrayOf(0.05f, 0.357f, 0.654f), 0.292f),
        floatArrayOf(0.157f, 0.868f, 0.693f, 0.107f), floatArrayOf(0.093f, 0.084f, 0.093f, 0.093f)
    ),
    MasterFrame(
        "fg3_master5", 30f, 170f, 352f, 491f,
        row3(0.746f, 0.118f, floatArrayOf(0.04f, 0.35f, 0.66f), 0.298f),
        floatArrayOf(0.15f, 0.869f, 0.704f, 0.081f), floatArrayOf(0.04f, 0.03f, 0.1f, 0.07f)
    ),
    MasterFrame(
        "fg3_master6", 33f, 166f, 345f, 494f,
        row3(0.686f, 0.093f, floatArrayOf(0.037f, 0.352f, 0.663f), 0.298f),
        floatArrayOf(0.106f, 0.803f, 0.79f, 0.071f), floatArrayOf(0.037f, 0.036f, 0.116f, 0.071f)
    )
)

/** Есть ли кадр для этого задания мастера. */
@Composable
fun hasMasterFrame(index: Int): Boolean =
    index in MASTER_FRAMES.indices && rememberHasArt(MASTER_FRAMES[index].art, "fg3_master_bg")

@Composable
fun MasterShift(vm: GameViewModel, shift: ActiveShift) {
    val task = shift.task
    val frame = MASTER_FRAMES[task.master.coerceIn(0, MASTER_FRAMES.lastIndex)]
    val scope = rememberCoroutineScope()
    val fb = rememberFeedback()
    var picked by remember(task) { mutableIntStateOf(-1) }
    val enter = rememberEnter()
    DesignCanvas {
        FrameArt("fg3_master_bg")
        // Мастерская под карточкой затемнена, как в макете (Rectangle 292).
        ScreenDim(0.4f)
        Box(Modifier.at(frame.x, frame.y, frame.w, frame.h).graphicsLayer { alpha = enter.value }) {
            ArtImage(frame.art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
        }
        fun zone(z: FloatArray) = floatArrayOf(frame.x + z[0] * frame.w, frame.y + z[1] * frame.h, z[2] * frame.w, z[3] * frame.h)

        frame.answers.forEachIndexed { i, a ->
            val r = zone(a)
            val shown by animateFloatAsState(if (picked == i) 1f else 0f, tween(180), label = "ans$i")
            if (shown > 0f) {
                val ok = i == task.correctIndex
                val tint = if (ok) Color(0xFF3FA944) else Color(0xFFE0574B)
                Box(
                    Modifier.at(r[0], r[1], r[2], r[3])
                        .graphicsLayer { alpha = shown }
                        .clip(RoundedCornerShape(d(16f)))
                        .background(tint.copy(alpha = 0.22f))
                        .border(d(3f), tint, RoundedCornerShape(d(16f)))
                )
            }
            PressHit(r[0], r[1], r[2], r[3], 16f, enabled = picked < 0) {
                picked = i
                if (i == task.correctIndex) fb.coin() else fb.tap()
                scope.launch {
                    delay(650)
                    vm.answerTask(i)
                }
            }
        }
        val sk = zone(frame.skip)
        PressHit(sk[0], sk[1], sk[2], sk[3], sk[3] / 2f, enabled = picked < 0) { vm.skipTask() }
        val bk = zone(frame.back)
        PressHit(bk[0], bk[1], bk[2], bk[3], bk[3] / 2f) { vm.cancelShift() }
    }
}
