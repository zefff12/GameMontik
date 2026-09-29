package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.Travel
import ru.montik.app.game.TripPhase

// ───────────────────────── Монтик в путешествии ─────────────────────────

private val TripSkyTop = Color(0xFF7CC8F2)
private val TripSkyBottom = Color(0xFFE6F6FF)
private val TripGround = Color(0xFFBFE6AC)

/**
 * Главный экран, пока Монтик в поездке (кадр макета Frame 37): небо и холм, карточка поездки,
 * наклейка города, твой Монтик с чемоданом, «Продолжить путешествие» и три плитки.
 */
@Composable
fun TripHomeScreen(vm: GameViewModel) {
    val s = vm.state
    val trip = s.trip ?: return
    val dest = Destinations.byId(trip.destinationId) ?: return
    val stop = GameEngine.currentStop(s)

    // В Сочи Монтик живёт у моря (кадры Frame 26 / Android Compact 31), в остальных городах — небо и холм (Frame 37).
    val beach = dest.id == "sochi" && rememberHasArt("fg_trip_beach")
    DesignCanvas(background = {
        if (beach) ArtImage("fg_trip_beach", Modifier.fillMaxSize(), ContentScale.Crop) {}
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TripSkyTop, TripSkyBottom))))
    }) {
        if (beach) {
            Art("fg_trip_beach", 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.Crop)
        } else {
            // Облака и зелёный холм.
            SoftGlow(cx = 80f, cy = 330f, w = 190f, h = 60f, color = Color.White, peak = 0.9f)
            SoftGlow(cx = 330f, cy = 380f, w = 170f, h = 54f, color = Color.White, peak = 0.85f)
            Box(Modifier.at(-80f, 520f, 572f, 460f).clip(RoundedCornerShape(topStart = d(290f), topEnd = d(290f))).background(TripGround))
            Box(Modifier.at(-80f, 600f, 572f, 380f).clip(RoundedCornerShape(topStart = d(290f), topEnd = d(290f))).background(Color(0xFF9CD67C)))
        }

        Box(Modifier.at(250f, 18f, 150f, 44f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }

        // Карточка поездки.
        Column(
            Modifier.at(16f, 78f, 380f, 124f).clip(RoundedCornerShape(d(26f))).background(Color.White.copy(alpha = 0.96f))
                .padding(horizontal = d(16f), vertical = d(12f)),
            verticalArrangement = Arrangement.spacedBy(d(5f))
        ) {
            Text(
                "✈️ Монтик в путешествии! ${dest.emoji} ${dest.name}",
                color = MontikColors.Ink, fontSize = fs(16.5f, false), fontWeight = FontWeight.Bold, maxLines = 1
            )
            Text(
                "📍 " + when (trip.phase) {
                    TripPhase.FLY_OUT -> "Самолёт летит: ${Travel.flightTitle(dest, false)} — ${Travel.duration(dest.flightMinutes)}"
                    TripPhase.CITY -> "Остановка ${trip.stopsDone + 1} из ${dest.stops.size}" + (stop?.let { " — «${it.title}»" } ?: "")
                    TripPhase.CONFERENCE -> "Бизнес-конференция «${dest.conference}»"
                    TripPhase.FLY_HOME -> "Пора домой: ${Travel.duration(dest.flightMinutes)} в самолёте"
                },
                color = Color(0xFF2B4A7A), fontSize = fs(13.5f, false), maxLines = 2
            )
            Box(Modifier.fillMaxWidth().height(d(1f)).background(Color(0xFFE3ECE0)))
            Text(
                "Квартира ждёт его дома. Еда ${s.food} · Вода ${s.water} · Силы ${s.energy}",
                color = Color(0xFF2B4A7A), fontSize = fs(12.5f, false), maxLines = 1
            )
        }

        // Наклейка города: волна для Сочи, магнит — для остальных.
        Box(Modifier.at(116f, 222f, 180f, 160f), contentAlignment = Alignment.Center) {
            ArtImage(if (dest.id == "sochi") "fg_trip_wave" else cityMagnetArt(dest.id), Modifier.fillMaxSize(), ContentScale.Fit) {
                Text(dest.emoji, fontSize = fs(100f, false))
            }
        }

        // Твой Монтик с чемоданом.
        SoftGlow(cx = 190f, cy = 636f, w = 200f, h = 40f, color = Color.Black, peak = 0.18f)
        Box(Modifier.at(80f, 400f)) { MontikView(vm.sprite, s, d(220f), heroPalette(s)) }
        Text("🧳", fontSize = fs(58f, false), modifier = Modifier.at(290f, 548f))

        // Кнопки.
        DPill("🧳 Продолжить путешествие →", 30f, 666f, 352f, 64f, fill = Color(0xFF2E7D4F), size = 18f) { vm.goTo(Screen.Travel) }
        TripButton("📱", "Телефон", 30f, 752f) { vm.goTo(Screen.Phone) }
        TripButton("💤", "Сон", 160f, 752f) { vm.goTo(Screen.Sleep) }
        TripButton("🍽", "Кухня", 290f, 752f) { vm.goTo(Screen.Kitchen) }
    }
}

