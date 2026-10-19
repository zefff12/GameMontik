package ru.montik.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import ru.montik.app.GameViewModel
import ru.montik.app.Screen
import ru.montik.app.game.Grocery
import ru.montik.app.game.GroceryItem

/*
 * Холодильник — кадр макета «Frame 31» (картинка fg_fridge_bg, 841×1870 → 412×916).
 * С картинки убраны нарисованные продукты: на полки кладётся то, что куплено в магазине продуктов.
 * Нажал на продукт — Монтик его съел или выпил: продукт пропал, сытость и вода выросли.
 */

private val FridgeInk = Color(0xFF2E6B3A)

/** Полки холодильника: где «пол» каждой полки (y) — по три продукта на полке. */
private val SHELF_FLOORS = floatArrayOf(386f, 505f, 638f)
private val SHELF_COLUMNS = floatArrayOf(100f, 201f, 302f)
private const val PER_PAGE = 9

/** Полки нового холодильника (кадр «Холодильник»): «пол» полок и середины столбцов. */
private val NEW_FLOORS = floatArrayOf(366f, 471f, 577f)
private val NEW_COLUMNS = floatArrayOf(100f, 197f, 294f)

@Composable
fun FridgeScreen(vm: GameViewModel) {
    if (rememberHasArt("fg3_fridge_clean", "fg3_fridge_empty_note")) {
        NewFridgeScreen(vm)
        return
    }
    val s = vm.state
    val items = Grocery.fridge(s)
    var pageNo by rememberSaveable { mutableStateOf(0) }
    val pages = maxOf(1, (items.size + PER_PAGE - 1) / PER_PAGE)
    val page = pageNo.coerceIn(0, pages - 1)
    val fb = rememberFeedback()
    BackHandler { vm.goTo(Screen.Kitchen) }

    // Пустой холодильник — кадр Figma «Android Compact - 85»: там и подпись, и кнопка «В магазин», и Монтик.
    val figmaEmpty = items.isEmpty() && rememberHasArt("fg2_fridge")

    DesignCanvas(background = {
        ArtImage(if (figmaEmpty) "fg2_fridge" else "fg_fridge_bg", Modifier.fillMaxSize(), ContentScale.Crop) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE7F1F7), Color(0xFFBFD3DE)))))
        }
    }) {
        if (figmaEmpty) {
            FrameArt("fg2_fridge")
            // Кадр целиком из макета: сытость и вода — живые, кнопка «В магазин» и крестик нажимаются.
            LiveText("${s.food}", 163f, 164f, 38f, 19f, Color(0xFFFCFCFD), 15.5f, Color(0xFF264A6B), align = Alignment.Center, radius = 2f)
            LiveText("${s.water}", 287f, 164f, 36f, 19f, Color(0xFFFCFCFD), 15.5f, Color(0xFF264A6B), align = Alignment.Center, radius = 2f)
            PressHit(113f, 405f, 155f, 42f, 21f) { vm.goTo(Screen.Grocery) }
            PressHit(330f, 50f, 52f, 56f, 26f) { vm.goTo(Screen.Kitchen) }
            return@DesignCanvas
        }
        if (rememberHasArt("fg_fridge_bg")) Art("fg_fridge_bg", 0f, 0f, DESIGN_W, 916f, ContentScale.FillBounds)
        else DText("🧊 Холодильник", 20f, 40f, 372f, 26f, align = TextAlign.Center)
        // Белый человечек из макета стёрт — у холодильника стоит твой Монтик.
        SceneMontik(vm, -18f, 712f, 190f)

        // Сытость и вода — чтобы видеть, что продукт помог.
        Box(
            Modifier.at(86f, 166f, 240f, 26f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "🍲 сытость ${s.food}   💧 вода ${s.water}",
                color = FridgeInk, fontSize = fs(13f, false), fontWeight = FontWeight.Bold, maxLines = 1
            )
        }

        if (items.isEmpty()) {
            Bubble("Холодильник пуст. Купи продукты в магазине — они появятся здесь!", 50f, 290f, 312f, 110f, size = 17f)
            DPill("🛒 В магазин", 111f, 420f, 190f, 56f) { vm.goTo(Screen.Grocery) }
        } else {
            items.drop(page * PER_PAGE).take(PER_PAGE).forEachIndexed { i, (item, n) ->
                FridgeItem(item, n, SHELF_COLUMNS[i % 3], SHELF_FLOORS[i / 3]) {
                    if (vm.eatFromFridge(item.id)) fb.good() else fb.bad()
                }
            }
            if (pages > 1) {
                DPill("‹", 40f, 690f, 52f, 44f, fill = Color.White, textColor = FridgeInk, size = 26f, enabled = page > 0) { pageNo = page - 1 }
                DPill("${page + 1} из $pages", 150f, 692f, 112f, 40f, fill = Color.White, textColor = FridgeInk, size = 15f) {}
                DPill("›", 320f, 690f, 52f, 44f, fill = Color.White, textColor = FridgeInk, size = 26f, enabled = page < pages - 1) { pageNo = page + 1 }
            }
            DPill("🛒 Магазин", 146f, 770f, 120f, 34f, fill = Color(0xFF6DBE45), size = 15f) { vm.goTo(Screen.Grocery) }
        }

        // Крестик в карточке «Холодильник» — назад на кухню.
        Hit(338f, 52f, 52f, 52f) { vm.goTo(Screen.Kitchen) }
    }
}

