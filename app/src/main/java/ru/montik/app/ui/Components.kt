package ru.montik.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.montik.app.ScenarioResultUi
import ru.montik.app.ShiftUi
import ru.montik.app.MorningUi
import ru.montik.app.game.Catalog
import ru.montik.app.game.GameEngine
import ru.montik.app.game.GameState
import ru.montik.app.game.HeroPreset
import ru.montik.app.game.Lesson
import ru.montik.app.game.Medal
import ru.montik.app.game.Rating
import ru.montik.app.game.Rules
import ru.montik.app.game.Scenario
import ru.montik.app.game.Skins
import ru.montik.app.game.Slot

// ───────────────────────── Мелкие строительные блоки ─────────────────────────

fun coinsText(n: Int): String = "$n 🪙"

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            BackCircle(onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MontikCard(
    modifier: Modifier = Modifier,
    color: Color = MontikColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    // Карточка нового макета: скругление 24, мягкая тень и тонкая светло-зелёная рамка.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = color,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Page.CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = MontikShapes.Button,
            modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
            border = if (enabled) androidx.compose.foundation.BorderStroke(1.5.dp, Page.PillBorder) else null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Page.PillGreen,
                contentColor = MontikColors.Ink,
                disabledContainerColor = MontikColors.Track,
                disabledContentColor = MontikColors.InkSoft
            )
        ) { Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MontikShapes.Button,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (enabled) Page.Outline else MontikColors.Track),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = MontikColors.Ink),
            modifier = modifier.fillMaxWidth().heightIn(min = 54.dp)
        ) { Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun StatBar(emoji: String, label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            modifier = Modifier.weight(1f).height(12.dp).clip(MontikShapes.Chip),
            color = color,
            trackColor = MontikColors.Track
        )
        Spacer(Modifier.width(8.dp))
        Text("$value", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** Плитка действия на главном экране. */
@Composable
fun ActionTile(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(MontikShapes.Card)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MontikShapes.Card,
        color = if (enabled) MontikColors.Surface else MontikColors.Track,
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 30.sp)
            Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft, textAlign = TextAlign.Center)
            }
        }
    }
}

// ───────────────────────── Монтик ─────────────────────────

/**
 * Имя картинки Монтика для заготовки. Синий — оригинал из макета, остальные — те же рисунки,
 * перекрашенные (fg_hero_<id>.webp сидит, fg_hero_<id>_stand.webp стоит спиной).
 */
fun heroArtName(preset: HeroPreset, standing: Boolean): String {
    if (!standing) {
        // Сидячий Монтик — новые рисунки из макета (Frame 33, те же, что в галерее скинов).
        // Белый — это штриховой зайка для раскраски: картинки нет, он рисуется слоями (LineArtMontik).
        return when (preset) {
            HeroPreset.WHITE -> "fg_paint_none"
            HeroPreset.BLUE -> "fg_skin_02"
            HeroPreset.GREEN -> "fg_skin_03"
            HeroPreset.PINK -> "fg_skin_04"
            HeroPreset.PURPLE -> "fg_skin_05"
            HeroPreset.ORANGE -> "fg_skin_11"
            HeroPreset.BLACK -> "fg_skin_09"
            HeroPreset.RAINBOW -> "fg_skin_10"
        }
    }
    return when (preset) {
        HeroPreset.BLUE -> "fg_story7_char"
        HeroPreset.BLACK, HeroPreset.RAINBOW -> "fg_hero_${preset.id}"
        else -> "fg_hero_${preset.id}_stand"
    }
}

/** Запасной Монтик, нарисованный кодом, — если ребёнок не фотографировал свой рисунок. */
@Composable
fun DefaultMontik(modifier: Modifier = Modifier) {
    MontikBunny(modifier)
}

/**
 * Монтик с надетой одеждой и подсказками о самочувствии.
 *
 * Пока ребёнок не сфотографировал свой рисунок и не раскрасил героя, показывается Монтик из макета Figma
 * выбранной заготовки (белый, синий, зелёный…): пушистый зайка с короной и рюкзаком — сидит
 * ([standing] = false) или стоит ([standing] = true).
 * Если герой раскрашен вручную, рисуется запасной Монтик с выбранными цветами. Без картинок макета — тоже он.
 */
