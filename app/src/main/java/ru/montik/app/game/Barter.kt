package ru.montik.app.game

/**
 * Рынок енота Сергеевича — игра про бартер (кадры макета «при первом приходе на рынок»,
 * Android Compact 41–63, «Поздравляем! Успешный бартер»).
 *
 * Раз в [INTERVAL_DAYS] игровых дней Монтику приходит сообщение: «Загляни на рынок».
 * Енот объясняет, что такое бартер, и предлагает свой товар (мёд или травяной чай).
 * Монтик открывает инвентарь — продукты из холодильника и услугу «помочь на рынке» — и
 * собирает предложение. Енот соглашается, только если обмен честный для обоих:
 * слишком мало — невыгодно еноту, слишком много — невыгодно самому Монтику.
 */
object Barter {
    const val INTERVAL_DAYS = 10

    /** Услуга: помочь Сергеевичу разложить товар. Есть всегда, даже если холодильник пуст. */
    const val SERVICE_ID = "service_help"
    const val SERVICE_VALUE = 16

    /** Честное предложение: от 80% до 150% ценности товара енота. */
    const val FAIR_MIN_PERCENT = 80
    const val FAIR_MAX_PERCENT = 150

    const val UNDERSTANDING_PER_TRADE = 25
    const val XP_PER_TRADE = 15

    /** Товары енота: id продукта в [Grocery] (после обмена он попадает в холодильник). */
    val goods: List<String> = listOf("honey", "herbal_tea")

    /** Строка инвентаря Монтика. [value] — ценность одной штуки в монетах. */
    data class Slot(val id: String, val title: String, val emoji: String, val count: Int, val value: Int)

    enum class Verdict { EMPTY, TOO_LITTLE, FAIR, TOO_MUCH }

    /** Пора ли позвать Монтика на рынок. */
    fun due(state: GameState): Boolean =
        state.created && state.introDone && state.day - state.barterDay >= INTERVAL_DAYS

    /** Первый ли это визит (тогда енот рассказывает, что такое бартер). */
    fun firstVisit(state: GameState): Boolean = state.barterSessions == 0 && Lesson.BARTER.name !in state.seenLessons

    fun goodValue(id: String): Int = Grocery.item(id)?.price ?: 0

    /** Инвентарь: услуга и продукты из холодильника (кроме товаров самого енота). */
    fun inventory(state: GameState): List<Slot> {
        val service = Slot(SERVICE_ID, "Помочь разложить товар", "🧺", 1, SERVICE_VALUE)
        val food = Grocery.fridge(state)
            .filter { (item, _) -> item.id !in goods }
            .map { (item, n) -> Slot(item.id, item.name, item.emoji, n, item.price) }
        return listOf(service) + food
    }

    /** Ценность предложения: [offer] — id → сколько штук. */
    fun offerValue(state: GameState, offer: Map<String, Int>): Int {
        val slots = inventory(state).associateBy { it.id }
        return offer.entries.sumOf { (id, n) ->
            val slot = slots[id] ?: return@sumOf 0
            slot.value * n.coerceIn(0, slot.count)
        }
    }

    fun judge(wantId: String, value: Int): Verdict {
        val target = goodValue(wantId)
        return when {
            value <= 0 -> Verdict.EMPTY
            value * 100 < target * FAIR_MIN_PERCENT -> Verdict.TOO_LITTLE
            value * 100 > target * FAIR_MAX_PERCENT -> Verdict.TOO_MUCH
            else -> Verdict.FAIR
        }
    }

    /** Что скажет енот на предложение. */
    fun reply(verdict: Verdict): String = when (verdict) {
        Verdict.EMPTY -> "Ты пока ничего не предложил. Открой инвентарь и выбери, что отдать."
        Verdict.TOO_LITTLE -> "Хм... Это слишком мало за мой товар. Мне такая сделка невыгодна. Добавь что-нибудь!"
        Verdict.TOO_MUCH -> "Постой-постой! Ты отдаёшь слишком много — это невыгодно тебе. Честная сделка должна быть удобной для обоих. Убери лишнее."
        Verdict.FAIR -> "Отлично... Хороший выбор! Условия подходят нам обоим — по рукам!"
    }

    /** Совершить обмен: предложение уходит еноту, его товар — в холодильник Монтика. */
    fun trade(state: GameState, wantId: String, offer: Map<String, Int>): Outcome {
        val want = Grocery.item(wantId)
        if (want == null || wantId !in goods) return GameEngine.fail(state, "У енота нет такого товара.")
        val clean = offer.filter { it.value > 0 }
        val slots = inventory(state).associateBy { it.id }
        for ((id, n) in clean) {
            val slot = slots[id] ?: return GameEngine.fail(state, "Этого нет в инвентаре.")
            if (n > slot.count) return GameEngine.fail(state, "Столько у Монтика нет: ${slot.title}.")
        }
        val verdict = judge(wantId, offerValue(state, clean))
        if (verdict != Verdict.FAIR) return GameEngine.fail(state, reply(verdict))

        val c = GameEngine.Ctx(state)
        var fridge = c.s.fridge
        for ((id, n) in clean) {
            if (id == SERVICE_ID) continue
            val left = (fridge[id] ?: 0) - n
            fridge = if (left > 0) fridge + (id to left) else fridge - id
        }
        fridge = fridge + (wantId to ((fridge[wantId] ?: 0) + 1).coerceAtMost(Grocery.MAX_PER_ITEM * 3))
        val gave = clean.entries.joinToString(", ") { (id, n) ->
            val title = slots[id]?.title?.lowercase() ?: id
            if (n > 1) "$title ×$n" else title
        }
        c.s = c.s.copy(
            fridge = fridge,
            barterDay = c.s.day,
            barterSessions = c.s.barterSessions + 1,
            barterUnderstanding = (c.s.barterUnderstanding + UNDERSTANDING_PER_TRADE).coerceAtMost(100)
        )
        if (SERVICE_ID in clean) c.meters(energy = -10)
        c.mood(6, "Удачный обмен на рынке!")
        c.xp(XP_PER_TRADE)
        c.say("🦝 Успешный бартер! Монтик отдал: $gave — и получил: ${want.emoji} ${want.name}. Уже в холодильнике.")
        c.log("бартер на рынке: $gave → ${want.name.lowercase()}")
        c.teach(Lesson.BARTER)
        return c.done()
    }

    /** Монтик ушёл с рынка без обмена: следующее приглашение — через [INTERVAL_DAYS] дней. */
    fun leave(state: GameState): GameState = state.copy(barterDay = state.day)

    fun levelTitle(understanding: Int): String = when {
        understanding < 25 -> "Новичок на рынке"
        understanding < 50 -> "Учится меняться"
        understanding < 75 -> "Честный торговец"
        else -> "Мастер бартера"
    }
}
