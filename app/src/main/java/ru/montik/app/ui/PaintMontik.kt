package ru.montik.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ru.montik.app.GameViewModel
import ru.montik.app.game.GameState
import ru.montik.app.game.HeroPart
import ru.montik.app.game.Skins

/*
 * Монтик для раскраски — штриховой рисунок из макета Figma (Frame 33).
 * Слои: белая заливка (fg_paint_base) → части героя, закрашенные цветами ребёнка
 * (fg_paint_part_fur / ears / crown / pack / belly) → контур (fg_paint_lines).
 * По тем же маскам определяется, какую часть ребёнок коснулся пальцем.
 */

/** Размер слоёв раскраски (все одинаковые). */
private const val PAINT_W = 820f
private const val PAINT_H = 943f

private val PART_LAYERS = listOf(
    HeroPart.FUR to "fg_paint_part_fur",
    HeroPart.BELLY to "fg_paint_part_belly",
    HeroPart.EARS to "fg_paint_part_ears",
    HeroPart.PACK to "fg_paint_part_pack",
    HeroPart.CROWN to "fg_paint_part_crown"
)

/** Есть ли в проекте слои раскраски из Figma. */
@Composable
fun rememberHasLineArt(): Boolean = rememberHasArt("fg_paint_base", "fg_paint_lines", "fg_paint_part_fur")

@Composable
private fun resId(name: String): Int {
    val context = LocalContext.current
    return remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
}

