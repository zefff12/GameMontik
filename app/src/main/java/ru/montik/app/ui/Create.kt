package ru.montik.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.montik.app.CreateUi
import ru.montik.app.GameViewModel
import ru.montik.app.game.Hero
import ru.montik.app.game.HeroPart
import ru.montik.app.game.HeroPreset
import ru.montik.app.game.Skins

/**
 * Экран создания героя по макету «отрисовка персонажа»: линия с именем сверху,
 * контур Монтика по центру, палитра цветов справа и подпись «нарисуй меня!» внизу.
 * Раскрашивание идёт прямо в приложении: сначала Монтик — белый человечек, выбери цвет и коснись части героя.
 * Кроме белого, можно взять готового Монтика: синего, зелёного и других — и докрасить его по-своему.
 * Часть готовых Монтиков и красок открывается за монеты (см. game/Skins.kt).
 */
@Composable
fun CreateScreen(vm: GameViewModel) {
    val ui = vm.create
    // Рисование занимает весь экран без прокрутки: движение пальцем целиком идёт на рисунок.
    if (ui is CreateUi.Drawing) {
        DrawingScreen(vm)
        return
    }
    // Новый экран раскраски из макета (Frame 33) — во весь экран, точно по картинке.
    if (ui is CreateUi.Idle && rememberHasArt("fg_paint_bg")) {
        FigmaColouringBoard(vm)
        return
    }
    Box(Modifier.fillMaxSize().background(MontikColors.Surface), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (ui) {
                is CreateUi.Idle, is CreateUi.Drawing -> ColouringBoard(vm)
                is CreateUi.Working -> {
                    Spacer(Modifier.height(80.dp))
                    CircularProgressIndicator(color = MontikColors.LimeDeep)
                    Spacer(Modifier.height(16.dp))
                    Text("Монтик появляется на свет…", style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink)
                }
                is CreateUi.Preview -> PhotoPreview(vm, ui)
                is CreateUi.Error -> PhotoError(vm, ui)
            }
        }
    }
}

/** Поле имени: линия и подпись «ИМЯ» под ней, как в макете. */
@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(Hero.MAX_NAME)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MontikColors.Ink,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(MontikColors.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.width(200.dp)
        )
        Box(Modifier.width(200.dp).height(4.dp).background(MontikColors.Ink))
        Spacer(Modifier.height(4.dp))
        Text("ИМЯ", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
    }
}

