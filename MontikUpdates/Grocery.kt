package ru.montik.app.game

/*
 * Магазин продуктов и холодильник (кадры макета «Главная», «Фрукты», «Овощи», «Напитки», «Готовая еда»,
 * «Еда», «Корзина», «Холодильник»).
 *
 * Ребёнок складывает продукты в корзину и платит за всё сразу. Купленное попадает в холодильник;
 * на кухне продукт достают из холодильника — он пропадает, а сытость и вода Монтика растут.
 * Так видно, что еда покупается заранее и заканчивается, если её не пополнять.
 *
 * Цены в макете — «магазинные» (40–350); в игре они в 10 раз меньше, чтобы совпадать с зарплатой
 * Монтика (смена помощника — 35 монет). Всё — чистый Kotlin, экраны — ui/GroceryUi.kt и ui/FridgeUi.kt.
 */

/** Раздел магазина = кадр макета. [art] — имя картинки кадра (fg_shop_<art>). */
enum class GrocerySection(val art: String, val title: String) {
    HOME("home", "Главная"),
    FRUITS("fruits", "Фрукты"),
    VEG("veg", "Овощи"),
    DRINKS("drinks", "Напитки"),
    READY("ready", "Готовая еда"),
    FOOD("food", "Еда")
}

/**
 * Продукт. [designPrice] — цена с карточки макета, [price] — цена в игре (в 10 раз меньше).
 * [food], [water], [mood] — сколько прибавится, когда Монтик его съест или выпьет.
 * [want] — желаемая покупка (вкусность, без которой можно обойтись), остальное — обязательное.
 */
data class GroceryItem(
    val id: String,
    val name: String,
    val amount: String,
    val designPrice: Int,
    val food: Int,
    val water: Int,
    val mood: Int = 0,
    val want: Boolean = false,
    val emoji: String = "🛒"
) {
    val price: Int get() = maxOf(3, (designPrice + 5) / 10)
    val kind: SpendKind get() = if (want) SpendKind.WANT else SpendKind.NEED
}

object Grocery {
    /** Сколько одинаковых продуктов помещается в холодильник (чтобы не скупать всё подряд). */
    const val MAX_PER_ITEM = 9

    private fun g(id: String, name: String, amount: String, price: Int, food: Int, water: Int, mood: Int = 0, want: Boolean = false, emoji: String) =
        GroceryItem(id, name, amount, price, food, water, mood, want, emoji)

