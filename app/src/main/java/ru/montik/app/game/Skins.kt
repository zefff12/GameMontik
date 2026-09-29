package ru.montik.app.game

/**
 * Скины Монтика за монеты: готовые Монтики (заготовки [HeroPreset]) и краски для раскраски.
 *
 * Белый, синий и зелёный Монтики и первые четыре краски — бесплатно. Остальное открывается
 * за монеты один раз и навсегда. Это «желаемая» трата: ребёнок сам решает, стоит ли она денег.
 */
object Skins {
    /** Сколько первых красок из [Hero.PALETTE] доступны сразу. */
    const val FREE_COLORS = 4

    /** Цена заготовки (0 — бесплатно). */
    fun price(preset: HeroPreset): Int = when (preset) {
        HeroPreset.WHITE, HeroPreset.BLUE, HeroPreset.GREEN -> 0
        HeroPreset.PINK -> 60
        HeroPreset.ORANGE -> 60
        HeroPreset.PURPLE -> 80
        HeroPreset.BLACK -> 120
        HeroPreset.RAINBOW -> 150
    }

    fun owns(state: GameState, preset: HeroPreset): Boolean = price(preset) == 0 || preset.id in state.ownedSkins

    /** Ключ краски в списке покупок: "color_RRGGBB". */
    fun colorKey(rgb: Int): String = "color_" + (rgb and 0xFFFFFF).toString(16).padStart(6, '0')

    /** Цена краски (0 — бесплатно). Цвета не из палитры (например, из старых сохранений) — бесплатно. */
    fun colorPrice(rgb: Int): Int {
        val i = Hero.PALETTE.indexOf(rgb and 0xFFFFFF)
        return when {
            i < 0 || i < FREE_COLORS -> 0
            i == Hero.PALETTE.lastIndex -> 20
            else -> 15
        }
    }

    // ───────────────────────── Готовые Монтики из макета (Frame 33) ─────────────────────────

    /** Готовый Монтик-картинка: [art] — картинка, [tint] — цвет шёрстки (им окрашиваются сцены). */
    data class Picture(val id: String, val title: String, val price: Int, val art: String, val tint: Int)

    private fun pic(n: Int, title: String, price: Int, tint: Int) =
        Picture("s%02d".format(n), title, price, "fg_skin_%02d".format(n), tint)

    val pictures: List<Picture> = listOf(
        pic(1, "Сливочный", 0, 0xEDD3BB),
        pic(2, "Небесный", 0, 0xBDDFF7),
        pic(3, "Мятный", 0, 0xD2EFC2),
        pic(4, "Розовый", 40, 0xFBC8D3),
        pic(5, "Лавандовый", 40, 0xDAB6F9),
        pic(6, "Ванильный", 30, 0xEDD2C3),
        pic(7, "Умник в очках", 90, 0xB9D9F6),
        pic(8, "Цветочек", 90, 0xFBC5CD),
        pic(9, "Геймер", 150, 0x4F494E),
        pic(10, "Радужный", 150, 0xEFCBC8),
        pic(11, "Звёздный", 120, 0xF7B773),
        pic(12, "В капюшоне", 110, 0xB586E0),
        pic(13, "Пилот", 120, 0xC0DFB0),
        pic(14, "Звёздочка", 100, 0xE4C4F9),
        pic(15, "В шапочке", 100, 0x90C7F2),
        pic(16, "Модница", 110, 0xFBBAC8),
        pic(17, "Хаски", 130, 0xE6E2E6),
        pic(18, "Солнечный", 80, 0xFBE0AC),
        pic(19, "Голубой", 60, 0xB8DDF8),
        pic(20, "Спортсмен", 130, 0xDCCFD8)
    )

    fun picture(id: String?): Picture? = pictures.firstOrNull { it.id == id }

    fun ownsPicture(owned: Set<String>, p: Picture): Boolean = p.price == 0 || p.id in owned
    fun ownsPicture(state: GameState, p: Picture): Boolean = ownsPicture(state.ownedSkins, p)

    /** Купить готового Монтика. */
    fun buyPicture(state: GameState, p: Picture): Outcome = purchase(state, p.id, p.price, "Монтик «${p.title}»")

    /** Цвет шёрстки героя: им окрашивается белый «человечек» в сценах (банк, холодильник, касса…). */
    fun tint(state: GameState): Int = picture(state.heroSkin)?.tint ?: state.heroColor(HeroPart.FUR)

    fun ownsColor(state: GameState, rgb: Int): Boolean = colorPrice(rgb) == 0 || colorKey(rgb) in state.ownedSkins

    /** Купить заготовку Монтика. */
    fun buy(state: GameState, preset: HeroPreset): Outcome =
        purchase(state, preset.id, price(preset), "Монтик «${preset.title}»")

    /** Купить краску. */
    fun buyColor(state: GameState, rgb: Int): Outcome =
        purchase(state, colorKey(rgb), colorPrice(rgb), "краска")

    private fun purchase(state: GameState, key: String, price: Int, title: String): Outcome {
        if (price <= 0 || key in state.ownedSkins) return GameEngine.fail(state, "Это уже открыто — можно пользоваться.")
        GameEngine.cannotAfford(state, price)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        c.spend(price, SpendKind.WANT, "скин: $title")
        c.s = c.s.copy(ownedSkins = c.s.ownedSkins + key)
        c.mood(5, "Новый облик радует Монтика.")
        c.say("🎨 Открыто: $title за $price монет. Это желаемая покупка — она не обязательна, но радует.")
        c.log("купил скин: $title (−$price)")
        c.teach(Lesson.WANT_NEED)
        return c.done()
    }
}