/** Палитра из семи цветов справа от героя, как в макете. */
@Composable
private fun Palette(
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    owned: (Int) -> Boolean = { true },
    onLocked: (Int) -> Unit = {}
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        for (rgb in Hero.PALETTE) {
            val color = argbColor(rgb)
            val open = owned(rgb)
            Box(
                Modifier
                    .size(55.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (rgb == selected) 4.dp else 2.dp,
                        color = if (rgb == selected) MontikColors.LimeDeep else MontikColors.Ink,
                        shape = CircleShape
                    )
                    .clickable { if (open) onPick(rgb) else onLocked(rgb) },
                contentAlignment = Alignment.Center
            ) {
                if (!open) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text("🔒", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Что ребёнок хочет открыть за монеты на экране создания. */
private class SkinOffer(val title: String, val price: Int, val art: String? = null, val buy: () -> Unit)

/** «Открыть за N монет?» — покупка скина или краски только после подтверждения. */
@Composable
private fun BuySkinDialog(vm: GameViewModel, offer: SkinOffer, onDone: () -> Unit) {
    val coins = vm.state.coins
    val canPay = coins >= offer.price
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("🔒 ${offer.title}") },
        text = {
            Text(
                if (canPay) {
                    "Открыть за ${offer.price} монет? В кошельке $coins. Это желаемая покупка: без неё можно обойтись, " +
                        "но она радует. Открывается один раз и навсегда."
                } else {
                    "Стоит ${offer.price} монет, а в кошельке $coins. Поработай на смене и возвращайся — " +
                        "изменить Монтика можно в телефоне, в «Профиле»."
                }
            )
        },
        confirmButton = {
            if (canPay) TextButton(onClick = { offer.buy(); onDone() }) { Text("Купить за ${offer.price}") }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(if (canPay) "Не сейчас" else "Понятно") }
        }
    )
}

@Composable
private fun ColouringBoard(vm: GameViewModel) {
    var colour by rememberSaveable { mutableStateOf(Hero.PALETTE.first()) }
    var lastPart by remember { mutableStateOf<HeroPart?>(null) }
    var offer by remember { mutableStateOf<SkinOffer?>(null) }
    offer?.let { o -> BuySkinDialog(vm, o) { offer = null } }

    NameField(vm.heroName) { vm.changeHeroName(it) }
    Spacer(Modifier.height(16.dp))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val side = maxWidth
            Box(
                Modifier
                    .size(side)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val s = minOf(size.width, size.height).toFloat()
                            val ox = (size.width - s) / 2f
                            val oy = (size.height - s) / 2f
                            val part = heroPartAt((offset.x - ox) / s, (offset.y - oy) / s)
                            if (part != null) {
                                lastPart = part
                                vm.paintHero(part, colour)
                            }
                        }
                    }
            ) {
                MontikBunny(
                    modifier = Modifier.fillMaxSize(),
                    palette = heroPalette(vm.heroPresetDraft, vm.heroDraft)
                )
            }
        }
        Palette(
            colour, { colour = it }, Modifier.padding(start = 8.dp),
            owned = { vm.colourOwned(it) },
            onLocked = { rgb -> offer = SkinOffer("Краска", Skins.colorPrice(rgb)) { if (vm.buyColour(rgb)) colour = rgb } }
        )
    }

    Spacer(Modifier.height(8.dp))
    PresetPicker(
        selected = vm.heroPresetDraft,
        onPick = { preset ->
            lastPart = null
            if (vm.presetOwned(preset)) vm.choosePreset(preset)
            else offer = SkinOffer("Монтик «${preset.title}»", Skins.price(preset)) { vm.buyPreset(preset) }
        },
        owned = { vm.presetOwned(it) }
    )
    Spacer(Modifier.height(12.dp))
    Text(
        when {
            lastPart != null -> "Закрашено: ${lastPart?.title}"
            vm.heroPresetDraft.isBlank -> "Это белый человечек. Выбери цвет и коснись его — он оживёт!"
            else -> "Выбери цвет и коснись Монтика, чтобы перекрасить его"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MontikColors.InkSoft,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    HandwrittenCaption("нарисуй меня!")
    Spacer(Modifier.height(16.dp))

    PillButton("Готово!", { vm.confirmColouring() })
    Spacer(Modifier.height(10.dp))
    SecondaryPill("🎲  Случайные цвета") { vm.randomColours() }
    Spacer(Modifier.height(10.dp))
    SecondaryPill("✏️  Нарисовать самому") { vm.startDrawing() }
    if (vm.redrawing) {
        Spacer(Modifier.height(10.dp))
        SecondaryPill("Отмена") { vm.cancelRedraw() }
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * Заготовки Монтика в один ряд: белый человечек и готовые Монтики из макета.
 * Повторное касание выбранной заготовки стирает раскраску.
 */
@Composable
private fun PresetPicker(selected: HeroPreset, onPick: (HeroPreset) -> Unit, owned: (HeroPreset) -> Boolean = { true }) {
    Text("Кто твой Монтик?", style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (preset in HeroPreset.values()) {
            val isSelected = preset == selected
            Column(
                Modifier.width(68.dp).clickable { onPick(preset) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MontikColors.SurfaceTint)
                        .border(
                            width = if (isSelected) 4.dp else 2.dp,
                            color = if (isSelected) MontikColors.LimeDeep else MontikColors.Ink,
                            shape = CircleShape
                        )
                        .padding(6.dp)
                ) {
                    ArtImage(heroArtName(preset, standing = false), Modifier.fillMaxSize(), ContentScale.Fit) {
                        MontikBunny(Modifier.fillMaxSize(), palette = heroPalette(preset))
                    }
                }
                Text(
                    if (owned(preset)) preset.title else "🔒 ${Skins.price(preset)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MontikColors.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Вторая по важности кнопка: белая пилюля с тёмной обводкой. */
@Composable
fun SecondaryPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .widthIn(max = MontikSizes.ButtonMaxWidth)
            .fillMaxWidth()
            .height(60.dp)
            .clip(MontikShapes.Button)
            .clickable(onClick = onClick),
        shape = MontikShapes.Button,
        color = MontikColors.Surface,
        border = androidx.compose.foundation.BorderStroke(2.dp, MontikColors.Ink)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = MontikColors.Ink, textAlign = TextAlign.Center)
        }
    }
}

/** Рукописная подпись под героем: имитация надписи от руки из макета. */
@Composable
private fun HandwrittenCaption(text: String) {
    Box(contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(28.dp)) {
                val w = size.width
                // Простой карандаш.
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.15f, w * 0.85f)
                        lineTo(w * 0.28f, w * 0.55f)
                        lineTo(w * 0.78f, w * 0.08f)
                        lineTo(w * 0.92f, w * 0.22f)
                        lineTo(w * 0.42f, w * 0.7f)
                        close()
                    },
                    color = MontikColors.Coin
                )
                drawCircle(MontikColors.Ink, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.2f, w * 0.78f))
            }
        }
    }
}

