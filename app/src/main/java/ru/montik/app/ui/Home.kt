package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.GameEngine

/**
 * Главный экран по макету «переезд»: комната во весь экран, монеты сверху справа,
 * плитка «Работа» слева и столбик круглых кнопок справа —
 * Голод, Сон, Усталость, Телефон и Кошелёк.
 */
@Composable
fun HomeScreen(vm: GameViewModel) {
    val s = vm.state
    Box(Modifier.fillMaxSize()) {
        ArtImage("bg_room", Modifier.fillMaxSize()) { RoomBackdrop(Modifier.fillMaxSize()) }

        // Герой живёт в этой комнате.
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
            MontikView(vm.sprite, s, 230.dp, heroPalette(s), Modifier.padding(bottom = 92.dp))
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Монеты и кнопка «заработать ещё».
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                CoinPill(s.coins)
                Spacer(Modifier.width(8.dp))
                PlusButton { vm.goTo(Screen.Work) }
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().weight(1f)) {
                // Слева — работа.
                WorkTile(highlighted = vm.workBlocker == null) { vm.goTo(Screen.Work) }
                Spacer(Modifier.weight(1f))
                // Справа — столбик круглых кнопок из макета.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundAction("🍔", "ГОЛОД", MontikColors.Hunger, MontikColors.HungerDeep, s.food) { vm.goTo(Screen.Shop) }
                    RoundAction("💤", "СОН", MontikColors.Sleep, MontikColors.SleepDeep, null) { vm.goTo(Screen.Sleep) }
                    RoundAction("⚡", "УСТАЛОСТЬ", MontikColors.Energy, MontikColors.EnergyDeep, s.energy) { vm.goTo(Screen.Sleep) }
                    RoundAction("📱", "ТЕЛЕФОН", MontikColors.Phone, MontikColors.PhoneDeep, null) { vm.goTo(Screen.Phone) }
                    RoundAction("👛", "КОШЕЛЁК", MontikColors.Wallet, MontikColors.WalletDeep, null) { vm.goTo(Screen.Cushion) }
                }
            }

            // Текущая цель игры — короткой белой полоской внизу.
            Surface(
                shape = MontikShapes.Chip,
                color = Color.White.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    GameEngine.currentGoal(s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/** Зелёная круглая кнопка «+» рядом с монетами. */
@Composable
private fun PlusButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(MontikColors.Phone, MontikColors.PhoneDeep)))
            .border(3.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("+", fontSize = 26.sp, color = Color.White)
    }
}

/** Плитка «Работа» в левой части комнаты, как в макете. */
@Composable
private fun WorkTile(highlighted: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(62.dp).clip(MontikShapes.Tile).clickable(onClick = onClick),
            shape = MontikShapes.Tile,
            color = MontikColors.Cream,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (highlighted) MontikColors.LimeDeep else MontikColors.Track)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("💼", fontSize = 30.sp) }
        }
        Spacer(Modifier.height(4.dp))
        LabelChip("РАБОТА")
    }
}

/**
 * Круглая кнопка из макета: цветной шар с белой обводкой, значок внутри и подпись под ним.
 * Если передан [value], вокруг шара рисуется тонкое кольцо — сколько осталось.
 */
@Composable
private fun RoundAction(
    emoji: String,
    label: String,
    light: Color,
    deep: Color,
    value: Int?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (value != null) {
                Canvas(Modifier.size(62.dp)) {
                    val ring = Stroke(width = size.width * 0.07f, cap = StrokeCap.Round)
                    val inset = size.width * 0.035f
                    drawArc(
                        color = Color.White.copy(alpha = 0.55f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = ring
                    )
                    drawArc(
                        color = if (value < 25) MontikColors.Bad else Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * (value.coerceIn(0, 100) / 100f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = ring
                    )
                }
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(light, deep)))
                    .border(3.dp, Color.White, CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(3.dp))
        LabelChip(label)
    }
}

/** Кремовая подпись-плашка под круглой кнопкой. */
@Composable
private fun LabelChip(text: String) {
    Surface(shape = MontikShapes.Chip, color = MontikColors.Cream, shadowElevation = 2.dp) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MontikColors.Ink,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Запасная комната, пока картинка из макета не добавлена. */
@Composable
private fun RoomBackdrop(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(MontikColors.RoomWall)) {
        // Пол.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.28f)
                .background(Brush.verticalGradient(listOf(MontikColors.RoomFloor, Color(0xFFB8895E))))
        )
        // Окно с городом.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 26.dp)
                .size(width = 132.dp, height = 210.dp)
                .background(Color.White)
                .border(6.dp, Color.White)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF7EC8F5), Color(0xFFCDE9FA))))
            )
        }
        // Коробки с надписью «новый дом».
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 10.dp)) {
            Box(Modifier.size(84.dp).background(Color(0xFFCBA57A)).border(2.dp, Color(0xFFA98151))) {
                Text(
                    "новый\nдом ♥",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B4A2A),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
