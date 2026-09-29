package ru.montik.app.game

import kotlin.random.Random

/*
 * Свой магазин Монтика. Когда накоплено 20 000 монет, Монтик решает открыть дело:
 * найти место → выбрать, чем торговать → построить → купить оборудование и первые товары → открыть.
 * После открытия он владелец: закупает товары, следит за выручкой и расходами, улучшает магазин.
 *
 * Это ядро без экранов: правила и расчёты. Экраны — в ui/BusinessUi.kt.
 */

/** Место для магазина: дороже — больше прохожих, но и аренда выше. */
enum class BizPlace(
    val id: String,
    val title: String,
    val emoji: String,
    val about: String,
    /** Оформление аренды или покупки помещения — один раз. */
    val price: Int,
    /** Ремонт и подготовка помещения — один раз. */
    val buildCost: Int,
    /** Аренда за каждый день. */
    val rent: Int,
    /** Сколько людей в день заходит в магазин на этой улице (для «продуктов» на простом оборудовании). */
    val traffic: Int
) {
    ALLEY("alley", "Тихий переулок", "🏘", "Дёшево, но мимо ходит мало людей.", 2000, 2000, 40, 30),
    DISTRICT("district", "Спальный район", "🏢", "Рядом много жилых домов, покупатели ходят каждый день.", 3500, 3500, 80, 50),
    CENTER("center", "Центральная улица", "🏙", "Дорого, зато людей здесь больше всего.", 7000, 5000, 150, 80);

    companion object {
        fun byId(id: String?): BizPlace? = values().firstOrNull { it.id == id }
    }
}

/** Чем торгует магазин. [basket] — средний чек, [unitCost] — во сколько Монтику обходятся товары на одного покупателя. */
enum class BizType(
    val id: String,
    val title: String,
    val emoji: String,
    val about: String,
    val basket: Int,
    val unitCost: Int,
    /** Насколько охотно сюда заходят: 100 — как в магазине продуктов. */
    val footfall: Int
) {
    FOOD("food", "Продукты", "🛒", "Покупатели ходят часто, но наценка небольшая.", 40, 29, 100),
    TOYS("toys", "Игрушки и книги", "🧸", "Заходят реже, зато чек больше.", 60, 37, 75),
    CLOTHES("clothes", "Одежда", "👕", "Покупателей мало, но на каждом зарабатываешь больше.", 70, 38, 45);

    /** Прибыль с одного покупателя до расходов на аренду. */
    val margin: Int get() = basket - unitCost

    companion object {
        fun byId(id: String?): BizType? = values().firstOrNull { it.id == id }
    }
}

/** Оборудование: стеллажи, касса, свет. Чем лучше — тем больше покупателей помещается и приходит. */
enum class BizEquip(
    val level: Int,
    val title: String,
    val emoji: String,
    val about: String,
    val price: Int,
    /** Сколько покупателей за день успевает обслужить магазин. */
    val capacity: Int,
    /** Насколько оборудование привлекает покупателей (в процентах). */
    val attract: Int,
    /** Свет, отопление и обслуживание — каждый день. */
    val upkeep: Int
) {
    BASIC(1, "Простые стеллажи и касса", "🗄", "Всё самое нужное. Недорого.", 1500, 40, 100, 10),
    GOOD(2, "Хорошее оборудование", "🛋", "Удобные стеллажи, современная касса.", 3000, 70, 125, 25),
    FINE(3, "Красивое оборудование", "✨", "Яркая витрина и быстрая касса: покупателей больше.", 5500, 110, 160, 45);

    companion object {
        fun byLevel(level: Int): BizEquip? = values().firstOrNull { it.level == level }
    }
}

/** Этапы пути от идеи до открытия. После [OPEN] Монтик уже владелец. */
enum class BizStage(val title: String) {
    PLACE("Выбор места"),
    TYPE("Что продаём"),
    BUILD("Строительство"),
    EQUIP("Оборудование"),
    STOCK("Первые товары"),
    OPEN("Магазин открыт")
}

/** Итог одного дня торговли — «отчёт владельца». */
data class BizReport(
    val day: Int,
    /** Сколько покупателей хотели зайти. */
    val demand: Int,
    /** Сколько обслужили (не больше, чем хватило товаров и места). */
    val served: Int,
    val revenue: Int,
    val goodsCost: Int,
    val rent: Int,
    val upkeep: Int,
    /** Особое событие дня (null — обычный день). */
    val event: String?
) {
    val lost: Int get() = (demand - served).coerceAtLeast(0)
    val expenses: Int get() = rent + upkeep
    val profit: Int get() = revenue - goodsCost - expenses
}

