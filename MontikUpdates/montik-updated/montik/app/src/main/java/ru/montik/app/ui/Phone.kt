package ru.montik.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen

private class PhoneApp(
    val emoji: String,
    val label: String,
    val badge: String? = null,
    val onOpen: () -> Unit
)

/**
 * Телефон Монтика — главное меню игры по макету: лаймовый фон, приветствие
 * и сетка приложений «Карта, Работа, Прогресс, Банк, Магазин, Профиль, Настройки, Будильник».
 */
@Composable
fun PhoneScreen(vm: GameViewModel) {
    val s = vm.state
    val apps = listOf(
        PhoneApp("🗺", "Карта") { vm.goTo(Screen.Travel) },
        PhoneApp("💼", "Работа", if (vm.workBlocker != null) "!" else null) { vm.goTo(Screen.Work) },
        PhoneApp("📈", "Прогресс") { vm.goTo(Screen.Diary) },
        PhoneApp("🏦", "Банк", if (s.debt > 0) "долг" else null) { vm.goTo(Screen.Bank) },
        PhoneApp("🛍", "Магазин") { vm.goTo(Screen.Grocery) },
        PhoneApp("🎨", "Профиль") { vm.startRedraw() },
        PhoneApp("⚙️", "Настройки") { vm.openParent() },
        PhoneApp("⏰", "Будильник") { vm.goTo(Screen.Sleep) }
    )
    // Телефон из Figma, если плитки fg_phone_i1…i8 добавлены в проект; иначе — нарисованный.
    val names = Array(8) { "fg_phone_i${it + 1}" }
    if (rememberHasArt(*names)) FigmaPhone(vm, apps) else ClassicPhone(vm, apps)
}

@Composable
private fun ClassicPhone(vm: GameViewModel, apps: List<PhoneApp>) {
    val s = vm.state
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MontikColors.PhoneTop, MontikColors.PhoneBottom))),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackCircle({ vm.back() })
                Spacer(Modifier.weight(1f))
                CoinPill(s.coins)
            }
            Spacer(Modifier.height(20.dp))
            Text("Привет!", style = MaterialTheme.typography.displayLarge, color = MontikColors.Ink)
            Text(
                "Хорошего дня ☺",
                style = MaterialTheme.typography.bodyLarge,
                color = MontikColors.Ink.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(20.dp))

            for (row in apps.chunked(3)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (app in row) AppTile(app, Modifier.weight(1f))
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Лучше с каждым днём ♥",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.Ink.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppTile(app: PhoneApp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .aspectRatio(0.88f)
            .clip(MontikShapes.Tile)
            .clickable(onClick = app.onOpen),
        shape = MontikShapes.Tile,
        color = MontikColors.Cream,
        shadowElevation = 4.dp
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(app.emoji, fontSize = 38.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.Ink,
                    textAlign = TextAlign.Center
                )
            }
            if (app.badge != null) {
                Surface(
                    shape = MontikShapes.Chip,
                    color = MontikColors.Bad,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    Text(
                        app.badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Телефон по макету: лаймовый фон, «Привет!», сетка плиток 3×3 (плитки — картинки из Figma
 * с подписями внутри). Плитки 121×127 с шагом 131×141, как в кадре телефона.
 */
@Composable
private fun FigmaPhone(vm: GameViewModel, apps: List<PhoneApp>) {
    DesignCanvas(
        background = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFBDF88A), Color(0xFFB2F276))))
            )
        }
    ) {
        // Глянцевые «капли» справа, как на макете.
        SoftGlow(cx = 360f, cy = 70f, w = 220f, h = 200f, color = Color(0xFFD6FF8E), peak = 0.85f)
        SoftGlow(cx = 380f, cy = 860f, w = 320f, h = 280f, color = Color(0xFF9CEC4F), peak = 0.9f)

        Box(Modifier.at(14f, 14f)) { BackCircle({ vm.back() }) }
        Box(Modifier.at(230f, 18f, 168f, 44f), contentAlignment = Alignment.CenterEnd) {
            CoinPill(vm.state.coins)
        }

        DText("Привет!", 18f, 84f, 320f, 44f, mono = false, lineHeight = 1.1f)
        DText("Хорошего дня ☺", 18f, 142f, 320f, 20f, bold = false, mono = false)

        apps.forEachIndexed { i, app ->
            val x = 14.5f + (i % 3) * 131f
            val y = 200f + (i / 3) * 141f
            Box(
                Modifier
                    .at(x, y, 121f, 127f)
                    .clip(RoundedCornerShape(d(20f)))
                    .clickable(onClick = app.onOpen)
            ) {
                ArtImage("fg_phone_i${i + 1}", Modifier.fillMaxSize(), ContentScale.Crop) {}
                if (app.badge != null) {
                    Surface(
                        shape = MontikShapes.Chip,
                        color = MontikColors.Bad,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Text(
                            app.badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        DText("Лучше с каждым днём ♥", 18f, 846f, 240f, 16f, bold = false, mono = false)
    }
}
