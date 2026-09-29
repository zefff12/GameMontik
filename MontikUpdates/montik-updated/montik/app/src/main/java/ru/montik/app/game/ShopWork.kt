package ru.montik.app.game

import kotlin.random.Random

/*
 * Работа Монтика в магазине: две мини-игры — «Касса» и «Выкладка товаров».
 * Здесь только правила: какие бывают товары, как придумываются покупатели и корзины,
 * как по результатам мини-игры считается оценка. Экраны — в ui/ShopUi.kt, оплата смены — GameEngine.workShop.
 */

/**
 * Полка на стеллаже из макета «Мини игра стенд» (сверху вниз). На каждой полке есть пустое место,
 * обведённое пунктиром, — туда ставится товар из корзины. Шестое пустое место (полка с чаем) —
 * «ловушка»: ни один товар из корзины туда не подходит.
 */
enum class ShelfCategory(val id: String, val title: String, val emoji: String) {
    BREAKFAST("breakfast", "Гранола и хлопья", "🥣"),
    PASTA("pasta", "Макароны и рис", "🍝"),
    SAUCES("sauces", "Соусы", "🥫"),
    OIL("oil", "Масло", "🫒"),
    SALT("salt", "Соль и сахар", "🧂"),
    BEANS("beans", "Бобовые и оливки", "🫘"),
    JAMS("jams", "Консервы и джемы", "🍯"),
    DRIED("dried", "Сухофрукты и семечки", "🍌");

    companion object {
        fun byId(id: String?): ShelfCategory? = values().firstOrNull { it.id == id }
    }
}

/**
 * Товар. [shelf] — на какой полке он лежит (null — товар продаётся только на кассе).
 * Цены в рублях: так написано на ценниках и на дисплее кассы в макете.
 */
data class ShopProduct(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val shelf: ShelfCategory? = null
)

object ShopCatalog {
    /**
     * Пять товаров из макета: у каждого есть своя картинка (fg_prod_*), своя полка на стеллаже
     * и цена с ценника на этом стеллаже.
     */
    val products: List<ShopProduct> = listOf(
        ShopProduct("granola", "Гранола с орехами", "🥣", 249, ShelfCategory.BREAKFAST),
        ShopProduct("pasta", "Макароны", "🍝", 99, ShelfCategory.PASTA),
        ShopProduct("tomato_sauce", "Томатный соус", "🥫", 149, ShelfCategory.SAUCES),
        ShopProduct("olive_oil", "Оливковое масло", "🫒", 399, ShelfCategory.OIL),
        ShopProduct("salt", "Соль морская", "🧂", 59, ShelfCategory.SALT),
        // Товары большого стеллажа «Вместе к лучшему» (картинки fg_prod_corn … fg_prod_eggs).
        ShopProduct("corn_puffs", "Кукурузные шарики", "🌽", 119, ShelfCategory.BREAKFAST),
        ShopProduct("olives", "Оливки", "🫒", 189, ShelfCategory.BEANS),
        ShopProduct("jam", "Ягодный джем", "🍓", 149, ShelfCategory.JAMS),
        ShopProduct("banana_chips", "Банановые чипсы", "🍌", 179, ShelfCategory.DRIED),
        ShopProduct("eggs", "Яйца", "🥚", 119, ShelfCategory.OIL)
    )

    fun byId(id: String): ShopProduct? = products.firstOrNull { it.id == id }

    /** Товары, у которых есть своя полка: из них собирается корзина для стеллажа. */
    val shelved: List<ShopProduct> = products.filter { it.shelf != null }
}

/**
 * Стеллаж для выкладки. Смены чередуются: то привычный стеллаж на пять полок, то большой
 * «Вместе к лучшему» на шесть полок с новыми товарами — чтобы работа не надоедала.
 * [slots] — какие товары подходят к каждому пустому месту (сверху вниз).
 */