@Composable
private fun PhotoPreview(vm: GameViewModel, ui: CreateUi.Preview) {
    val image = remember(ui.bitmap) { ui.bitmap.asImageBitmap() }
    Text("Твой Монтик", style = MaterialTheme.typography.titleLarge, color = MontikColors.Ink)
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = MontikShapes.Card,
        color = MontikColors.SurfaceTint,
        modifier = Modifier.fillMaxWidth().height(320.dp)
    ) {
        Image(
            bitmap = image,
            contentDescription = "Твой Монтик",
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentScale = ContentScale.Fit
        )
    }
    if (!ui.cutOk) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Здесь фото целиком — без вырезания. Так тоже можно играть.",
            style = MaterialTheme.typography.bodyMedium,
            color = MontikColors.InkSoft,
            textAlign = TextAlign.Center
        )
    }
    Spacer(Modifier.height(16.dp))
    PillButton("Это Монтик! Играть", { vm.confirmSprite() })
    Spacer(Modifier.height(10.dp))
    SecondaryPill("✏️  Дорисовать") { vm.startDrawing() }
    Spacer(Modifier.height(10.dp))
    SecondaryPill("🎨  Вернуться к раскраске") { vm.retake() }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PhotoError(vm: GameViewModel, ui: CreateUi.Error) {
    Spacer(Modifier.height(24.dp))
    MontikCard {
        Text("Пока не получилось", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(ui.message, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(12.dp))
    Spacer(Modifier.height(16.dp))
    SecondaryPill("🎨  Вернуться к раскраске") { vm.retake() }
    Spacer(Modifier.height(24.dp))
}

/** Подсказка «как сфотографировать»: два примера, нарисованные кодом. */
@Composable
fun PhotoTips() {
    MontikCard(color = MontikColors.SurfaceTint) {
        Text("Как сфотографировать рисунок", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (good in listOf(true, false)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = MontikShapes.Card,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.1f)
                    ) {
                        SampleSheet(good, Modifier.fillMaxSize().padding(8.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (good) "✅ Так получится" else "❌ Так не выйдет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (good) MontikColors.Good else MontikColors.Bad,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "• Рисуй тёмным карандашом или фломастером на белом листе.\n" +
                "• Клади лист на стол и снимай сверху, при ровном свете.\n" +
                "• Лист целиком в кадре, а рисунок не касается его краёв.\n" +
                "• Следи, чтобы на лист не падала тень от руки или телефона.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Пример снимка: слева как надо, справа как не надо. */
@Composable
private fun SampleSheet(good: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = if (good) Color.White else Color(0xFFE6E0D2),
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f)
        )
        if (!good) {
            drawRect(
                Color(0x30000000),
                topLeft = androidx.compose.ui.geometry.Offset.Zero,
                size = androidx.compose.ui.geometry.Size(w * 0.46f, h)
            )
        }
        val ink = if (good) MontikColors.Ink else Color(0xFFA8A296)
        val line = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.035f)
        val cx = if (good) w * 0.5f else w * 0.86f
        val headR = w * 0.13f
        val headY = h * 0.38f
        drawCircle(ink, radius = headR, center = androidx.compose.ui.geometry.Offset(cx, headY), style = line)
        drawOval(
            color = ink,
            topLeft = androidx.compose.ui.geometry.Offset(cx - headR * 1.15f, headY + headR * 0.7f),
            size = androidx.compose.ui.geometry.Size(headR * 2.3f, headR * 2.2f),
            style = line
        )
        for (dx in listOf(-headR * 0.55f, headR * 0.55f)) {
            drawOval(
                color = ink,
                topLeft = androidx.compose.ui.geometry.Offset(cx + dx - headR * 0.22f, headY - headR * 2.0f),
                size = androidx.compose.ui.geometry.Size(headR * 0.44f, headR * 1.3f),
                style = line
            )
        }
    }
}


// ───────────────────────── Раскраска по новому макету (Frame 33) ─────────────────────────

/** Масштаб картинки кадра 841×1870 → холст 412. */
private const val PK = 412f / 841f

private val PaintTeal = Color(0xFF0E5357)
private val PaintGreen = Color(0xFF3DB54A)

/** Где стоят заготовки в ряду «Кто твой Монтик?» (центры кружков в пикселях картинки). */
private val AVATAR_CENTERS = listOf(99f to 978f, 246f to 979f, 375f to 979f, 506f to 979f, 631f to 979f, 758f to 977f)
private val PALETTE_Y = listOf(301f, 378f, 452f, 527f, 602f, 677f, 752f)

/**
 * Экран «Монтик / Имя» из макета: заголовок и подписи — с картинки (fg_paint_bg), а имя, Монтик
 * для раскраски, палитра справа, выбор заготовки и кнопки — живые, на своих местах.
 */
@Composable
private fun FigmaColouringBoard(vm: GameViewModel) {
    var colour by rememberSaveable { mutableStateOf(Hero.PALETTE.first()) }
    var offer by remember { mutableStateOf<SkinOffer?>(null) }
    var gallery by remember { mutableStateOf(false) }
    val masks = rememberPartMasks()
    val lineArt = rememberHasLineArt()
    val skin = Skins.picture(vm.skinDraft)
    fun p(v: Float) = v * PK

    DesignCanvas(background = {
        ArtImage("fg_paint_bg", Modifier.fillMaxSize(), ContentScale.Crop) {}
    }) {
        Art("fg_paint_bg", 0f, 0f, DESIGN_W, 1870f * PK, ContentScale.FillBounds)

        // Стрелка «назад»: при перерисовке — отмена, иначе сбросить раскраску.
        Hit(p(38f), p(103f), p(80f), p(84f)) {
            if (vm.redrawing) vm.cancelRedraw() else vm.choosePreset(vm.heroPresetDraft)
        }

        // Имя — на месте надписи «Имя», над зелёной чертой.
        Box(Modifier.at(p(300f), p(180f), p(240f), p(48f)), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = vm.heroName,
                onValueChange = { vm.changeHeroName(it.take(Hero.MAX_NAME)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    color = PaintTeal,
                    textAlign = TextAlign.Center,
                    fontSize = fs(21f, false)
                ),
                cursorBrush = SolidColor(PaintTeal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (vm.heroName.isBlank()) Text("Имя", color = PaintTeal.copy(alpha = 0.5f), fontSize = fs(21f, false))
                        inner()
                    }
                }
            )
        }

        // Монтик для раскраски — штриховой зайка из макета (Frame 33, рисунок 245×236 в точке 74,155).
        // Если выбран готовый Монтик-картинка — показываем его.
        if (skin != null) {
            ArtImage(skin.art, Modifier.at(84f, 150f, 226f, 240f), ContentScale.Fit) {}
        } else if (lineArt) {
            Box(
                Modifier
                    .at(97f, 158f, 200f, 230f)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val part = masks.partAt(offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                            if (part != null) vm.paintHero(part, colour)
                        }
                    }
            ) {
                LineArtMontik(Modifier.fillMaxSize(), palette = heroPalette(vm.heroPresetDraft, vm.heroDraft))
            }
        } else {
            val side = 262f
            Box(
                Modifier
                    .at(p(385f) - side / 2f, p(545f) - side / 2f, side, side)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val sz = minOf(size.width, size.height).toFloat()
                            val ox = (size.width - sz) / 2f
                            val oy = (size.height - sz) / 2f
                            val part = heroPartAt((offset.x - ox) / sz, (offset.y - oy) / sz)
                            if (part != null) vm.paintHero(part, colour)
                        }
                    }
            ) {
                MontikBunny(modifier = Modifier.fillMaxSize(), palette = heroPalette(vm.heroPresetDraft, vm.heroDraft))
            }
        }

        // Палитра справа: семь кружков из макета, выбранный — в зелёном кольце.
        Hero.PALETTE.forEachIndexed { i, rgb ->
            val cx = p(727f)
            val cy = p(PALETTE_Y[i])
            val r = p(32f)
            if (rgb == colour) {
                Box(Modifier.at(cx - r - 4f, cy - r - 4f, 2 * r + 8f, 2 * r + 8f).clip(CircleShape).border(d(3.5f), PaintGreen, CircleShape))
            }
            val open = vm.colourOwned(rgb)
            Box(
                Modifier
                    .at(cx - r, cy - r, 2 * r, 2 * r)
                    .clip(CircleShape)
                    .background(argbColor(rgb))
                    .border(d(2f), PaintTeal, CircleShape)
                    .clickable {
                        if (open) colour = rgb
                        else offer = SkinOffer("Краска", Skins.colorPrice(rgb)) { if (vm.buyColour(rgb)) colour = rgb }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Закрытая краска: замок, открывается за монеты.
                if (!open) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Text("🔒", fontSize = fs(12f, false))
                    }
                }
            }
        }

        // «Кто твой Монтик?» — шесть заготовок, выбранная в зелёном кольце с галочкой.
        HeroPreset.PICKER.forEachIndexed { i, preset ->
            val (px, py) = AVATAR_CENTERS[i]
            val cx = p(px)
            val cy = p(py)
            val r = p(57f)
            val selected = preset == vm.heroPresetDraft
            ArtImage("fg_paint_av_${preset.id}", Modifier.at(cx - r, cy - r, 2 * r, 2 * r), ContentScale.FillBounds) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White)) {
                    MontikBunny(Modifier.fillMaxSize(), palette = heroPalette(preset))
                }
            }
            val open = vm.presetOwned(preset)
            Box(
                Modifier.at(cx - r - 3f, cy - r - 3f, 2 * r + 6f, 2 * r + 6f)
                    .clip(CircleShape)
                    .border(if (selected) d(4f) else d(1.2f), if (selected) PaintGreen else Color(0xFFCFE3CF), CircleShape)
                    .clickable {
                        if (open) vm.choosePreset(preset)
                        else offer = SkinOffer("Монтик «${preset.title}»", Skins.price(preset)) { vm.buyPreset(preset) }
                    }
            )
            // Платный Монтик: полупрозрачный, с замком и ценой.
            if (!open) {
                Box(
                    Modifier.at(cx - r, cy - r, 2 * r, 2 * r).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) { Text("🔒", fontSize = fs(16f, false)) }
                Box(
                    Modifier.at(cx - 24f, cy + r - 12f, 48f, 18f).clip(RoundedCornerShape(d(9f))).background(PaintGreen),
                    contentAlignment = Alignment.Center
                ) { Text("🪙${Skins.price(preset)}", color = Color.White, fontSize = fs(10f, false)) }
            }
            if (selected) {
                Box(
                    Modifier.at(cx + r * 0.45f, cy + r * 0.45f, 22f, 22f).clip(CircleShape).background(PaintGreen)
                        .border(d(2f), Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("✓", color = Color.White, fontSize = fs(13f, false)) }
            }
        }

        // Кнопки макета: «Готово!» и список действий.
        // «Нарисуй меня!» → кнопка галереи: ещё 20 готовых Монтиков из макета.
        Box(
            Modifier.at(p(70f), p(1108f), p(700f), p(74f)).clip(RoundedCornerShape(d(22f)))
                .background(Color(0xFFEAF7DF)).border(d(2f), PaintGreen, RoundedCornerShape(d(22f)))
                .clickable { gallery = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (skin != null) "✨ ${skin.title} · ещё Монтики ›" else "✨ Ещё ${Skins.pictures.size} Монтиков ›",
                color = PaintTeal, fontSize = fs(15f, false), fontWeight = FontWeight.Bold
            )
        }

        // Кнопки макета (фото-кнопок больше нет: «Отмена» поднялась на их место).
        Hit(p(190f), p(1222f), p(480f), p(136f)) { vm.confirmColouring() }
        Hit(p(139f), p(1375f), p(563f), p(78f)) { vm.randomColours() }
        Hit(p(139f), p(1456f), p(563f), p(78f)) { vm.startDrawing() }
        Hit(p(139f), p(1537f), p(563f), p(78f)) {
            if (vm.redrawing) vm.cancelRedraw() else vm.choosePreset(vm.heroPresetDraft)
        }
    }
    if (gallery) {
        SkinGallery(
            vm = vm,
            selected = vm.skinDraft,
            onPick = { pic ->
                if (Skins.ownsPicture(vm.state, pic)) vm.chooseSkin(pic)
                else offer = SkinOffer("Монтик «${pic.title}»", pic.price, pic.art) { vm.buySkin(pic) }
            },
            onClose = { gallery = false }
        )
    }
    // Окно «Открыть за N монет?» — поверх экрана и галереи (раньше его здесь не было, и покупка не открывалась).
    offer?.let { o -> SkinBuyOverlay(vm, o) { offer = null } }
}

