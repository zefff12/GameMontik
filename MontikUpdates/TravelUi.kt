package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.TripCelebration
import ru.montik.app.game.Destination
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine
import ru.montik.app.game.ShopWork
import ru.montik.app.game.Travel
import ru.montik.app.game.TripPhase

/*
 * Путешествия Монтика (кадры макета Frame 35–42, Android Compact 64–71):
 *   список городов с картой → ✈️ самолёт с плашкой «До Сочи: 2 ч 15 мин» → прогулка по городу
 *   (остановки-задания и кафе «Меню / Выйти») → 🏆 «Открыт новый город» → бизнес-конференция
 *   → +100 опыта, уровень профессионала, заработок до и после → ✈️ «До дома» → дом.
 * Правила — game/Travel.kt.
 */

private val TravelInk = Color(0xFF0D3B52)
private val TravelBody = Color(0xFF2B4A7A)
private val TravelGreen = Color(0xFF8AD742)
private val TravelDeepGreen = Color(0xFF2E7D4F)
private val TravelLine = Color(0xFFD9E9D2)
private val PlaqueCream = Color(0xFFFEFBE3)
private val PlaqueBorder = Color(0xFF9DC573)

/** Картинки города: прогулка, конференция, магнит, миниатюра. */
fun cityWalkArt(id: String): String = if (id == "nn") "fg_story2_bg" else "fg_trip_city_$id"
fun cityConferenceArt(id: String): String = "fg_trip_conf_$id"
fun cityMagnetArt(id: String): String = "fg_magnet_$id"
fun cityThumbArt(id: String): String = "fg_trip_thumb_$id"

// ───────────────────────── Экран «Путешествия» ─────────────────────────

@Composable
fun TravelScreen(vm: GameViewModel) {
    val trip = vm.state.trip
    if (trip == null) {
        TravelListScreen(vm)
        return
    }
    val dest = Destinations.byId(trip.destinationId) ?: return
    when (trip.phase) {
        TripPhase.FLY_OUT -> FlightScreen(vm, dest, home = false)
        TripPhase.CITY -> CityWalkScreen(vm, dest)
        TripPhase.CONFERENCE -> ConferenceScreen(vm, dest)
        TripPhase.FLY_HOME -> {
            // Сначала — итог конференции, потом самолёт домой.
            if (vm.tripCelebration is TripCelebration.Conference) ConferenceScreen(vm, dest) else FlightScreen(vm, dest, home = true)
        }
    }
    when (val c = vm.tripCelebration) {
        is TripCelebration.NewCity -> NewCityOverlay(vm, c)
        is TripCelebration.Conference -> ConferenceResultOverlay(vm, c)
        null -> Unit
    }
}

// ───────────────────────── Frame 35: выбор города ─────────────────────────

@Composable
private fun TravelListScreen(vm: GameViewModel) {
    val s = vm.state
    // Какой город выбран для кнопки «Купить билет» внизу: первый доступный.
    var picked by rememberSaveable {
        mutableStateOf(Destinations.all.firstOrNull { GameEngine.destinationStatus(s, it) == null && it.id !in s.completedTrips }?.id)
    }
    val chosen = picked?.let { Destinations.byId(it) }
    DesignCanvas(background = {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF3FAEE), Color(0xFFFFFFFF)))))
    }) {
        // Шапка: назад, «✈️ Путешествия», монеты.
        Box(Modifier.at(16f, 14f)) { BackCircle({ vm.goTo(Screen.Home) }) }
        DText("✈️ Путешествия", 76f, 24f, 220f, 24f, color = TravelInk, mono = false, softWrap = false)
        Box(Modifier.at(230f, 14f, 170f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }

        Column(
            Modifier
                .at(0f, 74f, DESIGN_W, 745f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d(14f)),
            verticalArrangement = Arrangement.spacedBy(d(10f))
        ) {
            // Подсказка в зелёной карточке.
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(d(22f))).background(Color(0xFFE6F5DC))
                    .border(d(1.5f), TravelLine, RoundedCornerShape(d(22f))).padding(d(14f))
            ) {
                Text(
                    "Выбирай город, куда хочешь полететь. Накопи на билет и отправляйся в путешествие! " +
                        "В каждом городе — прогулка, задания и бизнес-конференция: опыт с неё прибавляет к заработку.",
                    color = TravelBody,
                    fontSize = fs(14f, false),
                    lineHeight = fs(18f, false),
                    fontFamily = MontikFont
                )
            }
            // Карта России из макета.
            ArtImage(
                "fg_trip_map",
                Modifier.fillMaxWidth().height(d(275f)).clip(RoundedCornerShape(d(24f))),
                ContentScale.Crop
            ) {}
            // Опыт и заработок — связь, ради которой стоит путешествовать.
            CareerStrip(s.conferences)
            for (dest in Destinations.all) {
                CityCard(vm, dest, selected = dest.id == picked) { picked = dest.id }
            }
            Spacer(Modifier.height(d(76f)))
        }

        // Кнопка покупки билета.
        val status = chosen?.let { GameEngine.destinationStatus(s, it) }
        val canBuy = chosen != null && status == null && s.coins >= chosen.ticket
        Box(Modifier.at(0f, 826f, DESIGN_W, 91f).background(Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color.White))))
        DPill(
            when {
                chosen == null -> "Выбери город"
                status != null -> "🔒 $status"
                s.coins < chosen.ticket -> "Не хватает ${chosen.ticket - s.coins} монет"
                else -> "✈️ Купить билет (−${chosen.ticket})"
            },
            30f, 840f, 352f, 58f,
            fill = TravelGreen, textColor = Color.White, size = 17f, enabled = canBuy
        ) { chosen?.let { vm.startTrip(it.id) } }
    }
}