enum class StandLayout(val title: String, val slots: List<Set<ShelfCategory>>) {
    CLASSIC(
        "Стеллаж у кассы",
        listOf(
            setOf(ShelfCategory.BREAKFAST), setOf(ShelfCategory.PASTA), setOf(ShelfCategory.SAUCES),
            setOf(ShelfCategory.OIL), setOf(ShelfCategory.SALT)
        )
    ),
    BIG(
        "Стеллаж «Вместе к лучшему»",
        listOf(
            setOf(ShelfCategory.BREAKFAST), setOf(ShelfCategory.PASTA, ShelfCategory.SAUCES), setOf(ShelfCategory.BEANS),
            setOf(ShelfCategory.OIL), setOf(ShelfCategory.JAMS), setOf(ShelfCategory.DRIED)
        )
    );

    /** К какому месту подходит товар (-1 — ни к какому на этом стеллаже). */
    fun slotFor(product: ShopProduct): Int = slots.indexOfFirst { product.shelf in it }
}

/** Какую мини-игру выбрал игрок. */
enum class ShopGame(val title: String, val emoji: String, val about: String) {
    CASHIER("Работа на кассе", "🧾", "Пробивай покупки и возвращай сдачу."),
    SHELVES("Выкладка товаров", "📦", "Расставь товары из корзины по своим полкам.")
}

/**
 * Должность в магазине. Растёт с числом отработанных смен ([fromShifts]) и увеличивает ставку.
 * [withChange] — с какой должности покупатель платит купюрой и нужно посчитать сдачу.
 */
enum class ShopRank(
    val title: String,
    val fromShifts: Int,
    val base: Int,
    val customers: Int,
    val maxItems: Int,
    val withChange: Boolean,
    val shelfItems: Int
) {
    TRAINEE("Стажёр", 0, 80, 3, 3, false, 4),
    CASHIER("Кассир", 5, 150, 4, 4, true, 5),
    SENIOR("Старший кассир", 12, 260, 5, 5, true, 5),
    MANAGER("Менеджер зала", 22, 400, 5, 5, true, 5);

    /** Следующая должность (null — выше некуда). */
    val next: ShopRank? get() = values().getOrNull(ordinal + 1)
}

/** Покупатель у кассы: его товары, чем он платит и варианты сдачи. */
data class Customer(
    val items: List<ShopProduct>,
    val paid: Int,
    val changeOptions: List<Int>
) {
    val total: Int get() = items.sumOf { it.price }
    val change: Int get() = paid - total
    val correctChangeIndex: Int get() = changeOptions.indexOf(change)
}

/**
 * Как обслужен один покупатель: [doubleScans] — ошибки на кассе (нажал «Готово», когда пробито не всё),
 * время и верна ли сдача (null — сдачу не считали).
 */
data class CustomerResult(
    val items: Int,
    val doubleScans: Int,
    val changeCorrect: Boolean?,
    val millis: Long,
    val parMillis: Long
)

object ShopWork {
    /** Магазин открывается, когда Монтик отработал вот столько смен на любой работе. */
    const val UNLOCK_SHIFTS = 3

    /** Купюры, которыми платят покупатели. */
    private val BANKNOTES = listOf(100, 200, 500, 1000, 2000, 5000)

    fun unlocked(state: GameState): Boolean = state.shifts >= UNLOCK_SHIFTS

    /** Подсказка, чего не хватает до магазина (null — уже можно). */
    fun lockHint(state: GameState): String? {
        val left = UNLOCK_SHIFTS - state.shifts
        return if (left > 0) "ещё смен на любой работе: $left" else null
    }

    fun rank(shopShifts: Int): ShopRank =
        ShopRank.values().lastOrNull { shopShifts >= it.fromShifts } ?: ShopRank.TRAINEE

    fun rank(state: GameState): ShopRank = rank(state.shopShifts)

    /** Сколько смен в магазине осталось до следующей должности (0 — это высшая). */
    fun shiftsToNextRank(state: GameState): Int {
        val next = rank(state).next ?: return 0
        return (next.fromShifts - state.shopShifts).coerceAtLeast(0)
    }

    private fun random(state: GameState, salt: Long) =
        Random(state.seed * 1_000_033L + state.shopShifts * 131L + state.day * 7L + salt)

    /** Покупатели для смены на кассе. Для одного и того же состояния результат одинаков. */
    fun customers(state: GameState): List<Customer> {
        val rank = rank(state)
        val rnd = random(state, 17L)
        return List(rank.customers) { makeCustomer(rnd, rank) }
    }