/** Покупка скина или краски в стиле макета: картинка, цена, кошелёк и две кнопки. */
@Composable
private fun SkinBuyOverlay(vm: GameViewModel, offer: SkinOffer, onDone: () -> Unit) {
    val coins = vm.state.coins
    val canPay = coins >= offer.price
    BackHandler(enabled = true) { onDone() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
    ) {
        DesignCanvas {
            Box(
                Modifier.at(31f, 220f, 350f, 470f).clip(RoundedCornerShape(d(32f))).background(Color(0xFFF4FBEC))
                    .border(d(4f), PaintGreen, RoundedCornerShape(d(32f)))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            )
            Box(
                Modifier.at(131f, 244f, 150f, 150f).clip(RoundedCornerShape(d(30f))).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (offer.art != null) {
                    ArtImage(offer.art, Modifier.fillMaxSize().padding(d(6f)), ContentScale.Fit) { Text("🎨", fontSize = fs(60f, false)) }
                } else {
                    Text("🎨", fontSize = fs(60f, false))
                }
            }
            DText(offer.title, 51f, 408f, 310f, 22f, color = PaintTeal, align = TextAlign.Center, mono = false)
            DText(
                if (canPay) "Открыть за ${offer.price} 🪙? В кошельке $coins. Это желаемая покупка: без неё можно обойтись, но она радует. Открывается навсегда."
                else "Стоит ${offer.price} 🪙, а в кошельке $coins. Поработай на смене и возвращайся!",
                51f, 446f, 310f, 15f, bold = false, color = PaintTeal, align = TextAlign.Center, mono = false
            )
            if (canPay) {
                LivePill("Купить за ${offer.price}", 51f, 600f, 190f) { offer.buy(); onDone() }
                LivePill("Не сейчас", 251f, 600f, 110f, fill = Color(0xFFB9C9B0)) { onDone() }
            } else {
                LivePill("Понятно", 121f, 600f, 170f) { onDone() }
            }
        }
    }
}