@Composable
private fun DesignScope.TripButton(emoji: String, label: String, x: Float, y: Float, onClick: () -> Unit) {
    Box(
        Modifier.at(x, y, 92f, 92f).clip(RoundedCornerShape(d(24f))).background(Color.White)
            .border(d(2f), Color(0xFF8AD742), RoundedCornerShape(d(24f))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = fs(34f, false), modifier = Modifier.padding(bottom = d(22f)))
        Text(
            label,
            color = MontikColors.Ink,
            fontSize = fs(14f, false),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = d(10f))
        )
    }
}

// ───────────────────────── Приглашение на рынок ─────────────────────────

/** Сообщение в телефоне: енот Сергеевич зовёт на рынок (раз в 10 игровых дней). */
@Composable
fun BarterInviteDialog(vm: GameViewModel) {
    val fb = rememberFeedback()
    LaunchedEffect(Unit) { fb.coin() }
    BackHandler(enabled = true) { vm.hideBarterInvite() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            Box(Modifier.at(21f, 200f, 370f, 470f).clip(RoundedCornerShape(d(28f))).background(TipGreen))
            // Енот в круглой рамке — кусочек сцены с рынка.
            Box(
                Modifier.at(41f, 222f, 76f, 76f).clip(CircleShape).background(Color.White)
                    .border(d(3f), LevelGreen, CircleShape)
            ) {
                ArtImage("fg_market_s2", Modifier.fillMaxSize(), ContentScale.Crop) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("🦝", fontSize = fs(40f, false)) }
                }
            }
            DText("Новое сообщение", 130f, 230f, 250f, 13f, color = LevelGreen, mono = false)
            DText("Енот Сергеевич", 130f, 252f, 250f, 21f, color = NameInk, mono = false)
            Box(Modifier.at(41f, 316f, 330f, 230f)) {
                ArtImage("fg_market_s1", Modifier.fillMaxSize().clip(RoundedCornerShape(d(20f))), ContentScale.Crop) {}
            }
            Box(
                Modifier.at(41f, 470f, 330f, 100f).clip(RoundedCornerShape(d(18f))).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "«Привет, Монтик! Загляни ко мне на рынок: у меня отличный урожай. " +
                        "Научу меняться без денег!»",
                    color = MontikColors.Ink,
                    fontSize = fs(15f, false),
                    lineHeight = fs(19f, false),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = d(14f))
                )
            }
            LivePill("Пойти на рынок", 60f, 590f, 180f) { vm.openMarket() }
            LivePill("Позже", 250f, 590f, 110f, fill = Color(0xFFB9C9B0)) { vm.hideBarterInvite() }
        }
    }
}