@Composable
fun MontikView(
    sprite: ImageBitmap?,
    state: GameState,
    boxSize: Dp,
    palette: HeroPalette = HeroPalette.Default,
    modifier: Modifier = Modifier,
    standing: Boolean = false,
    /** Без подпрыгивания и значков состояния (Монтик спит). */
    still: Boolean = false
) {
    // Готовый Монтик-картинка (20 скинов из макета) важнее заготовки.
    val skin = Skins.picture(state.heroSkin)
    val artName = skin?.art ?: heroArtName(state.preset, standing)
    val useArt = sprite == null && (skin != null || state.heroColors.isEmpty()) && rememberHasArt(artName)
    val lineArt = rememberHasLineArt()
    val transition = rememberInfiniteTransition(label = "idle")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val emojiSize = (boxSize.value * 0.2f).sp
    Box(
        modifier = modifier.size(boxSize).offset(y = if (still) 0.dp else bounce.dp),
        contentAlignment = Alignment.Center
    ) {
        if (sprite != null) {
            Image(
                bitmap = sprite,
                contentDescription = "Монтик",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (useArt) {
            ArtImage(artName, Modifier.fillMaxSize(), ContentScale.Fit) {}
        } else if (lineArt) {
            // Раскрашенный Монтик — тот самый штриховой зайка из макета, в цветах ребёнка.
            LineArtMontik(Modifier.fillMaxSize(), palette = palette)
        } else {
            MontikBunny(Modifier.fillMaxSize(), palette = palette)
        }
        if (!still) {
            state.worn[Slot.HEAD]?.let { Catalog.clothing(it) }?.let {
                Text(it.emoji, fontSize = emojiSize, modifier = Modifier.align(Alignment.TopCenter))
            }
            state.worn[Slot.BODY]?.let { Catalog.clothing(it) }?.let {
                Text(it.emoji, fontSize = emojiSize, modifier = Modifier.align(Alignment.Center).offset(y = boxSize * 0.12f))
            }
            state.worn[Slot.FEET]?.let { Catalog.clothing(it) }?.let {
                Text(it.emoji, fontSize = emojiSize, modifier = Modifier.align(Alignment.BottomCenter))
            }
            if (state.energy < 30) {
                Text("💤", fontSize = emojiSize, modifier = Modifier.align(Alignment.TopEnd))
            }
            if (state.food < 25) {
                Text("🍽", fontSize = emojiSize, modifier = Modifier.align(Alignment.TopStart))
            }
            if (state.water < 25) {
                Text("💧", fontSize = emojiSize, modifier = Modifier.align(Alignment.CenterStart))
            }
        }
    }
}

// ───────────────────────── Ситуация «выбор» ─────────────────────────

@Composable
fun ScenarioCard(scenario: Scenario, state: GameState, onChoose: (Int) -> Unit) {
    MontikCard {
        Text("${scenario.emoji} ${scenario.title}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(scenario.situation, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        scenario.choices.forEachIndexed { index, choice ->
            val can = GameEngine.canChoose(state, scenario, choice)
            BigButton(
                text = if (can) choice.text else "${choice.text}\nНе хватает монет",
                onClick = { onChoose(index) },
                enabled = can,
                primary = false
            )
            Spacer(Modifier.height(8.dp))
        }
        if (scenario.coverFromCushion && state.cushion > 0) {
            Text(
                "🛟 Если не хватит монет в кошельке, поможет подушка безопасности: сейчас в ней ${state.cushion}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MontikColors.InkSoft
            )
        }
    }
}

// ───────────────────────── Диалоги ─────────────────────────

@Composable
private fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    buttonText: String = "Понятно",
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MontikColors.Surface,
        shape = MontikShapes.Card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), content = content)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(buttonText, style = MaterialTheme.typography.labelLarge, color = MontikColors.Ink)
            }
        }
    )
}