    val all: List<GroceryItem> = listOf(
        // Фрукты
        g("apples_red", "Яблоки красные", "1 кг", 140, 15, 5, emoji = "🍎"),
        g("apples_green", "Яблоки зелёные", "1 кг", 150, 15, 5, emoji = "🍏"),
        g("oranges", "Апельсины", "1 кг", 150, 15, 8, emoji = "🍊"),
        g("mandarins", "Мандарины", "1 кг", 180, 15, 8, 3, true, "🍊"),
        g("bananas", "Бананы", "1 кг", 120, 18, 2, emoji = "🍌"),
        g("lemons", "Лимоны", "1 кг", 160, 5, 8, emoji = "🍋"),
        g("grapes", "Виноград кишмиш", "1 кг", 190, 15, 8, 3, true, "🍇"),
        g("kiwi", "Киви", "1 кг", 170, 12, 6, 2, true, "🥝"),
        // Овощи
        g("tomatoes", "Помидоры", "1 кг", 120, 12, 6, emoji = "🍅"),
        g("cucumbers", "Огурцы", "1 кг", 110, 10, 8, emoji = "🥒"),
        g("pepper", "Перец болгарский", "1 кг", 160, 12, 5, emoji = "🫑"),
        g("lettuce", "Салат листовой", "1 пучок", 90, 8, 4, emoji = "🥬"),
        g("carrots", "Морковь", "1 кг", 80, 12, 3, emoji = "🥕"),
        g("onion", "Лук репчатый", "1 кг", 70, 8, 2, emoji = "🧅"),
        g("potatoes", "Картофель", "1 кг", 60, 22, 0, emoji = "🥔"),
        g("cabbage", "Капуста белокочанная", "1 кг", 70, 12, 3, emoji = "🥬"),
        g("broccoli", "Брокколи", "1 кг", 180, 12, 3, emoji = "🥦"),
        g("radish", "Редис", "1 пучок", 90, 8, 3, emoji = "🌱"),
        g("garlic", "Чеснок", "1 кг", 150, 4, 0, emoji = "🧄"),
        g("spinach", "Шпинат", "1 пучок", 120, 8, 3, emoji = "🥬"),
        // Напитки
        g("water", "Вода негазированная", "0,5 л", 40, 0, 35, emoji = "💧"),
        g("water_sparkling", "Вода газированная", "0,5 л", 40, 0, 35, emoji = "💧"),
        g("juice_orange", "Апельсиновый сок", "1 л", 120, 5, 45, 2, emoji = "🧃"),
        g("juice_apple", "Яблочный сок", "1 л", 110, 5, 45, 2, emoji = "🧃"),
        g("juice_tomato", "Томатный сок", "1 л", 120, 8, 40, emoji = "🧃"),
        g("lemonade", "Лимонад", "0,5 л", 100, 3, 30, 5, true, "🍋"),
        g("iced_tea", "Холодный чай", "0,5 л", 110, 2, 30, 4, true, "🧋"),
        g("juice_mango", "Манговый сок", "1 л", 140, 6, 40, 4, true, "🥭"),
        g("smoothie", "Ягодный смузи", "0,3 л", 130, 10, 20, 5, true, "🫐"),
        g("oat_milk", "Овсяное молоко", "1 л", 150, 10, 30, emoji = "🥛"),
        g("almond_milk", "Миндальное молоко", "1 л", 160, 10, 30, 2, true, "🥛"),
        g("green_tea", "Зелёный чай", "0,5 л", 100, 0, 35, 2, emoji = "🍵"),
        // Готовая еда
        g("greek_salad", "Греческий салат", "250 г", 180, 30, 5, 2, emoji = "🥗"),
        g("chicken_rice", "Курица с рисом и овощами", "300 г", 220, 45, 3, 2, emoji = "🍱"),
        g("pasta_tomato", "Паста с томатным соусом", "300 г", 200, 42, 3, 2, emoji = "🍝"),
        g("pumpkin_soup", "Крем-суп из тыквы", "300 г", 160, 32, 15, 2, emoji = "🥣"),
        g("noodles_chicken", "Лапша с курицей", "300 г", 210, 42, 5, 2, emoji = "🍜"),
        g("tuna_salad", "Салат с тунцом", "250 г", 190, 32, 4, 2, emoji = "🥗"),
        g("buckwheat_meatballs", "Гречка с фрикадельками", "300 г", 220, 45, 2, 2, emoji = "🍲"),
        g("veg_bowl", "Овощной боул", "250 г", 200, 38, 6, 2, emoji = "🥙"),
        g("syrniki", "Сырники с ягодами", "200 г", 180, 35, 3, 6, true, "🥞"),
        // Еда (продукты)
        g("beef", "Говядина", "500 г", 350, 40, 0, 2, emoji = "🥩"),
        g("salmon", "Лосось", "300 г", 320, 35, 0, 2, emoji = "🐟"),
        g("chicken", "Куриное филе", "500 г", 280, 38, 0, emoji = "🍗"),
        g("eggs", "Яйца", "10 шт.", 150, 30, 0, emoji = "🥚"),
        g("cheese", "Сыр", "200 г", 220, 25, 0, 2, emoji = "🧀"),
        g("milk", "Молоко", "1 л", 90, 10, 30, emoji = "🥛"),
        g("yogurt", "Йогурт", "150 г", 80, 14, 5, 2, emoji = "🍶"),
        g("butter", "Сливочное масло", "200 г", 180, 15, 0, emoji = "🧈"),
        g("rice", "Рис", "900 г", 100, 32, 0, emoji = "🍚"),
        g("macaroni", "Макароны", "400 г", 90, 32, 0, emoji = "🍝"),
        g("buckwheat", "Гречка", "800 г", 100, 32, 0, emoji = "🌾"),
        g("oats", "Овсяные хлопья", "500 г", 90, 30, 0, emoji = "🥣"),
        // Только на «Главной»
        g("strawberries", "Клубника", "250 г", 180, 12, 6, 5, true, "🍓"),
        g("bread", "Хлеб цельнозерновой", "400 г", 120, 25, 0, emoji = "🍞"),
        g("tvorog", "Творог натуральный", "200 г", 120, 25, 0, emoji = "🥛")
    )

    private val byId = all.associateBy { it.id }

    fun item(id: String): GroceryItem? = byId[id]

    /**
     * Какие продукты стоят на карточках каждого кадра — в том же порядке, что в макете
     * (слева направо, сверху вниз). На «Главной» — «Популярное» и «Завтрак».
     */
    val sections: Map<GrocerySection, List<String>> = mapOf(
        GrocerySection.HOME to listOf("bananas", "strawberries", "yogurt", "bread", "oats", "eggs", "tvorog", "milk"),
        GrocerySection.FRUITS to listOf("apples_red", "apples_green", "oranges", "mandarins", "bananas", "lemons", "grapes", "kiwi"),
        GrocerySection.VEG to listOf(
            "tomatoes", "cucumbers", "pepper", "lettuce", "carrots", "onion",
            "potatoes", "cabbage", "broccoli", "radish", "garlic", "spinach"
        ),
        GrocerySection.DRINKS to listOf(
            "water", "water_sparkling", "juice_orange", "juice_apple", "juice_tomato", "lemonade",
            "iced_tea", "juice_mango", "smoothie", "oat_milk", "almond_milk", "green_tea"
        ),
        GrocerySection.READY to listOf(
            "greek_salad", "chicken_rice", "pasta_tomato", "pumpkin_soup", "noodles_chicken",
            "tuna_salad", "buckwheat_meatballs", "veg_bowl", "syrniki"
        ),
        GrocerySection.FOOD to listOf(
            "beef", "salmon", "chicken", "eggs", "cheese", "milk",
            "yogurt", "butter", "rice", "macaroni", "buckwheat", "oats"
        )
    )