/** «Уровень профессионала N · +X% к заработку» и цепочка «путешествую → опыт → уровень → зарплата». */
@Composable
private fun DesignScope.CareerStrip(conferences: Int) {
    val level = Travel.careerLevel(conferences)
    val percent = Travel.careerPercent(conferences)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(d(22f))).background(Color.White)
            .border(d(1.5f), TravelLine, RoundedCornerShape(d(22f))).padding(d(12f)),
        verticalArrangement = Arrangement.spacedBy(d(4f))
    ) {
        Text(
            "⭐ Уровень профессионала $level · прибавка к заработку +$percent%",
            color = TravelInk, fontSize = fs(15f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont
        )
        Text(
            "✈️ Путешествую → 🎤 конференция → 📈 опыт → ⭐ уровень → 💰 зарплата выше",
            color = TravelBody, fontSize = fs(12.5f, false), fontFamily = MontikFont
        )
    }
}

@Composable
private fun DesignScope.CityCard(vm: GameViewModel, dest: Destination, selected: Boolean, onPick: () -> Unit) {
    val s = vm.state
    val status = GameEngine.destinationStatus(s, dest)
    val done = dest.id in s.completedTrips
    val shape = RoundedCornerShape(d(22f))
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White)
            .border(if (selected) d(3f) else d(1.5f), if (selected) TravelGreen else TravelLine, shape)
            .clickable(onClick = onPick)
            .padding(d(10f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(cityThumbArt(dest.id), Modifier.size(d(70f)).clip(RoundedCornerShape(d(16f))), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Color(0xFFE6F5DC)), contentAlignment = Alignment.Center) {
                Text(dest.emoji, fontSize = fs(34f, false))
            }
        }
        Spacer(Modifier.width(d(10f)))
        Column(Modifier.weight(1f)) {
            Text(dest.name, color = TravelInk, fontSize = fs(17f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont, maxLines = 1)
            Text(
                "✈️ ${Travel.duration(dest.flightMinutes)} · 🎤 «${dest.conference}»",
                color = TravelBody, fontSize = fs(11.5f, false), lineHeight = fs(14f, false), fontFamily = MontikFont, maxLines = 2
            )
        }
        Spacer(Modifier.width(d(6f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(d(96f))) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFF4D6)).padding(horizontal = d(8f), vertical = d(3f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoinIcon(Modifier.size(d(18f)))
                Spacer(Modifier.width(d(4f)))
                Text("${dest.ticket}", color = TravelInk, fontSize = fs(15f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
            }
            Spacer(Modifier.height(d(5f)))
            val (chip, chipBg, chipInk) = when {
                done -> Triple("✅ Уже побывали", Color(0xFFE3F5DA), Color(0xFF2E7D4F))
                status != null -> Triple("🔒 ${status.removePrefix("Сначала поездка: ").let { "после: $it" }}", Color(0xFFE6F0FB), TravelBody)
                else -> Triple("Можно лететь", Color(0xFFE3F5DA), Color(0xFF2E7D4F))
            }
            Text(
                chip,
                color = chipInk,
                fontSize = fs(10.5f, false),
                lineHeight = fs(12f, false),
                fontFamily = MontikFont,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.clip(RoundedCornerShape(d(10f))).background(chipBg).padding(horizontal = d(6f), vertical = d(3f))
            )
        }
    }
}

// ───────────────────────── Android Compact 64: самолёт ─────────────────────────

/** Длительность перелёта на экране (в игре — часы, на экране — несколько секунд). */
private const val FLIGHT_MS = 7000

@Composable
private fun FlightScreen(vm: GameViewModel, dest: Destination, home: Boolean) {
    val progress = remember(dest.id, home) { Animatable(0f) }
    LaunchedEffect(dest.id, home) {
        progress.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
        delay(500)
        if (home) vm.flyHome() else vm.landTrip()
    }
    BackHandler(enabled = true) { vm.goTo(Screen.Home) }
    val left = (dest.flightMinutes * (1f - progress.value)).toInt()
    DesignCanvas(background = {
        ArtImage("fg_trip_plane", Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF9CD3F5), Color(0xFFE8F6FF)))))
        }
    }) {
        Art("fg_trip_plane", 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.FillBounds)
        // Плашка «✈️ До Сочи: 2 ч 15 мин 🕐» — город и время меняются сами.
        Row(
            Modifier
                .at(14f, 60f, 384f, 58f)
                .clip(RoundedCornerShape(50))
                .background(PlaqueCream)
                .border(d(3f), PlaqueBorder, RoundedCornerShape(50))
                .padding(horizontal = d(12f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtImage("fg_trip_ic_plane", Modifier.size(d(46f), d(36f)), ContentScale.Fit) { Text("✈️", fontSize = fs(24f, false)) }
            Spacer(Modifier.width(d(8f)))
            Text(
                Travel.flightTitle(dest, home) + ": ",
                color = Color(0xFF26323A), fontSize = fs(17f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont,
                maxLines = 1, modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                Travel.duration(left),
                color = TravelDeepGreen, fontSize = fs(17f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont, maxLines = 1
            )
            Spacer(Modifier.width(d(6f)))
            ArtImage("fg_trip_ic_clock", Modifier.size(d(26f)), ContentScale.Fit) { Text("🕐", fontSize = fs(18f, false)) }
        }
        // Полоска полёта.
        Box(Modifier.at(40f, 128f, 332f, 10f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.75f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress.value).clip(RoundedCornerShape(50)).background(TravelGreen))
        }
        DText(
            if (home) "Монтик летит домой с новым опытом" else "Монтик летит: ${dest.name} уже близко!",
            20f, 146f, 372f, 14f, bold = false, color = Color.White, mono = false, align = TextAlign.Center
        )
    }
}

// ───────────────────────── Прогулка по городу ─────────────────────────

@Composable
private fun CityWalkScreen(vm: GameViewModel, dest: Destination) {
    val s = vm.state
    val trip = s.trip ?: return
    val stop = GameEngine.currentStop(s)
    var stopOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var cafe by rememberSaveable { mutableStateOf(false) }
    if (cafe) {
        CafeScreen(vm, onExit = { cafe = false })
        return
    }
    BackHandler(enabled = stopOpen != null) { stopOpen = null }
    DesignCanvas(background = {
        ArtImage(cityWalkArt(dest.id), Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF8CCBF0), Color(0xFFF6E7CF)))))
        }
    }) {
        Art(cityWalkArt(dest.id), 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.Crop)
        Box(Modifier.at(16f, 14f)) { BackCircle({ vm.goTo(Screen.Home) }) }
        Box(Modifier.at(230f, 14f, 170f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }

        // Карточка прогулки.
        Column(
            Modifier.at(16f, 74f, 380f, 112f).clip(RoundedCornerShape(d(24f))).background(Color.White.copy(alpha = 0.95f))
                .padding(horizontal = d(16f), vertical = d(12f)),
            verticalArrangement = Arrangement.spacedBy(d(4f))
        ) {
            Text(
                "📍 Прогулка: ${dest.name} ${dest.emoji}",
                color = TravelInk, fontSize = fs(18f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont, maxLines = 1
            )
            Text(
                if (stop != null) "Остановка ${trip.stopsDone + 1} из ${dest.stops.size} — «${stop.title}»" else "Все остановки пройдены!",
                color = TravelBody, fontSize = fs(14f, false), fontFamily = MontikFont, maxLines = 1
            )
            StopDots(done = trip.stopsDone, total = dest.stops.size)
            Text(
                "Еда ${s.food} · Вода ${s.water} · Силы ${s.energy}",
                color = TravelBody.copy(alpha = 0.8f), fontSize = fs(12f, false), fontFamily = MontikFont
            )
        }

        // Твой Монтик гуляет по городу.
        SoftGlow(cx = 110f, cy = 846f, w = 200f, h = 44f, color = Color.Black, peak = 0.25f)
        SceneMontik(vm, 0f, 610f, 230f)

        // Кнопки прогулки.
        if (stop != null) {
            DPill("🗺 Остановка ${trip.stopsDone + 1} ›", 220f, 690f, 176f, 60f, fill = TravelDeepGreen, size = 17f) { stopOpen = stop.id }
        }
        DPill(
            if (trip.cafe) "☕ Кафе (уже был)" else "☕ Кафе «Патрики»",
            220f, 762f, 176f, 54f,
            fill = Color(0xFFFFF4E4), textColor = Color(0xFF6B3A1E), size = 15f
        ) { cafe = true }
        DText("🏡 Квартира ждёт Монтика дома", 220f, 826f, 176f, 11.5f, bold = false, color = Color.White, mono = false, align = TextAlign.Center)

        if (stop != null && stopOpen == stop.id) {
            Box(
                Modifier.at(0f, 0f, DESIGN_W, DESIGN_H).background(Color.Black.copy(alpha = 0.45f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { stopOpen = null }
            )
            Column(
                Modifier.at(10f, 120f, 392f, 760f).verticalScroll(rememberScrollState())
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            ) {
                ScenarioCard(stop, s) { index -> stopOpen = null; vm.choose(stop.id, index) }
                Spacer(Modifier.height(d(8f)))
                DPillBox("Позже") { stopOpen = null }
            }
        }
    }
}

@Composable
private fun DesignScope.StopDots(done: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val passed = i < done
            val current = i == done
            Box(
                Modifier.size(d(if (current) 16f else 12f)).clip(CircleShape)
                    .background(if (passed) TravelDeepGreen else if (current) TravelGreen else Color(0xFFD6E4EE))
            )
            if (i < total - 1) {
                Box(Modifier.width(d(34f)).height(d(3f)).background(if (passed) TravelDeepGreen else Color(0xFFD6E4EE)))
            }
        }
        Box(Modifier.width(d(34f)).height(d(3f)).background(Color(0xFFD6E4EE)))
        Text(" 🎤", fontSize = fs(13f, false))
    }
}

/** Светлая пилюля на всю ширину для колонок. */
@Composable
private fun DesignScope.DPillBox(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(d(52f)).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TravelInk, fontSize = fs(17f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
    }
}

// ───────────────────────── Android Compact 66, 67, 71: кафе ─────────────────────────

@Composable
private fun CafeScreen(vm: GameViewModel, onExit: () -> Unit) {
    val s = vm.state
    val trip = s.trip ?: return
    var menu by rememberSaveable { mutableStateOf(false) }
    val fb = rememberFeedback()
    BackHandler(enabled = true) { if (menu) menu = false else onExit() }
    val bg = if (trip.cafe) "fg_trip_cafe_meal" else "fg_trip_cafe"
    DesignCanvas(background = {
        ArtImage(bg, Modifier.fillMaxSize(), ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Color(0xFF3A2C4A))) }
    }) {
        Art(bg, 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.Crop)
        // Кнопки «Меню» и «Выйти» из макета.
        Art("fg_trip_cafe_bar", -14f, 0f, 426f, 142f, ContentScale.FillBounds)
        Hit(28f, 40f, 170f, 62f) { fb.tap(); menu = true }
        Hit(212f, 40f, 170f, 62f) { fb.tap(); onExit() }

        if (!menu) {
            // Решение: поужинать или нет — это желаемая трата.
            Column(
                Modifier.at(16f, 786f, 380f, 118f).clip(RoundedCornerShape(d(24f))).background(Color.White.copy(alpha = 0.96f))
                    .padding(horizontal = d(14f), vertical = d(10f)),
                verticalArrangement = Arrangement.spacedBy(d(8f))
            ) {
                if (trip.cafe) {
                    Text(
                        "😋 Приятного аппетита! Ужин стоил ${Travel.CAFE_PRICE} монет. В поездке кафе — приятное желание, а не обязательная трата.",
                        color = TravelInk, fontSize = fs(14f, false), lineHeight = fs(18f, false), fontFamily = MontikFont
                    )
                    Box(
                        Modifier.fillMaxWidth().height(d(42f)).clip(RoundedCornerShape(50)).background(TravelGreen).clickable { onExit() },
                        contentAlignment = Alignment.Center
                    ) { Text("Продолжить прогулку", color = Color.White, fontSize = fs(16f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont) }
                } else {
                    Text(
                        "🍝 Ужин в кафе: ${Travel.CAFE_PRICE} 🪙 · +${Travel.CAFE_FOOD} еды, +${Travel.CAFE_WATER} воды. В кошельке ${s.coins}.",
                        color = TravelInk, fontSize = fs(14f, false), lineHeight = fs(18f, false), fontFamily = MontikFont
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(d(8f))) {
                        Box(
                            Modifier.weight(1.3f).height(d(42f)).clip(RoundedCornerShape(50))
                                .background(if (s.coins >= Travel.CAFE_PRICE) TravelGreen else TravelGreen.copy(alpha = 0.4f))
                                .clickable { if (vm.eatAtCafe()) fb.coin() else fb.bad() },
                            contentAlignment = Alignment.Center
                        ) { Text("Поужинать (−${Travel.CAFE_PRICE})", color = Color.White, fontSize = fs(15f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont) }
                        Box(
                            Modifier.weight(1f).height(d(42f)).clip(RoundedCornerShape(50)).background(Color(0xFFF1E4E4)).clickable { onExit() },
                            contentAlignment = Alignment.Center
                        ) { Text("Не сейчас", color = Color(0xFF6B3A1E), fontSize = fs(15f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont) }
                    }
                }
            }
        } else {
            // Меню из макета и крестик «закрыть».
            Box(
                Modifier.at(0f, 0f, DESIGN_W, DESIGN_H).background(Color.Black.copy(alpha = 0.25f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menu = false }
            )
            Art("fg_trip_menu", 30f, 159f, 352f, 527f, ContentScale.Fit)
            Art("fg_trip_close", 143f, 686f, 126f, 109f, ContentScale.Fit)
            Hit(150f, 690f, 112f, 100f) { fb.tap(); menu = false }
        }
    }
}

// ───────────────────────── Frame 39–42: бизнес-конференция ─────────────────────────

@Composable
private fun ConferenceScreen(vm: GameViewModel, dest: Destination) {
    val s = vm.state
    val attended = s.trip?.phase == TripPhase.FLY_HOME
    val fb = rememberFeedback()
    DesignCanvas(background = {
        ArtImage(cityConferenceArt(dest.id), Modifier.fillMaxSize(), ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Color(0xFF2A2230))) }
    }) {
        Art(cityConferenceArt(dest.id), 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.Crop)
        Box(Modifier.at(16f, 14f)) { BackCircle({ vm.goTo(Screen.Home) }) }
        Box(Modifier.at(230f, 14f, 170f, 50f), contentAlignment = Alignment.CenterEnd) { CoinPill(s.coins) }
        if (!attended) {
            Column(
                Modifier.at(14f, 612f, 384f, 290f).clip(RoundedCornerShape(d(28f))).background(Color.White.copy(alpha = 0.96f))
                    .padding(horizontal = d(16f), vertical = d(14f)),
                verticalArrangement = Arrangement.spacedBy(d(6f))
            ) {
                Text("🎤 Бизнес-конференция · ${dest.name}", color = TravelBody, fontSize = fs(13f, false), fontFamily = MontikFont)
                Text(
                    "«${dest.conference}»",
                    color = TravelInk, fontSize = fs(19f, false), lineHeight = fs(22f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont
                )
                Text(
                    "📝 Главное: ${dest.lesson}",
                    color = TravelBody, fontSize = fs(13.5f, false), lineHeight = fs(17f, false), fontFamily = MontikFont
                )
                Text(
                    "Награда: +${Travel.CONFERENCE_XP} опыта и +${Travel.CAREER_STEP_PERCENT}% к заработку за каждую смену.",
                    color = TravelDeepGreen, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont
                )
                Box(
                    Modifier.fillMaxWidth().height(d(52f)).clip(RoundedCornerShape(50)).background(TravelGreen)
                        .clickable { fb.fanfare(); vm.attendConference() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Послушать и записать главное", color = Color.White, fontSize = fs(17f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                }
            }
        }
    }
}

// ───────────────────────── Достижения ─────────────────────────

@Composable
private fun NewCityOverlay(vm: GameViewModel, c: TripCelebration.NewCity) {
    val dest = Destinations.byId(c.destinationId) ?: return
    val fb = rememberFeedback()
    LaunchedEffect(c) { fb.fanfare() }
    BackHandler(enabled = true) { vm.dismissTripCelebration() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            Box(Modifier.at(26f, 190f, 360f, 500f).clip(RoundedCornerShape(d(32f))).background(TipGreen).border(d(4f), TravelGreen, RoundedCornerShape(d(32f))))
            DText("🏆 Ура! Открыт новый город!", 36f, 214f, 340f, 23f, color = NameInk, mono = false, align = TextAlign.Center)
            DText(dest.name, 36f, 250f, 340f, 17f, bold = false, color = TravelBody, mono = false, align = TextAlign.Center)
            ArtImage(cityMagnetArt(dest.id), Modifier.at(96f, 288f, 220f, 200f).rotate(-4f), ContentScale.Fit) {
                Text(dest.emoji, fontSize = fs(90f, false))
            }
            DText(
                "Новый магнит — уже на холодильнике на кухне. Теперь гуляй по городу: впереди задания и бизнес-конференция!",
                46f, 500f, 320f, 14.5f, bold = false, color = NameInk, mono = false, align = TextAlign.Center
            )
            LivePill("Гулять!", 121f, 606f) { vm.dismissTripCelebration() }
        }
        Confetti(trigger = 1)
    }
}

@Composable
private fun ConferenceResultOverlay(vm: GameViewModel, c: TripCelebration.Conference) {
    val dest = Destinations.byId(c.destinationId) ?: return
    val fb = rememberFeedback()
    LaunchedEffect(c) { fb.fanfare() }
    BackHandler(enabled = true) { vm.dismissTripCelebration() }
    val base = ShopWork.rank(vm.state).base
    val before = Travel.shiftExample(base, c.conferencesBefore)
    val after = Travel.shiftExample(base, c.conferencesAfter)
    val lvlBefore = Travel.careerLevel(c.conferencesBefore)
    val lvlAfter = Travel.careerLevel(c.conferencesAfter)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        DesignCanvas {
            Box(Modifier.at(20f, 110f, 372f, 700f).clip(RoundedCornerShape(d(32f))).background(TipGreen).border(d(4f), TravelGreen, RoundedCornerShape(d(32f))))
            DText("🏆 Получил новый опыт!", 30f, 134f, 352f, 24f, color = NameInk, mono = false, align = TextAlign.Center)
            DText("Конференция «${dest.conference}»", 40f, 172f, 332f, 14f, bold = false, color = TravelBody, mono = false, align = TextAlign.Center)
            // +100 опыта.
            Box(
                Modifier.at(116f, 214f, 180f, 50f).clip(RoundedCornerShape(50)).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Text("📈 +${Travel.CONFERENCE_XP} опыта", color = TravelDeepGreen, fontSize = fs(19f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont) }
            // Новый уровень.
            Column(
                Modifier.at(40f, 284f, 332f, 124f).clip(RoundedCornerShape(d(24f))).background(Color.White).padding(d(12f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d(4f))
            ) {
                Text(
                    if (lvlAfter > lvlBefore) "⭐ НОВЫЙ УРОВЕНЬ!" else "⭐ Опыт пополнился",
                    color = Color(0xFFE08A00), fontSize = fs(20f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont
                )
                Text(
                    "Уровень профессионала: $lvlBefore → $lvlAfter",
                    color = TravelInk, fontSize = fs(16f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont
                )
                if (c.xpLevelAfter > c.xpLevelBefore) {
                    Text("Уровень Монтика: ${c.xpLevelBefore} → ${c.xpLevelAfter}", color = TravelBody, fontSize = fs(13f, false), fontFamily = MontikFont)
                }
            }
            // Заработок до и после.
            Column(
                Modifier.at(40f, 424f, 332f, 186f).clip(RoundedCornerShape(d(24f))).background(Color.White).padding(d(12f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d(4f))
            ) {
                Text("💰 Заработок за смену в магазине", color = TravelInk, fontSize = fs(15f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$before", color = TravelBody, fontSize = fs(26f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                    Text("  →  ", color = TravelBody, fontSize = fs(22f, false), fontFamily = MontikFont)
                    Text("$after", color = TravelDeepGreen, fontSize = fs(30f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
                    Text(" 🪙", fontSize = fs(22f, false))
                }
                Text(
                    "Прибавка за опыт: +${Travel.careerPercent(c.conferencesBefore)}% → +${Travel.careerPercent(c.conferencesAfter)}% — на любой работе.",
                    color = TravelBody, fontSize = fs(12.5f, false), fontFamily = MontikFont, textAlign = TextAlign.Center
                )
                Text(
                    "Путешествую → получаю опыт → повышаю уровень → зарабатываю больше!",
                    color = TravelDeepGreen, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont, textAlign = TextAlign.Center
                )
            }
            DPill("✈️ Лететь домой", 76f, 636f, 260f, 62f, fill = TravelDeepGreen, size = 19f) { vm.dismissTripCelebration() }
            DText("Перелёт: ${Travel.duration(dest.flightMinutes)}", 40f, 708f, 332f, 13f, bold = false, color = TravelBody, mono = false, align = TextAlign.Center)
        }
        Confetti(trigger = 2)
    }
}

// ───────────────────────── Frame 38: холодильник с магнитами ─────────────────────────

/** Где магниты висят на дверце (координаты макета). */
private class MagnetSpot(val id: String, val x: Float, val y: Float, val w: Float, val h: Float)

private const val FK = 412f / 840f
private val MAGNET_SPOTS = listOf(
    MagnetSpot("nn", 111f * FK, 347f * FK, 290f * FK, 252f * FK),
    MagnetSpot("sochi", 434f * FK, 366f * FK, 250f * FK, 227f * FK),
    MagnetSpot("moscow", 295f * FK, 554f * FK, 245f * FK, 260f * FK),
    MagnetSpot("spb", 158f * FK, 798f * FK, 269f * FK, 268f * FK),
    MagnetSpot("kazan", 465f * FK, 845f * FK, 226f * FK, 255f * FK)
)

/** Закрытый холодильник: магниты из поездок. Нажми «Открой холодильник» — внутри продукты. */
@Composable
fun FridgeDoorScreen(vm: GameViewModel) {
    val s = vm.state
    val magnets = ru.montik.app.game.Keepsakes.magnets(s)
    val got = magnets.count { it.collected }
    val fb = rememberFeedback()
    BackHandler(enabled = true) { vm.goTo(Screen.Kitchen) }
    DesignCanvas(background = {
        ArtImage("fg_fridge_door", Modifier.fillMaxSize(), ContentScale.Crop) { Box(Modifier.fillMaxSize().background(Color(0xFF9FC3C9))) }
    }) {
        Art("fg_fridge_door", 0f, 0f, DESIGN_W, DESIGN_H, ContentScale.FillBounds)
        for (spot in MAGNET_SPOTS) {
            val m = magnets.firstOrNull { it.destinationId == spot.id } ?: continue
            if (m.collected) {
                Art(cityMagnetArt(spot.id), spot.x, spot.y, spot.w, spot.h, ContentScale.Fit)
            } else {
                // Пустое место для магнита: ещё не побывали.
                Box(
                    Modifier.at(spot.x + 14f, spot.y + 14f, spot.w - 28f, spot.h - 28f)
                        .clip(RoundedCornerShape(d(18f)))
                        .background(Color.White.copy(alpha = 0.35f))
                        .border(d(2f), Color(0xFFB5C7D3), RoundedCornerShape(d(18f))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("? ${m.city}", color = Color(0xFF7D93A3), fontSize = fs(11f, false), fontFamily = MontikFont, textAlign = TextAlign.Center)
                }
            }
        }
        Box(Modifier.at(16f, 14f)) { BackCircle({ vm.goTo(Screen.Kitchen) }) }
        Box(
            Modifier.at(186f, 20f, 210f, 40f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🧲 Магниты из поездок: $got из ${magnets.size}", color = NameInk, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, fontFamily = MontikFont)
        }
        // Наклейка «Открой холодильник» и вся нижняя дверца открывают холодильник.
        Hit(20f, 590f, 360f, 250f) { fb.tap(); vm.goTo(Screen.Fridge) }
    }
}