/** Продукт на полке: картинка стоит на полке, под ней — белая табличка с названием, как в макете. */
@Composable
private fun DesignScope.FridgeItem(item: GroceryItem, count: Int, cx: Float, floor: Float, onEat: () -> Unit) {
    val w = 86f
    val h = 72f
    Box(Modifier.at(cx - w / 2f, floor - h - 2f, w, h), contentAlignment = Alignment.BottomCenter) {
        ArtImage(groceryArt(item.id), Modifier.fillMaxSize(), ContentScale.Fit) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = fs(40f, false)) }
        }
    }
    Box(
        Modifier.at(cx - 45f, floor - 24f, 90f, 20f)
            .clip(RoundedCornerShape(d(8f)))
            .background(Color.White.copy(alpha = 0.95f))
            .border(d(1f), Color(0xFFD4E6D4), RoundedCornerShape(d(8f))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            item.name, color = FridgeInk, fontSize = fs(10.5f, false), fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
    if (count > 1) {
        Box(
            Modifier.at(cx + 24f, floor - h - 4f, 22f, 22f).clip(CircleShape).background(Color(0xFFF2A516)),
            contentAlignment = Alignment.Center
        ) { Text("$count", color = Color.White, fontSize = fs(12f, false), fontWeight = FontWeight.Bold) }
    }
    Hit(cx - w / 2f, floor - h - 4f, w, h + 4f, onEat)
}

/** Холодильник из нового макета: продукты на полках, сытость и вода сверху, плашка «+N⚡», когда Монтик поел. */
@Composable
private fun NewFridgeScreen(vm: GameViewModel) {
    val s = vm.state
    val items = Grocery.fridge(s)
    var pageNo by rememberSaveable { mutableStateOf(0) }
    val pages = maxOf(1, (items.size + PER_PAGE - 1) / PER_PAGE)
    val page = pageNo.coerceIn(0, pages - 1)
    val fb = rememberFeedback()
    BackHandler { vm.back() }
    DesignCanvas {
        FrameArt("fg3_fridge_clean", 0f, 0f, 412f, 918f)
        PressHit(338f, 56f, 50f, 50f, 25f) { vm.back() }
        Box(Modifier.at(176f, 163f, 34f, 24f), contentAlignment = Alignment.CenterStart) {
            Text("${s.food}", color = Color(0xFF14246B), fontSize = fs(13.5f, false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.at(292f, 163f, 34f, 24f), contentAlignment = Alignment.CenterStart) {
            Text("${s.water}", color = Color(0xFF14246B), fontSize = fs(13.5f, false), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (items.isEmpty()) {
            // Пустой холодильник: подпись и «В магазин» — картинка из макета.
            Art("fg3_fridge_empty_note", 18f, 338f, 363f, 121f, ContentScale.FillBounds)
            PressHit(118f, 408f, 164f, 46f, 23f) { vm.goTo(Screen.Grocery) }
        } else {
            items.drop(page * PER_PAGE).take(PER_PAGE).forEachIndexed { i, (item, n) ->
                FridgeItem(item, n, NEW_COLUMNS[i % 3], NEW_FLOORS[i / 3]) {
                    if (vm.eatFromFridge(item.id)) fb.good() else fb.bad()
                }
            }
            if (pages > 1) {
                DPill("‹", 50f, 612f, 44f, 36f, fill = Color.White, textColor = FridgeInk, size = 22f, enabled = page > 0) { pageNo = page - 1 }
                DPill("${page + 1} / $pages", 166f, 614f, 80f, 32f, fill = Color.White, textColor = FridgeInk, size = 14f) {}
                DPill("›", 318f, 612f, 44f, 36f, fill = Color.White, textColor = FridgeInk, size = 22f, enabled = page < pages - 1) { pageNo = page + 1 }
            }
        }
        EnergyBadge(vm.energyGain, 70f, 640f)
    }
}

/** Плашка с энергией из макета (+2⚡, +5⚡, +7⚡): всплывает над Монтиком и тает. */
@Composable
fun DesignScope.EnergyBadge(gain: Pair<Int, Int>, x: Float, y: Float) {
    val (amount, event) = gain
    if (event == 0) return
    val anim = androidx.compose.runtime.remember(event) { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(event) {
        anim.snapTo(0f)
        anim.animateTo(1f, androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing))
    }
    val v = anim.value
    if (v >= 1f) return
    val art = when {
        amount >= 7 -> "fg3_cu_639"
        amount >= 5 -> "fg3_energy"
        else -> "fg3_cu_641"
    }
    Box(
        Modifier.at(x, y, 120f, 60f).graphicsLayer {
            translationY = -v * 60f * density
            alpha = if (v < 0.15f) v / 0.15f else if (v > 0.7f) (1f - v) / 0.3f else 1f
        }
    ) { ArtImage(art, Modifier.fillMaxSize(), ContentScale.Fit) {} }
}