/** Монтик из макета в цветах [palette]; вписывается в [modifier] с сохранением пропорций. */
@Composable
fun LineArtMontik(modifier: Modifier = Modifier, palette: HeroPalette) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val base = resId("fg_paint_base")
        if (base != 0) {
            Image(painterResource(base), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        for ((part, name) in PART_LAYERS) {
            val id = resId(name)
            if (id != 0) {
                Image(
                    painterResource(id), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(palette[part], BlendMode.SrcIn)
                )
            }
        }
        val lines = resId("fg_paint_lines")
        if (lines != 0) {
            Image(painterResource(lines), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** Маски частей для касаний (загружаются один раз). */
class PartMasks(private val masks: List<Pair<HeroPart, Bitmap>>) {
    /**
     * Какая часть под пальцем. [fx], [fy] — доли (0..1) от ширины и высоты области,
     * в которую вписан рисунок ([boxW] × [boxH] в пикселях).
     */
    fun partAt(x: Float, y: Float, boxW: Float, boxH: Float): HeroPart? {
        val scale = minOf(boxW / PAINT_W, boxH / PAINT_H)
        val ox = (boxW - PAINT_W * scale) / 2f
        val oy = (boxH - PAINT_H * scale) / 2f
        val px = (x - ox) / scale
        val py = (y - oy) / scale
        if (px < 0f || py < 0f || px >= PAINT_W || py >= PAINT_H) return null
        // Сначала мелкие части (корона, платочек, ушки), потом крупные.
        for (part in listOf(HeroPart.CROWN, HeroPart.PACK, HeroPart.EARS, HeroPart.BELLY, HeroPart.FUR)) {
            val bmp = masks.firstOrNull { it.first == part }?.second ?: continue
            val bx = (px * bmp.width / PAINT_W).toInt().coerceIn(0, bmp.width - 1)
            val by = (py * bmp.height / PAINT_H).toInt().coerceIn(0, bmp.height - 1)
            if ((bmp.getPixel(bx, by) ushr 24) > 100) return part
        }
        return null
    }
}

@Composable
fun rememberPartMasks(): PartMasks {
    val context = LocalContext.current
    return remember {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        PartMasks(PART_LAYERS.mapNotNull { (part, name) ->
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id == 0) null else BitmapFactory.decodeResource(context.resources, id, opts)?.let { part to it }
        })
    }
}

// ───────────────────────── Белый «человечек» в сценах → твой Монтик ─────────────────────────

/**
 * В картинках сцен из макета (банк, холодильник, касса, приставка…) нарисован белый человечек —
 * место для героя. Слой <art>_tint повторяет его светотень; он окрашивается цветом шёрстки
 * Монтика ребёнка и ложится поверх картинки в той же рамке.
 */
@Composable
fun DesignScope.HeroTint(art: String, x: Float, y: Float, w: Float, h: Float, state: GameState) {
    val id = resId(art + "_tint")
    if (id == 0) return
    val rgb = Skins.tint(state)
    // Слишком тёмную шёрстку чуть осветляем, чтобы был виден контур.
    val c = argbColor(rgb)
    val lum = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
    val color = if (lum < 0.25f) Color(
        red = c.red * 0.7f + 0.3f * 0.45f,
        green = c.green * 0.7f + 0.3f * 0.45f,
        blue = c.blue * 0.7f + 0.3f * 0.45f
    ) else c
    Image(
        painterResource(id), null, Modifier.at(x, y, w, h),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color, BlendMode.Modulate)
    )
}

// ───────────────────────── Галерея готовых Монтиков ─────────────────────────

/** Все 20 готовых Монтиков из макета: купленные — выбрать, остальные — купить за монеты. */
@Composable
fun SkinGallery(vm: GameViewModel, selected: String?, onPick: (Skins.Picture) -> Unit, onClose: () -> Unit) {
    val s = vm.state
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        DesignCanvas {
            Box(
                Modifier.at(12f, 60f, 388f, 800f).clip(RoundedCornerShape(d(36f))).background(Color(0xFFF4FBEC))
                    .border(d(4f), Color(0xFF8AD742), RoundedCornerShape(d(36f)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            )
            DText("Кто твой Монтик?", 12f, 78f, 388f, 24f, color = Color(0xFF0E5357), align = TextAlign.Center, mono = false)
            DText("Монеты: ${s.coins} 🪙", 12f, 112f, 388f, 14f, bold = false, color = Color(0xFF0E5357), align = TextAlign.Center, mono = false)
            Skins.pictures.forEachIndexed { i, p ->
                val col = i % 4
                val row = i / 4
                val x = 24f + col * 92f
                val y = 140f + row * 128f
                val owned = Skins.ownsPicture(s, p)
                val isSel = p.id == selected
                Box(
                    Modifier.at(x, y, 88f, 88f).clip(RoundedCornerShape(d(22f))).background(Color.White)
                        .border(if (isSel) d(4f) else d(1.5f), if (isSel) Color(0xFF3DB54A) else Color(0xFFCFE3CF), RoundedCornerShape(d(22f)))
                        .clickable { onPick(p) },
                    contentAlignment = Alignment.Center
                ) {
                    ArtImage(p.art, Modifier.fillMaxSize().padding(d(4f)), ContentScale.Fit) {}
                    if (!owned) {
                        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = d(4f)).clip(RoundedCornerShape(d(9f)))
                                .background(Color(0xFF3DB54A)).padding(horizontal = d(6f), vertical = d(1f))
                        ) { Text("🔒 ${p.price}", color = Color.White, fontSize = fs(10f, false), fontWeight = FontWeight.Bold) }
                    }
                    if (isSel) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(d(4f)).clip(CircleShape).background(Color(0xFF3DB54A)),
                            contentAlignment = Alignment.Center
                        ) { Text(" ✓ ", color = Color.White, fontSize = fs(11f, false)) }
                    }
                }
                DText(p.title, x - 4f, y + 90f, 96f, 10.5f, bold = false, color = Color(0xFF0E5357), align = TextAlign.Center, mono = false)
            }
            DPill("Закрыть", 121f, 790f, 170f, 52f, fill = Color(0xFF8AD742)) { onClose() }
        }
    }
}

// ───────────────────────── Монтик ребёнка в сценах ─────────────────────────

/**
 * Твой Монтик (скин, раскраска или рисунок) в сцене из макета — на месте белого человечка,
 * которого мы стёрли из картинки. [size] — сторона квадрата в координатах макета.
 */
@Composable
fun DesignScope.SceneMontik(vm: GameViewModel, x: Float, y: Float, size: Float) {
    MontikView(
        sprite = vm.sprite,
        state = vm.state,
        boxSize = d(size),
        palette = heroPalette(vm.state),
        modifier = Modifier.at(x, y)
    )
}