/** Свой магазин: всё, что нужно помнить об этапах, товарах, кассе и отчётах. */
data class Business(
    val stage: BizStage = BizStage.PLACE,
    val place: String? = null,
    val type: String? = null,
    /** Уровень оборудования: 0 — ещё нет. */
    val equip: Int = 0,
    /** Сколько раз расширяли помещение. */
    val expansion: Int = 0,
    /** Товара на складе — в «покупателях»: на скольких покупателей хватит. */
    val stock: Int = 0,
    /** Выручка в кассе: пока её не забрали, это деньги магазина, а не Монтика. */
    val till: Int = 0,
    /** Долг по аренде и расходам: копится, если денег не хватило. */
    val overdue: Int = 0,
    val daysOpen: Int = 0,
    val totalRevenue: Int = 0,
    val totalProfit: Int = 0,
    /** Сколько дней ещё действует реклама. */
    val adDays: Int = 0,
    /** Касса сломалась: магазин работает вполсилы, пока её не починят. */
    val broken: Boolean = false,
    val report: BizReport? = null
) {
    val placeInfo: BizPlace? get() = BizPlace.byId(place)
    val typeInfo: BizType? get() = BizType.byId(type)
    val equipInfo: BizEquip? get() = BizEquip.byLevel(equip)
    val isOpen: Boolean get() = stage == BizStage.OPEN

    companion object {
        /** Запись для сохранения: поля через «|», отчёт — через «,» в последнем поле. */
        fun encode(b: Business): String = listOf(
            b.stage.name, b.place.orEmpty(), b.type.orEmpty(), b.equip, b.expansion, b.stock, b.till,
            b.overdue, b.daysOpen, b.totalRevenue, b.totalProfit, b.adDays, b.broken,
            b.report?.let { r ->
                listOf(r.day, r.demand, r.served, r.revenue, r.goodsCost, r.rent, r.upkeep, r.event.orEmpty())
                    .joinToString(",")
            }.orEmpty()
        ).joinToString("|")

        /** Возвращает null, если запись повреждена. */
        fun decode(text: String?): Business? {
            if (text.isNullOrBlank()) return null
            return try {
                val f = text.split('|')
                val stage = BizStage.values().firstOrNull { it.name == f[0] } ?: return null
                fun int(i: Int) = f.getOrNull(i)?.toIntOrNull() ?: 0
                val report = f.getOrNull(13)?.takeIf { it.isNotBlank() }?.split(',')?.let { r ->
                    fun n(i: Int) = r.getOrNull(i)?.toIntOrNull() ?: 0
                    BizReport(n(0), n(1), n(2), n(3), n(4), n(5), n(6), r.getOrNull(7)?.takeIf { it.isNotBlank() })
                }
                Business(
                    stage = stage,
                    place = BizPlace.byId(f.getOrNull(1))?.id,
                    type = BizType.byId(f.getOrNull(2))?.id,
                    equip = int(3).coerceIn(0, 3),
                    expansion = int(4).coerceIn(0, BusinessEngine.MAX_EXPANSION),
                    stock = int(5).coerceAtLeast(0),
                    till = int(6).coerceAtLeast(0),
                    overdue = int(7).coerceAtLeast(0),
                    daysOpen = int(8).coerceAtLeast(0),
                    totalRevenue = int(9).coerceAtLeast(0),
                    totalProfit = int(10),
                    adDays = int(11).coerceAtLeast(0),
                    broken = f.getOrNull(12) == "true",
                    report = report
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Что произошло на этапе: удалось ли действие и что сказать ребёнку. Состояние возвращается в [Outcome]. */
object BusinessEngine {
    /** Сколько монет нужно накопить, чтобы задуматься о своём деле. */
    const val GOAL_COINS = 20_000

    /** Метки в [GameState.milestones]. */
    const val WEALTH_MARK = "wealth_20k"
    const val OFFERED_MARK = "biz_offered"

    /** Налог с выручки при снятии денег из кассы (упрощённая система для малого бизнеса). */
    const val TAX_PERCENT = 6

    const val AD_PRICE = 300
    const val AD_DAYS = 3
    const val AD_BOOST_PERCENT = 30

    const val REPAIR_PRICE = 400
    const val BROKEN_CAPACITY_PERCENT = 40

    const val MAX_EXPANSION = 2
    val EXPANSION_PRICES = listOf(3500, 6000)
    const val EXPANSION_CAPACITY = 30
    const val EXPANSION_RENT = 30

    /** Варианты первой закупки и стандартные закупки: сколько покупателей обслужит товар. */
    val FIRST_STOCK = listOf(40, 80, 160)
    val RESTOCK_OPTIONS = listOf(40, 100, 200)

    /** Вероятности событий дня (в процентах). */
    private const val HOLIDAY_CHANCE = 12
    private const val BREAKDOWN_CHANCE = 10
    private const val HOLIDAY_BOOST_PERCENT = 50

    // ───────────────────────── Что есть у Монтика ─────────────────────────

    /** Всё, что Монтик накопил: кошелёк, подушка и вклад за вычетом долга банку. */
    fun wealth(state: GameState): Int = state.coins + state.cushion + state.piggy + state.deposit - state.debt

    /** Пора показать Монтику идею открыть магазин. */
    fun offerPending(state: GameState): Boolean =
        WEALTH_MARK in state.milestones && OFFERED_MARK !in state.milestones && state.business == null

    /** Можно ли начать (или продолжить) путь к своему магазину. */
    fun canStart(state: GameState): Boolean = WEALTH_MARK in state.milestones || state.business != null

    /** Цель для главного экрана. */
    fun goal(state: GameState): String {
        val b = state.business
        return when {
            b != null && b.isOpen -> "🏬 Развивай свой магазин: закупай товары и следи за прибылью."
            b != null -> "🏗 Открыть свой магазин: этап «${b.stage.title}»."
            canStart(state) -> "🏬 Открыть свой магазин: денег хватает, нажми «Работа» → «Свой бизнес»."
            else -> "🏬 Накопить $GOAL_COINS монет на свой магазин: сейчас ${wealth(state)}."
        }
    }

    // ───────────────────────── Расчёт торговли ─────────────────────────

    /** Сколько покупателей может обслужить магазин за день (место на кассе и в зале). */
    fun capacity(b: Business): Int {
        val base = (b.equipInfo?.capacity ?: 0) + b.expansion * EXPANSION_CAPACITY
        return if (b.broken) base * BROKEN_CAPACITY_PERCENT / 100 else base
    }

    /** Сколько покупателей хотят зайти в обычный день (событий нет). */
    fun demand(b: Business): Int {
        val place = b.placeInfo ?: return 0
        val type = b.typeInfo ?: return 0
        val equip = b.equipInfo ?: return 0
        var d = place.traffic * type.footfall / 100 * equip.attract / 100
        if (b.adDays > 0) d = d * (100 + AD_BOOST_PERCENT) / 100
        return d
    }

    /** Ожидаемое число обслуженных покупателей в обычный день. */
    fun expectedServed(b: Business): Int = minOf(demand(b), capacity(b))

    /** Расходы на день: аренда и содержание. */
    fun dailyRent(b: Business): Int = (b.placeInfo?.rent ?: 0) + b.expansion * EXPANSION_RENT

    fun dailyUpkeep(b: Business): Int = b.equipInfo?.upkeep ?: 0

    /** Ожидаемая прибыль за обычный день, если товара хватает. */
    fun expectedProfit(b: Business): Int {
        val type = b.typeInfo ?: return 0
        return expectedServed(b) * type.margin - dailyRent(b) - dailyUpkeep(b)
    }

    /** Сколько монет стоит закупка на [baskets] покупателей. */
    fun stockCost(type: BizType, baskets: Int): Int = type.unitCost * baskets

    /** Налог с суммы, забираемой из кассы. */
    fun tax(amount: Int): Int = (amount * TAX_PERCENT + 50) / 100

    /** Сколько дней хватит товара при обычном спросе. */
    fun stockDays(b: Business): Float {
        val need = expectedServed(b)
        return if (need <= 0) 0f else b.stock.toFloat() / need
    }

    // ───────────────────────── Действия Монтика ─────────────────────────

    private fun current(state: GameState, stage: BizStage): Business? =
        state.business?.takeIf { it.stage == stage }

    private fun put(c: GameEngine.Ctx, b: Business) {
        c.s = c.s.copy(business = b)
    }

    /** Монтик решил открыть своё дело (нажал «Открыть свой бизнес»). */
    fun begin(state: GameState): Outcome {
        if (state.business != null) return Outcome(state.copy(milestones = state.milestones + OFFERED_MARK))
        if (!canStart(state)) return GameEngine.fail(state, "Сначала нужно накопить $GOAL_COINS монет.")
        val c = GameEngine.Ctx(state.copy(milestones = state.milestones + OFFERED_MARK))
        put(c, Business(stage = BizStage.PLACE))
        c.say("🏬 Монтик решил открыть свой магазин! Сначала нужно найти подходящее место.")
        c.log("решил открыть свой магазин")
        c.teach(Lesson.INVEST)
        return c.done()
    }

    /** «Пока рано»: идея остаётся, но больше не всплывает сама. */
    fun postpone(state: GameState): Outcome =
        Outcome(state.copy(milestones = state.milestones + OFFERED_MARK))

    /** Выбрать место и оформить аренду. */
    fun rentPlace(state: GameState, placeId: String): Outcome {
        val b = current(state, BizStage.PLACE) ?: return GameEngine.fail(state, "Место уже выбрано.")
        val place = BizPlace.byId(placeId) ?: return GameEngine.fail(state, "Такого места нет.")
        if (state.coins < place.price) {
            return GameEngine.fail(state, "Не хватает монет в кошельке: нужно ${place.price}, а есть ${state.coins}. Сними деньги с вклада.")
        }
        val c = GameEngine.Ctx(state)
        c.spend(place.price, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(stage = BizStage.TYPE, place = place.id))
        c.say("📍 Место выбрано: ${place.title.lowercase()}. Оформление аренды: −${place.price} монет. Теперь аренда будет стоить ${place.rent} в день.")
        c.log("выбрал место: ${place.title} (−${place.price})")
        return c.done()
    }

    fun chooseType(state: GameState, typeId: String): Outcome {
        val b = current(state, BizStage.TYPE) ?: return GameEngine.fail(state, "Сейчас нельзя выбрать, чем торговать.")
        val type = BizType.byId(typeId) ?: return GameEngine.fail(state, "Такого магазина нет.")
        val c = GameEngine.Ctx(state)
        put(c, b.copy(stage = BizStage.BUILD, type = type.id))
        c.say("${type.emoji} Монтик будет торговать так: «${type.title.lowercase()}».")
        c.log("тип магазина: ${type.title}")
        return c.done()
    }

    /** Строительство: ремонт и подготовка помещения. */
    fun build(state: GameState): Outcome {
        val b = current(state, BizStage.BUILD) ?: return GameEngine.fail(state, "Сейчас нечего строить.")
        val place = b.placeInfo ?: return GameEngine.fail(state, "Сначала выбери место.")
        if (state.coins < place.buildCost) {
            return GameEngine.fail(state, "На строительство не хватает монет: нужно ${place.buildCost}, а есть ${state.coins}.")
        }
        val c = GameEngine.Ctx(state)
        c.spend(place.buildCost, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(stage = BizStage.EQUIP))
        c.say("🏗 Помещение отремонтировано: −${place.buildCost} монет. Теперь нужно оборудование.")
        c.log("построил магазин (−${place.buildCost})")
        return c.done()
    }

    /** Купить оборудование. Пока магазин строится — первое; после открытия — улучшение (платят только разницу). */
    fun buyEquipment(state: GameState, level: Int): Outcome {
        val b = state.business ?: return GameEngine.fail(state, "У Монтика пока нет магазина.")
        val equip = BizEquip.byLevel(level) ?: return GameEngine.fail(state, "Такого оборудования нет.")
        val building = b.stage == BizStage.EQUIP
        if (!building && !(b.isOpen && level > b.equip)) {
            return GameEngine.fail(state, "Это оборудование сейчас не нужно.")
        }
        val price = if (building) equip.price else equip.price - (b.equipInfo?.price ?: 0)
        if (state.coins < price) {
            return GameEngine.fail(state, "Не хватает монет: нужно $price, а есть ${state.coins}.")
        }
        val c = GameEngine.Ctx(state)
        c.spend(price, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(stage = if (building) BizStage.STOCK else b.stage, equip = equip.level))
        c.say("${equip.emoji} ${equip.title}: −$price монет. Магазин вмещает до ${equip.capacity} покупателей в день.")
        c.log("оборудование: ${equip.title} (−$price)")
        return c.done()
    }

    /**
     * Первая закупка — после неё магазин открывается. Дальше эта же функция пополняет склад.
     * [baskets] — на скольких покупателей закупаются товары.
     */
    fun buyStock(state: GameState, baskets: Int): Outcome {
        val b = state.business ?: return GameEngine.fail(state, "У Монтика пока нет магазина.")
        if (b.stage != BizStage.STOCK && !b.isOpen) return GameEngine.fail(state, "Сначала нужно построить магазин и купить оборудование.")
        val type = b.typeInfo ?: return GameEngine.fail(state, "Сначала выбери, чем торговать.")
        if (baskets <= 0) return GameEngine.fail(state, "Выбери, сколько товаров закупить.")
        val cost = stockCost(type, baskets)
        if (state.coins < cost) {
            return GameEngine.fail(state, "На закупку не хватает монет: нужно $cost, а есть ${state.coins}.")
        }
        val c = GameEngine.Ctx(state)
        c.spend(cost, SpendKind.OTHER, "свой магазин")
        val opening = b.stage == BizStage.STOCK
        put(c, b.copy(stage = BizStage.OPEN, stock = b.stock + baskets))
        if (opening) {
            c.say("🎉 Магазин открыт! Закуплено товаров на $baskets покупателей (−$cost монет). Первые покупатели уже идут.")
            c.log("открыл свой магазин (товары −$cost)")
            c.skill(Skill.ENTREPRENEUR, 3)
        } else {
            c.say("📦 Закуплено товаров на $baskets покупателей: −$cost монет. На складе — ${b.stock + baskets}.")
            c.log("закупка товаров (−$cost)")
        }
        c.teach(Lesson.PROFIT)
        return c.done()
    }

    /** Расширить помещение: в магазине помещается больше покупателей, но и аренда выше. */
    fun expand(state: GameState): Outcome {
        val b = state.business?.takeIf { it.isOpen } ?: return GameEngine.fail(state, "Магазин ещё не открыт.")
        if (b.expansion >= MAX_EXPANSION) return GameEngine.fail(state, "Магазин уже расширен до предела.")
        val price = EXPANSION_PRICES[b.expansion]
        if (state.coins < price) return GameEngine.fail(state, "Не хватает монет: нужно $price, а есть ${state.coins}.")
        val c = GameEngine.Ctx(state)
        c.spend(price, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(expansion = b.expansion + 1))
        c.say("📐 Магазин стал больше: −$price монет. Теперь помещается ещё $EXPANSION_CAPACITY покупателей в день, но аренда выросла на $EXPANSION_RENT.")
        c.log("расширил магазин (−$price)")
        return c.done()
    }

    /** Реклама: несколько дней покупателей больше — но она помогает, только пока в магазине есть свободное место. */
    fun advertise(state: GameState): Outcome {
        val b = state.business?.takeIf { it.isOpen } ?: return GameEngine.fail(state, "Магазин ещё не открыт.")
        if (b.adDays > 0) return GameEngine.fail(state, "Реклама уже идёт: ещё ${b.adDays} дн.")
        if (state.coins < AD_PRICE) return GameEngine.fail(state, "Не хватает монет: нужно $AD_PRICE, а есть ${state.coins}.")
        val c = GameEngine.Ctx(state)
        c.spend(AD_PRICE, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(adDays = AD_DAYS))
        c.say("📣 Реклама запущена на $AD_DAYS дня: −$AD_PRICE монет. Покупателей станет больше на $AD_BOOST_PERCENT%.")
        c.log("реклама (−$AD_PRICE)")
        return c.done()
    }

    /** Починить кассу. */
    fun repair(state: GameState): Outcome {
        val b = state.business?.takeIf { it.isOpen && it.broken } ?: return GameEngine.fail(state, "Чинить нечего.")
        if (state.coins < REPAIR_PRICE) return GameEngine.fail(state, "Не хватает монет: нужно $REPAIR_PRICE, а есть ${state.coins}.")
        val c = GameEngine.Ctx(state)
        c.spend(REPAIR_PRICE, SpendKind.OTHER, "свой магазин")
        put(c, b.copy(broken = false))
        c.say("🔧 Касса починена: −$REPAIR_PRICE монет. Магазин снова работает в полную силу.")
        c.log("починил кассу (−$REPAIR_PRICE)")
        return c.done()
    }

    /**
     * Забрать выручку из кассы. Сначала гасится долг по аренде, потом удерживается налог ${TAX_PERCENT}%,
     * остальное становится деньгами Монтика.
     */
    fun collect(state: GameState): Outcome {
        val b = state.business?.takeIf { it.isOpen } ?: return GameEngine.fail(state, "Магазин ещё не открыт.")
        if (b.till <= 0) return GameEngine.fail(state, "В кассе пока пусто.")
        val c = GameEngine.Ctx(state)
        var money = b.till
        var overdue = b.overdue
        val paidDebt = minOf(money, overdue)
        money -= paidDebt
        overdue -= paidDebt
        val tax = tax(money)
        val net = money - tax
        if (net > 0) c.earn(net)
        c.s = c.s.copy(totalTax = c.s.totalTax + tax)
        put(c, b.copy(till = 0, overdue = overdue))
        if (paidDebt > 0) c.say("🧾 Из выручки погашен долг по аренде: $paidDebt монет.")
        c.say("💰 Из кассы взято ${b.till} монет: налог $TAX_PERCENT% — $tax, на руки — $net.")
        c.log("забрал выручку ${b.till}: налог $tax, на руки $net")
        c.teach(Lesson.PROFIT)
        c.teach(Lesson.TAX)
        return c.done()
    }

    // ───────────────────────── Конец дня ─────────────────────────

    /**
     * Торговый день: покупатели заходят, товары уходят, выручка падает в кассу, списываются аренда и содержание.
     * Вызывается ядром при наступлении нового дня.
     */
    internal fun settleDay(c: GameEngine.Ctx) {
        val b = c.s.business?.takeIf { it.isOpen } ?: return
        val type = b.typeInfo ?: return

        // Событие дня: праздник или поломка кассы.
        val rnd = Random(c.s.seed * 31_337L + c.s.day)
        val roll = rnd.nextInt(100)
        var event: String? = null
        var boost = 100
        var broken = b.broken
        if (!broken && roll < BREAKDOWN_CHANCE) {
            broken = true
            event = "Сломалась касса — магазин работает вполсилы, пока её не починят."
        } else if (roll in BREAKDOWN_CHANCE until BREAKDOWN_CHANCE + HOLIDAY_CHANCE) {
            boost = 100 + HOLIDAY_BOOST_PERCENT
            event = "В городе праздник — людей на улицах больше обычного."
        }
        val today = b.copy(broken = broken)

        val demand = demand(today) * boost / 100
        val room = capacity(today)
        val canSell = minOf(demand, room)
        val served = minOf(canSell, today.stock)
        val revenue = served * type.basket
        val goodsCost = served * type.unitCost
        val rent = dailyRent(today)
        val upkeep = dailyUpkeep(today)
        val expenses = rent + upkeep

        // Расходы платятся из кассы, потом из кошелька; на остальное копится долг.
        var till = today.till + revenue
        val fromTill = minOf(till, expenses)
        till -= fromTill
        var rest = expenses - fromTill
        val fromWallet = minOf(c.s.coins, rest)
        if (fromWallet > 0) c.spend(fromWallet, SpendKind.OTHER, "свой магазин")
        rest -= fromWallet

        val report = BizReport(c.s.day, demand, served, revenue, goodsCost, rent, upkeep, event)
        val next = today.copy(
            stock = today.stock - served,
            till = till,
            overdue = today.overdue + rest,
            daysOpen = today.daysOpen + 1,
            totalRevenue = today.totalRevenue + revenue,
            totalProfit = today.totalProfit + report.profit,
            adDays = maxOf(0, today.adDays - 1),
            report = report
        )
        put(c, next)

        c.say("🏬 Магазин за день: покупателей $served, выручка $revenue, прибыль ${report.profit}.")
        if (event != null) c.say("📰 $event")
        if (report.lost > 0 && today.stock <= served) {
            c.say("📦 Товары закончились: ${report.lost} покупателей ушли без покупки. Закупай товары заранее!")
            c.teach(Lesson.STOCK)
        } else if (report.lost > 0 && demand > room) {
            c.say("🚪 В магазине не хватило места: ${report.lost} покупателей не смогли зайти. Помогут оборудование получше или расширение.")
        }
        if (rest > 0) c.say("🧾 На аренду и содержание не хватило $rest монет — это долг магазина. Забери выручку или пополни кошелёк.")
        c.log("магазин: выручка $revenue, прибыль ${report.profit}")
        c.teach(Lesson.PROFIT)
    }
}
