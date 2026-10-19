package ru.montik.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Нажимаемая зона на картинке из макета. Координаты — доли картинки (0…1), поэтому зона
 * совпадает с кнопкой при любом размере экрана.
 */
class ArtHit(val x: Float, val y: Float, val w: Float, val h: Float, val onClick: () -> Unit)

/**
 * Окно, целиком нарисованное картинкой из Figma (кадр «Как играть», «Первый кошелёк» и т.п.).
 * Кнопки уже нарисованы в картинке, поэтому поверх них кладутся только невидимые зоны нажатия.
 * [aspect] — ширина / высота картинки, чтобы она не растягивалась.
 */
@Composable
fun FigmaDialogArt(art: String, aspect: Float, hits: List<ArtHit>, onDismiss: () -> Unit) {
    // Без серого затемнения: картинка окна стоит прямо на экране игры.
    GameOverlay(onDismiss = onDismiss) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            val enter = rememberEnter()
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(aspect).enterPop(enter)) {
                ArtImage(art, Modifier.fillMaxSize(), ContentScale.FillBounds) {}
                val interaction = remember { MutableInteractionSource() }
                hits.forEach { hit ->
                    Box(
                        Modifier
                            .offset(maxWidth * hit.x, maxHeight * hit.y)
                            .size(maxWidth * hit.w, maxHeight * hit.h)
                            .clickable(interactionSource = interaction, indication = null, onClick = hit.onClick)
                    )
                }
            }
        }
    }
}