    private fun makeCustomer(rnd: Random, rank: ShopRank): Customer {
        val count = rnd.nextInt(2, minOf(rank.maxItems, ShopCatalog.products.size) + 1)
        val items = ShopCatalog.products.shuffled(rnd).take(count)
        val total = items.sumOf { it.price }
        val paid = BANKNOTES.first { it > total }
        val change = paid - total
        val wrong = linkedSetOf<Int>()
        val candidates = listOf(change + 10, change - 10, change + 1, change - 1, change + 50, change - 50, change + 100)
            .filter { it > 0 && it != change }
            .shuffled(rnd)
        for (w in candidates) if (wrong.size < 2) wrong += w
        val options = (wrong + change).toList().shuffled(rnd)
        return Customer(items, paid, options)
    }

    /** Какой стеллаж сегодня: смены в магазине чередуют привычный и большой. */
    fun standLayout(state: GameState): StandLayout =
        if (state.shopShifts % 2 == 1) StandLayout.BIG else StandLayout.CLASSIC

    /** Корзина для стеллажа: по одному товару на разные пустые места. */
    fun shelfBasket(state: GameState, layout: StandLayout = standLayout(state)): List<ShopProduct> {
        val rank = rank(state)
        val rnd = random(state, 91L)
        val count = if (layout == StandLayout.BIG) minOf(layout.slots.size, rank.shelfItems + 1) else rank.shelfItems
        val slots = layout.slots.indices.shuffled(rnd).take(count).sorted()
        return slots.mapNotNull { i -> ShopCatalog.shelved.filter { it.shelf in layout.slots[i] }.randomOrNull(rnd) }
    }

    /** Разумное время обслуживания покупателя: секунда-две на товар и время на сдачу. */
    fun parMillisCashier(items: Int, withChange: Boolean): Long =
        4_000L + items * 2_500L + if (withChange) 6_000L else 0L

    /** Разумное время на выкладку товаров. */
    fun parMillisShelves(items: Int): Long = 8_000L + items * 5_000L

    /** 1.0 — уложился во время, 0.0 — ушёл далеко за разумное время. */
    fun speedFactor(millis: Long, par: Long): Float {
        if (millis <= par) return 1f
        return (1f - (millis - par).toFloat() / (par * 1.5f)).coerceIn(0f, 1f)
    }

    /**
     * Оценка смены на кассе, 0..100. Учитывает внимательность (повторно пробитый товар,
     * неверная сдача) и время: за скорость добавляется до 30% оценки.
     */
    fun cashierPerformance(results: List<CustomerResult>): Int {
        if (results.isEmpty()) return 0
        val scores = results.map { r ->
            var accuracy = (1f - 0.25f * r.doubleScans).coerceAtLeast(0.25f)
            if (r.changeCorrect == false) accuracy *= 0.5f
            accuracy * (0.7f + 0.3f * speedFactor(r.millis, r.parMillis))
        }
        return (scores.average() * 100).toInt().coerceIn(0, 100)
    }

    /** Оценка выкладки, 0..100: доля верных положений с поправкой на время. */
    fun shelvesPerformance(items: Int, mistakes: Int, millis: Long): Int {
        if (items <= 0) return 0
        val accuracy = items.toFloat() / (items + mistakes)
        val score = accuracy * (0.7f + 0.3f * speedFactor(millis, parMillisShelves(items)))
        return (score * 100).toInt().coerceIn(0, 100)
    }

    /** Звёзды за смену: 3 — отлично (90+), 2 — хорошо (70+), 1 — смена отработана. */
    fun stars(performance: Int): Int = when {
        performance >= 90 -> 3
        performance >= 70 -> 2
        else -> 1
    }

    /** Лучшая оценка в мини-игре в звёздах (0 — ещё не играли). */
    fun bestStars(state: GameState, game: ShopGame): Int = state.shopBest[game.name]?.let { stars(it) } ?: 0

    /** Слова о результате для расчётного листка. */
    fun performanceNote(performance: Int): String = when {
        performance >= 90 -> "Отличная работа: быстро и без ошибок!"
        performance >= 70 -> "Хорошая работа, но есть что улучшить."
        performance >= 40 -> "Получилось не всё: ошибки и медлительность уменьшили премию."
        else -> "Было трудно. Не переживай: с каждой сменой получается лучше."
    }
}