@Composable
fun LessonDialog(lesson: Lesson, onDismiss: () -> Unit) {
    InfoDialog(title = "${lesson.emoji} Монтик объясняет", onDismiss = onDismiss) {
        Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(lesson.text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PayslipDialog(ui: ShiftUi, onDismiss: () -> Unit) {
    val slip = ui.slip
    val fb = rememberFeedback()
    LaunchedEffect(slip) { if ((slip.stars ?: 0) >= 3) fb.fanfare() else fb.coin() }
    InfoDialog(title = "🧾 Расчётный листок", onDismiss = onDismiss) {
        slip.stars?.let { stars ->
            // Звёзды за смену в магазине: 3 — отлично, 2 — хорошо, 1 — смена отработана.
            Text(
                starsText(stars),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 40.sp,
                color = androidx.compose.ui.graphics.Color(0xFFFFC928)
            )
            if (slip.record) {
                Text(
                    "🏆 Новый рекорд в этой мини-игре!",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = LevelGreen
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(slip.jobTitle, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        PayRow("Начислено", coinsText(slip.gross))
        PayRow("Налог ${Rules.TAX_PERCENT}% (городу)", "−${coinsText(slip.tax)}")
        PayRow("На руки", coinsText(slip.net), bold = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ставка ${slip.base} × ${slip.efficiencyPercent + slip.appearancePercent + slip.taskBonusPercent + slip.careerPercent}% " +
                "(силы ${slip.efficiencyPercent}%, вид +${slip.appearancePercent}%, премия +${slip.taskBonusPercent}%" +
                (if (slip.careerPercent > 0) ", опыт конференций +${slip.careerPercent}%" else "") + ").",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft
        )
        for (note in slip.notes) {
            Spacer(Modifier.height(6.dp))
            Text("• $note", style = MaterialTheme.typography.bodyMedium)
        }
        if (ui.task != null && ui.correct != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (ui.correct) "✅ Задание решено верно!" else "❌ Задание решено неверно.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(ui.task.explanation, style = MaterialTheme.typography.bodyMedium)
        }
        for (m in ui.messages) {
            Spacer(Modifier.height(6.dp))
            Text(m, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PayRow(left: String, right: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(right, style = MaterialTheme.typography.bodyLarge, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun ScenarioResultDialog(ui: ScenarioResultUi, onDismiss: () -> Unit) {
    val fb = rememberFeedback()
    LaunchedEffect(ui) { if (ui.rating == Rating.BAD) fb.bad() else fb.good() }
    val color = when (ui.rating) {
        Rating.GREAT -> MontikColors.Good
        Rating.OK -> MontikColors.Warn
        Rating.BAD -> MontikColors.Bad
    }
    val icon = when (ui.rating) {
        Rating.GREAT -> "🌟"
        Rating.OK -> "👍"
        Rating.BAD -> "💡"
    }
    InfoDialog(title = "${ui.scenario.emoji} ${ui.scenario.title}", onDismiss = onDismiss) {
        Text("$icon ${ui.rating.title}", style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(8.dp))
        Text("Ты выбрал: ${ui.choice.text}", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        Spacer(Modifier.height(8.dp))
        Text(ui.choice.outcome, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Text("Почему так?", style = MaterialTheme.typography.titleMedium)
        Text(ui.choice.explanation, style = MaterialTheme.typography.bodyLarge)
        for (m in ui.messages) {
            Spacer(Modifier.height(6.dp))
            Text(m, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun MorningDialog(ui: MorningUi, day: Int, onDismiss: () -> Unit) {
    InfoDialog(title = "☀️ Доброе утро! День $day", onDismiss = onDismiss) {
        val diary = ui.diary
        if (diary != null) {
            Text("📒 Финансовый дневник, день ${diary.day}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            PayRow("Заработал", "+${coinsText(diary.earned)}")
            PayRow("Потратил", "−${coinsText(diary.spent)}")
            PayRow("Отложил", coinsText(diary.saved))
            Spacer(Modifier.height(6.dp))
            Text("Монтик: «Так мы учимся планировать!»", style = MaterialTheme.typography.bodyMedium, color = MontikColors.InkSoft)
        }
        for (m in ui.messages) {
            Spacer(Modifier.height(8.dp))
            Text(m, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Поздравление с новой медалью: лучи, вспышка и крупный значок.
 * Появляется поверх всего, закрывается одной кнопкой.
 */
@Composable
fun MedalDialog(medal: Medal, onDismiss: () -> Unit) {
    var shown by remember(medal.id) { mutableStateOf(false) }
    val fb = rememberFeedback()
    LaunchedEffect(medal.id) {
        shown = true
        fb.fanfare()
    }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.2f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pop"
    )
    val rays = rememberInfiniteTransition(label = "rays")
    val spin by rays.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 18000, easing = LinearEasing)),
        label = "spin"
    )
    val twinkle by rays.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MontikShapes.Card, color = MontikColors.Surface, shadowElevation = 8.dp) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Новая медаль!", style = MaterialTheme.typography.headlineMedium, color = MontikColors.Ink)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = minOf(size.width, size.height) / 2f
                        drawCircle(MontikColors.Lime.copy(alpha = 0.25f * twinkle), radius = r * 0.95f, center = c)
                        rotate(spin, pivot = c) {
                            for (i in 0 until 12) {
                                rotate(i * 30f, pivot = c) {
                                    drawRect(
                                        color = MontikColors.Coin.copy(alpha = 0.35f),
                                        topLeft = Offset(c.x - r * 0.035f, c.y - r),
                                        size = androidx.compose.ui.geometry.Size(r * 0.07f, r * 0.42f)
                                    )
                                }
                            }
                        }
                        drawCircle(Color.White, radius = r * 0.52f, center = c)
                        drawCircle(MontikColors.Coin.copy(alpha = 0.55f * twinkle), radius = r * 0.56f, center = c, style = Stroke(width = r * 0.05f))
                    }
                    Text(medal.emoji, fontSize = 64.sp, modifier = Modifier.scale(pop))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    medal.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MontikColors.Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    medal.hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.InkSoft,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                BigButton("Ура!", onClick = onDismiss)
            }
        }
    }
}

/** Небольшое всплывающее сообщение внизу экрана. */
@Composable
fun ToastBanner(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(16.dp).clickable(onClick = onClick),
        shape = MontikShapes.Card,
        color = MontikColors.Ink,
        shadowElevation = 6.dp
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun FullBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MontikColors.Cream)) { content() }
}