    fun section(section: GrocerySection): List<GroceryItem> = sections[section].orEmpty().mapNotNull { item(it) }

    // ───────────────────────── Корзина ─────────────────────────

    /** Корзина — это черновик покупки (не сохраняется): id → сколько штук. */
    fun add(cart: Map<String, Int>, id: String): Map<String, Int> {
        if (item(id) == null) return cart
        val n = (cart[id] ?: 0) + 1
        return cart + (id to n.coerceAtMost(MAX_PER_ITEM))
    }

    fun remove(cart: Map<String, Int>, id: String): Map<String, Int> {
        val n = (cart[id] ?: 0) - 1
        return if (n <= 0) cart - id else cart + (id to n)
    }

    fun count(cart: Map<String, Int>): Int = cart.values.sum()

    fun total(cart: Map<String, Int>): Int = cart.entries.sumOf { (id, n) -> (item(id)?.price ?: 0) * n }

    /** Сколько в корзине обязательных и желаемых покупок (для плана бюджета). */
    fun split(cart: Map<String, Int>): Pair<Int, Int> {
        var needs = 0
        var wants = 0
        for ((id, n) in cart) {
            val it = item(id) ?: continue
            if (it.want) wants += it.price * n else needs += it.price * n
        }
        return needs to wants
    }

    /**
     * Оплатить корзину: деньги списываются сразу за всё, продукты едут в холодильник.
     * В минус покупать нельзя — если не хватает, покупка не проходит и объясняется почему.
     */
    fun checkout(state: GameState, cart: Map<String, Int>): Outcome {
        val clean = cart.filter { (id, n) -> n > 0 && item(id) != null }
        if (clean.isEmpty()) return GameEngine.fail(state, "Корзина пуста — добавь продукты кнопкой «+».")
        val total = total(clean)
        GameEngine.cannotAfford(state, total)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        val (needs, wants) = split(clean)
        val names = clean.entries.joinToString(", ") { (id, n) -> item(id)!!.name.lowercase() + if (n > 1) " ×$n" else "" }
        c.spend(needs, SpendKind.NEED, if (needs > 0) "продукты" else null)
        c.spend(wants, SpendKind.WANT, if (wants > 0) "вкусности" else null)
        var fridge = state.fridge
        for ((id, n) in clean) fridge = fridge + (id to ((fridge[id] ?: 0) + n).coerceAtMost(MAX_PER_ITEM * 3))
        c.s = c.s.copy(fridge = fridge)
        c.say(
            "🛒 Куплено на $total монет: $names. Продукты уже в холодильнике." +
                if (wants > 0) " Из них $wants — желаемые вкусности." else ""
        )
        c.log("магазин продуктов: −$total")
        c.teach(Lesson.NEEDS)
        if (wants > 0) c.teach(Lesson.WANT_NEED)
        return c.done()
    }

    // ───────────────────────── Холодильник ─────────────────────────

    /** Что лежит в холодильнике — в порядке каталога. */
    fun fridge(state: GameState): List<Pair<GroceryItem, Int>> =
        all.mapNotNull { item -> state.fridge[item.id]?.takeIf { it > 0 }?.let { item to it } }

    fun fridgeCount(state: GameState): Int = state.fridge.values.sum()

    /** Съесть или выпить продукт из холодильника: он пропадает, сытость и вода растут. */
    fun eat(state: GameState, id: String): Outcome {
        val item = item(id) ?: return GameEngine.fail(state, "Такого продукта нет.")
        val have = state.fridge[id] ?: 0
        if (have <= 0) return GameEngine.fail(state, "${item.name} закончились — купи в магазине.")
        val usefulFood = item.food > 0 && state.food < 100
        val usefulWater = item.water > 0 && state.water < 100
        if (!usefulFood && !usefulWater) {
            return GameEngine.fail(state, "Монтик сыт и не хочет пить — пусть «${item.name}» полежит в холодильнике.")
        }
        val c = GameEngine.Ctx(state)
        val left = have - 1
        c.s = c.s.copy(fridge = if (left > 0) state.fridge + (id to left) else state.fridge - id)
        c.meters(food = item.food, water = item.water)
        if (item.mood > 0) c.mood(item.mood, "Вкусно! ${item.name} подняли настроение.")
        val gains = buildList {
            if (item.food > 0) add("сытость +${item.food}")
            if (item.water > 0) add("вода +${item.water}")
        }.joinToString(", ")
        c.say("${item.emoji} ${item.name}: $gains." + if (left > 0) " В холодильнике осталось: $left." else " Это был последний.")
        c.log("из холодильника: ${item.name.lowercase()}")
        c.teach(Lesson.NEEDS)
        return c.done()
    }
}
