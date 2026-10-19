package ru.montik.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import ru.montik.app.GameViewModel
import ru.montik.app.game.Destinations
import ru.montik.app.game.GameEngine

/*
 * «Карта (Путешествия)» из нового макета: шапка с монетами, Монтик с картой, карта городов и четыре
 * строки городов с ценой билета. Зелёная «>» в строке сразу покупает билет и отправляет в путешествие,
 * если хватает монет (и город уже открыт); иначе Монтик объясняет, чего не хватает.
 */

/** Строки городов на картинке image 620 (−23, 493, 456×304): город и середина строки по высоте. */
private val MAP_ROWS = listOf("spb" to 534f, "kazan" to 608f, "moscow" to 682f, "sochi" to 756f)

@Composable
fun NewTravelMap(vm: GameViewModel) {
    val s = vm.state
    val coins = rememberCountUp(s.coins)
    DesignCanvas {
        FrameArt("fg3_map_bg")
        Art("fg3_map_i617", 22f, 126f, 368f, 145f, ContentScale.FillBounds)
        Art("fg3_map_i619", 8f, 241f, 396f, 264f, ContentScale.FillBounds)
        Art("fg3_map_i620", -23f, 493f, 456f, 304f, ContentScale.FillBounds)
        // Шапка: «назад», «Путешествия», монеты (нарисованное «830» стёрто, число — настоящее).
        Art("fg3_map_i618_clean", 32f, 10f, 348f, 116f, ContentScale.FillBounds)
        Box(Modifier.at(314f, 64f, 64f, 27f), contentAlignment = Alignment.Center) {
            Text(
                "$coins",
                color = Color(0xFF15238C),
                fontSize = fs(if (coins >= 10000) 15f else 20f, false),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
        PressHit(40f, 50f, 50f, 50f, 25f) { vm.back() }

        for ((id, cy) in MAP_ROWS) {
            val dest = Destinations.byId(id) ?: continue
            val go = {
                val status = GameEngine.destinationStatus(s, dest)
                when {
                    status != null -> vm.say(status)
                    s.coins < dest.ticket -> vm.say("На билет в ${dest.name} не хватает ${dest.ticket - s.coins} ${coinWord(dest.ticket - s.coins)}.")
                    else -> vm.startTrip(dest.id)
                }
            }
            // Зелёная «>» и вся строка.
            PressHit(316f, cy - 30f, 60f, 60f, 30f, onClick = go)
            Hit(32f, cy - 32f, 280f, 64f, go)
        }
    }
}
