package ru.montik.app.game

/*
 * «Маркет» — вещи для Монтика из макета (шапка, очки, галстук, кепка, тапочки).
 * Купленная вещь остаётся навсегда в гардеробе; надеть можно по одной вещи на каждое место
 * (голова, глаза, шея, ноги). Это желаемые траты: они радуют Монтика, но не обязательны.
 */

/** Куда надевается вещь. */
enum class WearSlot { HEAD, EYES, NECK, FEET }

/**
 * Вещь маркета. [art] — картинка вещи; [x], [y], [w], [h] — рамка картинки вещи в квадрате героя
 * (доли стороны квадрата) для штрихового Монтика, как на рисунке «Так должна сидеть одежда»:
 * шапка и кепка на голове, очки на глазах, галстук у шеи, тапочки на лапках.
 */
data class MarketItem(
    val id: String,
    val name: String,
    val about: String,
    val price: Int,
    val slot: WearSlot,
    val art: String,
    val mood: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float
)

object Market {
    val items: List<MarketItem> = listOf(
        MarketItem("beanie", "Шапка", "Уютная и стильная", 120, WearSlot.HEAD, "fg2_cl_beanie", 12, 0.142f, -0.005f, 0.733f, 0.506f),
        MarketItem("spikes", "Очки", "Для особого настроения", 250, WearSlot.EYES, "fg2_cl_spikes", 18, 0.060f, 0.336f, 0.862f, 0.367f),
        MarketItem("tie", "Галстук", "Для важных дел", 180, WearSlot.NECK, "fg2_cl_tie", 14, 0.375f, 0.70f, 0.26f, 0.257f),
        MarketItem("cap", "Кепка", "Классика на каждый день", 200, WearSlot.HEAD, "fg2_cl_cap", 15, 0.131f, -0.018f, 0.785f, 0.523f),
        MarketItem("slippers", "Тапочки", "Комфорт в каждом шаге", 150, WearSlot.FEET, "fg2_cl_slippers", 12, 0.200f, 0.825f, 0.612f, 0.308f),
        MarketItem("sunglasses", "Солнечные очки", "Для солнечных дней", 100, WearSlot.EYES, "fg2_cl_glasses", 10, 0.056f, 0.361f, 0.872f, 0.379f)
    )

    fun item(id: String): MarketItem? = items.firstOrNull { it.id == id }

    /** Что сейчас надето — в порядке рисования (сначала ноги, потом шея, глаза, голова). */
    fun worn(state: GameState): List<MarketItem> =
        state.outfit.mapNotNull { item(it) }.sortedBy {
            when (it.slot) {
                WearSlot.FEET -> 0
                WearSlot.NECK -> 1
                WearSlot.EYES -> 2
                WearSlot.HEAD -> 3
            }
        }

    fun owns(state: GameState, id: String): Boolean = id in state.wardrobe

    /** Купить вещь: монеты списываются (желаемое), вещь сразу надевается. */
    fun buy(state: GameState, id: String): Outcome {
        val item = item(id) ?: return GameEngine.fail(state, "Такой вещи нет.")
        if (id in state.wardrobe) return wear(state, id)
        GameEngine.cannotAfford(state, item.price)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        c.spend(item.price, SpendKind.WANT, item.name)
        c.s = c.s.copy(wardrobe = c.s.wardrobe + id)
        c.s = wearIn(c.s, item)
        c.mood(item.mood, "Новая вещь «${item.name}» радует Монтика.")
        c.say("${item.name} куплена за ${item.price} монет и уже надета! Это желаемая трата.")
        c.log("маркет: ${item.name.lowercase()} (−${item.price})")
        c.teach(Lesson.WANT_NEED)
        return c.done()
    }

    /** Надеть купленную вещь (снимает другую вещь с того же места). */
    fun wear(state: GameState, id: String): Outcome {
        val item = item(id) ?: return GameEngine.fail(state, "Такой вещи нет.")
        if (id !in state.wardrobe) return GameEngine.fail(state, "Сначала купи «${item.name}» в маркете.")
        return Outcome(wearIn(state, item))
    }

    /** Снять вещь. */
    fun takeOff(state: GameState, id: String): Outcome = Outcome(state.copy(outfit = state.outfit - id))

    private fun wearIn(state: GameState, item: MarketItem): GameState {
        val others = state.outfit.filter { other -> item(other)?.slot != item.slot }.toSet()
        return state.copy(outfit = others + item.id)
    }
}
