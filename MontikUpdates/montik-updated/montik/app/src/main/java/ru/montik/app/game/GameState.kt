package ru.montik.app.game

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Итог прошедшего дня для «Финансового дневника Монтика». */
data class DayDiary(val day: Int, val earned: Int, val spent: Int, val saved: Int)

/** Текущее путешествие: сколько остановок уже пройдено. */
data class TripProgress(val destinationId: String, val stopsDone: Int)

/** Активный кредит: сумма, цель, сроки платежей. */
data class Credit(
    val id: String,
    val amount: Int,
    val purpose: String,  // "food", "housing", "clothes", "entertainment", "skills"
    val dayTaken: Int,
    val daysRemaining: Int,
    val monthlyPayment: Int,
    val interestRate: Float
)

/** Завершённый кредит: история и сумма выплат. */
data class CreditRecord(
    val amount: Int,
    val purpose: String,
    val dayCompleted: Int,
    val totalPaid: Int
)

/**
 * Полное состояние игры. Неизменяемое: любое действие возвращает новое состояние.
 * Хранится локально на устройстве, интернет не нужен.
 */
data class GameState(
    val seed: Long = 1L,
    /** Нарисован ли Монтик (пройден ли первый экран). */
    val created: Boolean = false,
    /** Имя героя. По умолчанию Монтик — его можно оставить как есть. */
    val heroName: String = Hero.DEFAULT_NAME,
    /** Заготовка героя (id из [HeroPreset]): с неё начинается раскраска. По умолчанию — белый человечек. */
    val heroPreset: String = HeroPreset.DEFAULT.id,
    /** Раскраска героя поверх заготовки: часть тела → цвет 0xRRGGBB. Пустая карта — цвета заготовки. */
    val heroColors: Map<String, Int> = emptyMap(),
    /** Пройдено ли вступление с историей Монтика. */
    val storySeen: Boolean = false,
    val day: Int = 1,
    val coins: Int = Rules.START_COINS,
    /** Сумма в «подушке безопасности». */
    val cushion: Int = 0,
    /** Деньги на вкладе в банке: они растут, но не выручат прямо сегодня. */
    val deposit: Int = 0,
    /** Сколько монет нужно вернуть банку по кредиту (0 — долга нет). */
    val debt: Int = 0,
    val energy: Int = 100,
    val food: Int = 100,
    val water: Int = 100,
    val shifts: Int = 0,
    /** Сколько заданий на смене решено верно. */
    val tasksCorrect: Int = 0,
    /** Сколько смен отработано в магазине: от этого зависит должность и ставка. */
    val shopShifts: Int = 0,
    /** Свой магазин Монтика (null — ещё нет). */
    val business: Business? = null,
    val totalEarned: Int = 0,
    val totalTax: Int = 0,
    val totalSpent: Int = 0,
    val dayEarned: Int = 0,
    val daySpent: Int = 0,
    val daySaved: Int = 0,
    val diary: DayDiary? = null,
    val skills: Map<Skill, Int> = emptyMap(),
    /** Купленная одежда: id → сколько ночей осталось. */
    val owned: Map<String, Int> = emptyMap(),
    /** Надетое: слот → id вещи. */
    val worn: Map<Slot, String> = emptyMap(),
    val seenLessons: Set<String> = emptySet(),
    val doneScenarios: Set<String> = emptySet(),
    val trip: TripProgress? = null,
    val completedTrips: Set<String> = emptySet(),
    /** Событие, которое ждёт выбора игрока (например, неожиданная поломка или потеря). */
    val pendingEvent: String? = null,
    val lastEventDay: Int = 0,
    val prepaidNight: Boolean = false,
    /** Достигнутые «вехи» (например, подушка безопасности накоплена). */
    val milestones: Set<String> = emptySet(),
    val log: List<String> = emptyList(),
    /**
     * Виртуальные часы Монтика: [clockMinutes] — виртуальная минута (от 00:00 первого дня)
     * в тот настоящий миг [clockStamp] (мс). Пока [clockStamp] = 0, часы стоят.
     */
    val clockMinutes: Long = Rules.START_CLOCK_MINUTES.toLong(),
    val clockStamp: Long = 0L,
    /** Идущий сон (null — Монтик бодрствует). */
    val sleep: SleepSession? = null,
    /** Как Монтик выспался в последний раз: влияет на то, как быстро он устаёт на смене. */
    val sleepQuality: SleepQuality = SleepQuality.NORMAL,

    // ── Жильё и аренда (см. Life.kt) ──
    /** Уровень жилья = уровень Монтика: 1 — скромный достаток, 2 — средний, 3 — богатый. */
    val housing: Int = 1,
    /** День, когда нужно заплатить за квартиру (15-е и 30-е число игрового месяца). */
    val rentDueDay: Int = Rules.RENT_PERIOD_DAYS,
    /** Ближайший платёж уже внесён заранее. */
    val rentPaidAhead: Boolean = false,
    /** Долг за квартиру: копится, только если в срок не хватило денег (Монтика никто не выселяет). */
    val rentDebt: Int = 0,

    // ── Настроение ──
    val mood: Int = Rules.START_MOOD,
    /** Почему настроение последний раз изменилось — короткое объяснение для ребёнка. */
    val moodWhy: String = "",

    // ── Копилка на цель (см. Savings.kt) ──
    val goalId: String? = null,
    val piggy: Int = 0,
    /** Сколько всего положено в копилку и с какого дня — для расчёта срока до цели. */
    val piggyDeposited: Int = 0,
    val piggyFirstDay: Int = 0,
    /** Цели, которые уже достигнуты (например, велосипед куплен). */
    val goalsDone: Set<String> = emptySet(),

    // ── Бюджет игрового периода (см. Life.kt) ──
    val plan: BudgetPlan? = null,
    /** Факт текущего периода: обязательные и желаемые траты, отложено, заработано, голодные ночи. */
    val pNeeds: Int = 0,
    val pWants: Int = 0,
    val pSaved: Int = 0,
    val pEarned: Int = 0,
    val pHungry: Int = 0,
    /** Покупки текущего периода — история для экрана бюджета. */
    val purchases: List<String> = emptyList(),
    /** Итоги прошедших периодов (последние несколько), новые — в конце. */
    val periods: List<PeriodResult> = emptyList(),
    /** Итоги какого периода ребёнок уже посмотрел. */
    val periodSeen: Int = 0,
    /** Звёзды привычек за всё время: от них зависит переезд на новый уровень. */
    val stars: Int = 0,

    // ── Задания, реклама, радости ──
    /** Лучшая оценка по каждому пройденному заданию (id → оценка). */
    val taskResults: Map<String, Rating> = emptyMap(),
    /** Реклама, которая ждёт ответа ребёнка (id из [Ads]). */
    val pendingAd: String? = null,
    val lastAdDay: Int = 0,
    val adsShown: Int = 0,
    val adsBought: Int = 0,
    val adsDeclined: Int = 0,
    /** Купленные радости и вещи из рекламы: id → сколько раз. */
    val joys: Map<String, Int> = emptyMap(),

    /** Показано ли знакомство «три решения». */
    val helpSeen: Boolean = false,
    /** Демонстрационный режим для экспертов: периоды без ожидания, все задания открыты. */
    val demo: Boolean = false,

    // ── Опыт, советы, похвала, рекорды (см. Progress.kt) ──
    /** Опыт Монтика: от него зависит уровень на шкале «Имя / Уровень». */
    val xp: Int = 0,
    /** До какого уровня поздравление уже показано. */
    val levelSeen: Int = 1,
    /** В какой день опыт за накопления уже начислен (раз в день). */
    val xpSaveDay: Int = 0,
    /** В какой день уже был «Совет от Лобачевского» и какой именно. */
    val tipDay: Int = 0,
    val lastTipId: String = "",
    /** Сообщение от взрослого и прочитано ли оно. */
    val parentNote: String = "",
    val parentNoteNew: Boolean = false,
    val praises: Int = 0,
    /** Лучшая оценка смены в магазине по каждой мини-игре (id → 0..100). */
    val shopBest: Map<String, Int> = emptyMap(),
    /** Звуки в игре. */
    val soundOn: Boolean = true,

    // ── Магазин продуктов, банк, приставка ──
    /** Холодильник: id продукта (см. [Grocery]) → сколько штук. */
    val fridge: Map<String, Int> = emptyMap(),
    /** Сколько смен отработано в банке (проверка купюр). */
    val bankShifts: Int = 0,
    /** В какой день Монтик играл на приставке и сколько раз за этот день. */
    val consoleDay: Int = 0,
    val consolePlays: Int = 0,

    // ── Система скинов ──
    /** ID купленного платного скина (null = используется стандартный). */
    val heroPaidSkin: String? = null,
    /** Купленные скины. */
    val ownedSkins: Set<String> = emptySet(),
    /** Свободная раскраска: часть героя (HeroPart.id) → RGB цвет. */
    val skinColors: Map<String, Int> = emptyMap(),

    // ── Кредитная система ──
    /** Активные кредиты. */
    val credits: List<Credit> = emptyList(),
    /** История завершённых кредитов. */
    val creditHistory: List<CreditRecord> = emptyList(),
    /** Всего заимствовано. */
    val totalCreditUsed: Int = 0,
    /** Всего выплачено по кредитам. */
    val totalCreditPaid: Int = 0,

    // ── Игра про бартер ──
    /** День, когда последний раз енот был на рынке. */
    val barterDay: Int = 0,
    /** Сколько раз прошла игра про бартер. */
    val barterSessions: Int = 0,
    /** Понимание механики бартера (0-100). */
    val barterUnderstanding: Int = 0,

    // ── Исправление времени ──
    /** Системное время (мс) когда игра была создана. */
    val gameStartMs: Long = 0L
) {
    val introDone: Boolean get() = Scenarios.INTRO in doneScenarios

    fun skillPoints(skill: Skill): Int = skills[skill] ?: 0
    fun skillLevel(skill: Skill): Int = skillPoints(skill) / Skill.POINTS_PER_LEVEL

    /** Прибавка к заработку за опрятный вид (в процентах). */
    val appearancePercent: Int
        get() = worn.values.sumOf { Catalog.clothing(it)?.tier?.appearancePercent ?: 0 }

    val wornCount: Int get() = worn.size

    /** Уровень жилья (он же уровень Монтика). */
    val home: Housing get() = Housing.byLevel(housing)

    /** Номер текущего игрового периода (по [Rules.PERIOD_DAYS] дней). */
    val period: Int get() = Life.periodOf(day)

    /** Все накопления: копилка на цель и подушка безопасности. */
    val savings: Int get() = piggy + cushion

    /** Выбранная заготовка героя. */
    val preset: HeroPreset get() = HeroPreset.byId(heroPreset) ?: HeroPreset.DEFAULT

    /** Цвет части героя: свой, если ребёнок раскрасил, иначе цвет заготовки. */
    fun heroColor(part: HeroPart): Int = heroColors[part.id] ?: preset.color(part)
}

/** Части героя, которые ребёнок раскрашивает на экране создания (по макету). */
enum class HeroPart(val id: String, val title: String, val default: Int) {
    FUR("fur", "Шёрстка", 0x86D4FF),
    BELLY("belly", "Живот", 0xFFF6EA),
    EARS("ears", "Ушки", 0xFFA9B8),
    PACK("pack", "Рюкзак", 0x3F4A2E),
    CROWN("crown", "Корона", 0xFFC928);

    companion object {
        fun byId(id: String): HeroPart? = values().firstOrNull { it.id == id }
    }
}

/**
 * Заготовки Монтика. Игра начинается с белого человечка — его ребёнок раскрашивает сам;
 * остальные заготовки — готовые Монтики из макета в разных цветах (синий и зелёный — как в дизайне).
 */
enum class HeroPreset(
    val id: String,
    val title: String,
    private val fur: Int,
    private val belly: Int,
    private val ears: Int,
    private val pack: Int,
    private val crown: Int
) {
    WHITE("white", "Белый", 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF),
    BLUE("blue", "Синий", 0x86D4FF, 0xFFF6EA, 0xFFA9B8, 0x3F4A2E, 0xFFC928),
    GREEN("green", "Зелёный", 0x8FE06A, 0xFFF6EA, 0xFFA9B8, 0x3F4A2E, 0xFFC928),
    PINK("pink", "Розовый", 0xFF9EC8, 0xFFF6EA, 0xFFD0E0, 0x5A3F4A, 0xFFC928),
    PURPLE("purple", "Фиолетовый", 0xB98CFF, 0xFFF6EA, 0xFFA9B8, 0x3F3A5A, 0xFFC928),
    ORANGE("orange", "Рыжий", 0xFFB45A, 0xFFF6EA, 0xFFA9B8, 0x3F4A2E, 0xFFC928),
    /** Новые Монтики из макета экрана раскраски: чёрный в наушниках и радужный. */
    BLACK("black", "Чёрный", 0x2E2E33, 0x55555C, 0x6FD24A, 0x3FBF46, 0xFFC928),
    RAINBOW("rainbow", "Радужный", 0xFFB8E4, 0xFFF3A6, 0xA6E3FF, 0xB98CFF, 0xFFC928);

    /** Белый человечек — чистый контур без цвета: красить его можно с нуля. */
    val isBlank: Boolean get() = this == WHITE

    fun color(part: HeroPart): Int = when (part) {
        HeroPart.FUR -> fur
        HeroPart.BELLY -> belly
        HeroPart.EARS -> ears
        HeroPart.PACK -> pack
        HeroPart.CROWN -> crown
    }

    companion object {
        val DEFAULT = WHITE

        /** Заготовки в ряду «Кто твой Монтик?» — в порядке макета. */
        val PICKER: List<HeroPreset> = listOf(WHITE, BLUE, PINK, BLACK, RAINBOW, PURPLE)

        fun byId(id: String?): HeroPreset? = values().firstOrNull { it.id == id }
    }
}

object Hero {
    const val DEFAULT_NAME = "Монтик"
    const val MAX_NAME = 12

    /** Палитра из макета: семь кружков справа от героя. */
    val PALETTE = listOf(
        0xE8E82E, // жёлтый
        0x28E0E0, // бирюзовый
        0x2BA8F0, // голубой
        0x1616D8, // синий
        0x8B2BE0, // фиолетовый
        0xE01B1B, // красный
        0x111111  // чёрный
    )

    /**
     * Цвета для рисования Монтика: семь цветов из макета и ещё несколько, которых там нет
     * (зелёный, оранжевый, розовый, коричневый, серый), чтобы можно было нарисовать любого героя.
     */
    val DRAW_PALETTE = PALETTE + listOf(
        0x3FBF46, // зелёный
        0xFF8A3D, // оранжевый
        0xFF7EC0, // розовый
        0x8B5A2B, // коричневый
        0x9AA5AD  // серый
    )

    /** Толщина кисти в долях стороны холста: тонкая, средняя, толстая. */
    val BRUSH_SIZES = listOf(0.010f, 0.022f, 0.045f)

    /** Приводит введённое имя к виду, который можно показывать. */
    fun cleanName(raw: String): String {
        val name = raw.trim().take(MAX_NAME)
        return if (name.isEmpty()) DEFAULT_NAME else name
    }
}

/** Сохранение/загрузка состояния в текстовом виде (java.util.Properties). */
object StateCodec {
    private const val VERSION = 1

    fun encode(s: GameState): String {
        val p = Properties()
        p["version"] = VERSION.toString()
        p["seed"] = s.seed.toString()
        p["created"] = s.created.toString()
        p["heroName"] = s.heroName
        p["heroPreset"] = s.heroPreset
        p["heroColors"] = s.heroColors.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["storySeen"] = s.storySeen.toString()
        p["day"] = s.day.toString()
        p["coins"] = s.coins.toString()
        p["cushion"] = s.cushion.toString()
        p["deposit"] = s.deposit.toString()
        p["debt"] = s.debt.toString()
        p["energy"] = s.energy.toString()
        p["food"] = s.food.toString()
        p["water"] = s.water.toString()
        p["shifts"] = s.shifts.toString()
        p["tasksCorrect"] = s.tasksCorrect.toString()
        p["shopShifts"] = s.shopShifts.toString()
        s.business?.let { p["business"] = Business.encode(it) }
        p["totalEarned"] = s.totalEarned.toString()
        p["totalTax"] = s.totalTax.toString()
        p["totalSpent"] = s.totalSpent.toString()
        p["dayEarned"] = s.dayEarned.toString()
        p["daySpent"] = s.daySpent.toString()
        p["daySaved"] = s.daySaved.toString()
        s.diary?.let { p["diary"] = "${it.day},${it.earned},${it.spent},${it.saved}" }
        p["skills"] = s.skills.entries.joinToString(";") { "${it.key.name}=${it.value}" }
        p["owned"] = s.owned.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["worn"] = s.worn.entries.joinToString(";") { "${it.key.name}=${it.value}" }
        p["seenLessons"] = s.seenLessons.joinToString(",")
        p["doneScenarios"] = s.doneScenarios.joinToString(",")
        s.trip?.let { p["trip"] = "${it.destinationId}:${it.stopsDone}" }
        p["completedTrips"] = s.completedTrips.joinToString(",")
        s.pendingEvent?.let { p["pendingEvent"] = it }
        p["lastEventDay"] = s.lastEventDay.toString()
        p["prepaidNight"] = s.prepaidNight.toString()
        p["milestones"] = s.milestones.joinToString(",")
        p["clockMinutes"] = s.clockMinutes.toString()
        p["clockStamp"] = s.clockStamp.toString()
        p["sleepQuality"] = s.sleepQuality.name
        s.sleep?.let { p["sleep"] = "${it.startV},${it.alarmV},${it.place.name},${it.snoozes}" }
        p["housing"] = s.housing.toString()
        p["rentDueDay"] = s.rentDueDay.toString()
        p["rentPaidAhead"] = s.rentPaidAhead.toString()
        p["rentDebt"] = s.rentDebt.toString()
        p["mood"] = s.mood.toString()
        p["moodWhy"] = s.moodWhy
        s.goalId?.let { p["goalId"] = it }
        p["piggy"] = s.piggy.toString()
        p["piggyDeposited"] = s.piggyDeposited.toString()
        p["piggyFirstDay"] = s.piggyFirstDay.toString()
        p["goalsDone"] = s.goalsDone.joinToString(",")
        s.plan?.let { p["plan"] = BudgetPlan.encode(it) }
        p["pNeeds"] = s.pNeeds.toString()
        p["pWants"] = s.pWants.toString()
        p["pSaved"] = s.pSaved.toString()
        p["pEarned"] = s.pEarned.toString()
        p["pHungry"] = s.pHungry.toString()
        p["purchaseCount"] = s.purchases.size.toString()
        s.purchases.forEachIndexed { i, line -> p["purchase.$i"] = line }
        p["periods"] = s.periods.joinToString(";") { PeriodResult.encode(it) }
        p["periodSeen"] = s.periodSeen.toString()
        p["stars"] = s.stars.toString()
        p["taskResults"] = s.taskResults.entries.joinToString(";") { "${it.key}=${it.value.name}" }
        s.pendingAd?.let { p["pendingAd"] = it }
        p["lastAdDay"] = s.lastAdDay.toString()
        p["adsShown"] = s.adsShown.toString()
        p["adsBought"] = s.adsBought.toString()
        p["adsDeclined"] = s.adsDeclined.toString()
        p["joys"] = s.joys.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["helpSeen"] = s.helpSeen.toString()
        p["demo"] = s.demo.toString()
        p["xp"] = s.xp.toString()
        p["levelSeen"] = s.levelSeen.toString()
        p["xpSaveDay"] = s.xpSaveDay.toString()
        p["tipDay"] = s.tipDay.toString()
        p["lastTipId"] = s.lastTipId
        p["parentNote"] = s.parentNote
        p["parentNoteNew"] = s.parentNoteNew.toString()
        p["praises"] = s.praises.toString()
        p["shopBest"] = s.shopBest.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["soundOn"] = s.soundOn.toString()
        p["fridge"] = s.fridge.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["bankShifts"] = s.bankShifts.toString()
        p["consoleDay"] = s.consoleDay.toString()
        p["consolePlays"] = s.consolePlays.toString()
        // ── Новые поля: скины, кредиты, бартер, время ──
        s.heroPaidSkin?.let { p["heroPaidSkin"] = it }
        p["ownedSkins"] = s.ownedSkins.joinToString(",")
        p["skinColors"] = s.skinColors.entries.joinToString(";") { "${it.key}=${it.value}" }
        p["creditCount"] = s.credits.size.toString()
        s.credits.forEachIndexed { i, c -> p["credit.$i"] = "${c.id}|${c.amount}|${c.purpose}|${c.dayTaken}|${c.daysRemaining}|${c.monthlyPayment}|${c.interestRate}" }
        p["creditHistoryCount"] = s.creditHistory.size.toString()
        s.creditHistory.forEachIndexed { i, r -> p["creditRecord.$i"] = "${r.amount}|${r.purpose}|${r.dayCompleted}|${r.totalPaid}" }
        p["totalCreditUsed"] = s.totalCreditUsed.toString()
        p["totalCreditPaid"] = s.totalCreditPaid.toString()
        p["barterDay"] = s.barterDay.toString()
        p["barterSessions"] = s.barterSessions.toString()
        p["barterUnderstanding"] = s.barterUnderstanding.toString()
        p["gameStartMs"] = s.gameStartMs.toString()
        p["logCount"] = s.log.size.toString()
        s.log.forEachIndexed { i, line -> p["log.$i"] = line }
        val w = StringWriter()
        p.store(w, "Montik save")
        return w.toString()
    }

    /** Возвращает null, если текст повреждён и восстановить состояние нельзя. */
    fun decode(text: String): GameState? {
        return try {
            val p = Properties()
            p.load(StringReader(text))
            if (p.getProperty("version") == null) return null
            val d = GameState()
            fun int(key: String, def: Int) = p.getProperty(key)?.toIntOrNull() ?: def
            fun pct(key: String, def: Int) = int(key, def).coerceIn(0, 100)
            fun set(key: String): Set<String> =
                p.getProperty(key).orEmpty().split(',').filter { it.isNotBlank() }.toSet()

            fun pairs(key: String): List<Pair<String, String>> =
                p.getProperty(key).orEmpty().split(';').mapNotNull {
                    val i = it.indexOf('=')
                    if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
                }

            val skills = pairs("skills").mapNotNull { (k, v) ->
                val skill = Skill.values().firstOrNull { it.name == k }
                val pts = v.toIntOrNull()
                if (skill == null || pts == null) null else skill to pts.coerceAtLeast(0)
            }.toMap()
            val owned = pairs("owned").mapNotNull { (k, v) ->
                val days = v.toIntOrNull()
                if (Catalog.clothing(k) == null || days == null || days <= 0) null else k to days
            }.toMap()
            val worn = pairs("worn").mapNotNull { (k, v) ->
                val slot = Slot.values().firstOrNull { it.name == k }
                val item = Catalog.clothing(v)
                if (slot == null || item == null || item.slot != slot || v !in owned) null else slot to v
            }.toMap()
            val diary = p.getProperty("diary")?.split(',')?.mapNotNull { it.toIntOrNull() }?.let {
                if (it.size == 4) DayDiary(it[0], it[1], it[2], it[3]) else null
            }
            val trip = p.getProperty("trip")?.split(':')?.let {
                val dest = it.getOrNull(0)?.let { id -> Destinations.byId(id) }
                val done = it.getOrNull(1)?.toIntOrNull()
                if (dest == null || done == null) null
                else TripProgress(dest.id, done.coerceIn(0, dest.stops.size - 1))
            }
            val day = int("day", 1).coerceAtLeast(1)
            // В старых сохранениях часов нет: считаем, что каждый день начинался в 07:00.
            val clockMinutes = p.getProperty("clockMinutes")?.toLongOrNull()?.coerceAtLeast(0L)
                ?: ((day - 1) * VirtualClock.DAY.toLong() + Rules.START_CLOCK_MINUTES)
            val sleep = p.getProperty("sleep")?.split(',')?.let {
                val start = it.getOrNull(0)?.toLongOrNull()
                val alarm = it.getOrNull(1)?.toLongOrNull()
                val place = SleepPlace.values().firstOrNull { pl -> pl.name == it.getOrNull(2) }
                val snoozes = it.getOrNull(3)?.toIntOrNull() ?: 0
                if (start == null || alarm == null || place == null || alarm < start) null
                else SleepSession(start, alarm, place, snoozes.coerceAtLeast(0))
            }
            val logCount = int("logCount", 0).coerceIn(0, Rules.MAX_LOG)
            val log = (0 until logCount).mapNotNull { p.getProperty("log.$it") }

            // ── Парсим новые поля ──
            val ownedSkins = set("ownedSkins")
            val skinColors = pairs("skinColors").mapNotNull { (k, v) ->
                val color = v.toIntOrNull()
                if (color == null) null else k to (color and 0xFFFFFF)
            }.toMap()
            val credits = (0 until int("creditCount", 0).coerceIn(0, 100)).mapNotNull { i ->
                p.getProperty("credit.$i")?.split('|')?.let {
                    if (it.size < 7) null else {
                        Credit(
                            id = it[0],
                            amount = it[1].toIntOrNull() ?: 0,
                            purpose = it[2],
                            dayTaken = it[3].toIntOrNull() ?: 0,
                            daysRemaining = it[4].toIntOrNull() ?: 0,
                            monthlyPayment = it[5].toIntOrNull() ?: 0,
                            interestRate = it[6].toFloatOrNull() ?: 0.12f
                        )
                    }
                }
            }
            val creditHistory = (0 until int("creditHistoryCount", 0).coerceIn(0, 1000)).mapNotNull { i ->
                p.getProperty("creditRecord.$i")?.split('|')?.let {
                    if (it.size < 4) null else {
                        CreditRecord(
                            amount = it[0].toIntOrNull() ?: 0,
                            purpose = it[1],
                            dayCompleted = it[2].toIntOrNull() ?: 0,
                            totalPaid = it[3].toIntOrNull() ?: 0
                        )
                    }
                }
            }

            d.copy(
                seed = p.getProperty("seed")?.toLongOrNull() ?: d.seed,
                created = p.getProperty("created") == "true",
                heroName = p.getProperty("heroName")?.takeIf { it.isNotBlank() } ?: Hero.DEFAULT_NAME,
                // Старые сохранения без заготовки играли синим Монтиком из макета: он таким и остаётся.
                heroPreset = HeroPreset.byId(p.getProperty("heroPreset"))?.id
                    ?: if (p.getProperty("created") == "true") HeroPreset.BLUE.id else HeroPreset.DEFAULT.id,
                heroColors = pairs("heroColors").mapNotNull { (k, v) ->
                    val color = v.toIntOrNull()
                    if (HeroPart.byId(k) == null || color == null) null else k to (color and 0xFFFFFF)
                }.toMap(),
                storySeen = p.getProperty("storySeen") == "true",
                day = day,
                coins = int("coins", d.coins).coerceAtLeast(0),
                cushion = int("cushion", 0).coerceAtLeast(0),
                deposit = int("deposit", 0).coerceAtLeast(0),
                debt = int("debt", 0).coerceAtLeast(0),
                energy = pct("energy", 100),
                food = pct("food", 100),
                water = pct("water", 100),
                shifts = int("shifts", 0).coerceAtLeast(0),
                tasksCorrect = int("tasksCorrect", 0).coerceAtLeast(0),
                shopShifts = int("shopShifts", 0).coerceAtLeast(0),
                business = Business.decode(p.getProperty("business")),
                totalEarned = int("totalEarned", 0).coerceAtLeast(0),
                totalTax = int("totalTax", 0).coerceAtLeast(0),
                totalSpent = int("totalSpent", 0).coerceAtLeast(0),
                dayEarned = int("dayEarned", 0).coerceAtLeast(0),
                daySpent = int("daySpent", 0).coerceAtLeast(0),
                daySaved = int("daySaved", 0),
                diary = diary,
                skills = skills,
                owned = owned,
                worn = worn,
                seenLessons = set("seenLessons"),
                doneScenarios = set("doneScenarios"),
                trip = trip,
                completedTrips = set("completedTrips"),
                pendingEvent = p.getProperty("pendingEvent")?.takeIf { Scenarios.byId(it) != null },
                lastEventDay = int("lastEventDay", 0),
                prepaidNight = p.getProperty("prepaidNight") == "true",
                milestones = set("milestones"),
                log = log,
                clockMinutes = clockMinutes,
                clockStamp = p.getProperty("clockStamp")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                sleep = sleep,
                sleepQuality = SleepQuality.values().firstOrNull { it.name == p.getProperty("sleepQuality") }
                    ?: SleepQuality.NORMAL,
                housing = int("housing", 1).coerceIn(1, Housing.values().size),
                // В старых сохранениях аренды не было: первый платёж — через полмесяца от текущего дня.
                rentDueDay = p.getProperty("rentDueDay")?.toIntOrNull()?.coerceAtLeast(day)
                    ?: Life.nextRentDay(day),
                rentPaidAhead = p.getProperty("rentPaidAhead") == "true",
                rentDebt = int("rentDebt", 0).coerceAtLeast(0),
                mood = pct("mood", Rules.START_MOOD),
                moodWhy = p.getProperty("moodWhy").orEmpty(),
                goalId = p.getProperty("goalId")?.takeIf { Goals.byId(it) != null },
                piggy = int("piggy", 0).coerceAtLeast(0),
                piggyDeposited = int("piggyDeposited", 0).coerceAtLeast(0),
                piggyFirstDay = int("piggyFirstDay", 0).coerceAtLeast(0),
                goalsDone = set("goalsDone"),
                plan = BudgetPlan.decode(p.getProperty("plan")),
                pNeeds = int("pNeeds", 0).coerceAtLeast(0),
                pWants = int("pWants", 0).coerceAtLeast(0),
                pSaved = int("pSaved", 0),
                pEarned = int("pEarned", 0).coerceAtLeast(0),
                pHungry = int("pHungry", 0).coerceAtLeast(0),
                purchases = (0 until int("purchaseCount", 0).coerceIn(0, Rules.MAX_PURCHASES))
                    .mapNotNull { p.getProperty("purchase.$it") },
                periods = p.getProperty("periods").orEmpty().split(';')
                    .mapNotNull { PeriodResult.decode(it) }.takeLast(Rules.MAX_PERIODS),
                periodSeen = int("periodSeen", 0).coerceAtLeast(0),
                stars = int("stars", 0).coerceAtLeast(0),
                taskResults = pairs("taskResults").mapNotNull { (k, v) ->
                    val r = Rating.values().firstOrNull { it.name == v }
                    if (Tasks.byId(k) == null || r == null) null else k to r
                }.toMap(),
                pendingAd = p.getProperty("pendingAd")?.takeIf { Ads.byId(it) != null },
                lastAdDay = int("lastAdDay", 0),
                adsShown = int("adsShown", 0).coerceAtLeast(0),
                adsBought = int("adsBought", 0).coerceAtLeast(0),
                adsDeclined = int("adsDeclined", 0).coerceAtLeast(0),
                joys = pairs("joys").mapNotNull { (k, v) -> v.toIntOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap(),
                helpSeen = p.getProperty("helpSeen") == "true",
                demo = p.getProperty("demo") == "true",
                xp = int("xp", 0).coerceAtLeast(0),
                levelSeen = int("levelSeen", 1).coerceAtLeast(1),
                xpSaveDay = int("xpSaveDay", 0),
                tipDay = int("tipDay", 0),
                lastTipId = p.getProperty("lastTipId").orEmpty(),
                parentNote = p.getProperty("parentNote").orEmpty().take(Praise.MAX_LENGTH),
                parentNoteNew = p.getProperty("parentNoteNew") == "true",
                praises = int("praises", 0).coerceAtLeast(0),
                shopBest = pairs("shopBest").mapNotNull { (k, v) -> v.toIntOrNull()?.coerceIn(0, 100)?.let { k to it } }.toMap(),
                soundOn = p.getProperty("soundOn") != "false",
                fridge = pairs("fridge").mapNotNull { (k, v) ->
                    val n = v.toIntOrNull()
                    if (Grocery.item(k) == null || n == null || n <= 0) null else k to n
                }.toMap(),
                bankShifts = int("bankShifts", 0).coerceAtLeast(0),
                consoleDay = int("consoleDay", 0).coerceAtLeast(0),
                consolePlays = int("consolePlays", 0).coerceAtLeast(0),
                // ── Новые поля: скины, кредиты, бартер, время ──
                heroPaidSkin = p.getProperty("heroPaidSkin")?.takeIf { it in ownedSkins },
                ownedSkins = ownedSkins,
                skinColors = skinColors,
                credits = credits,
                creditHistory = creditHistory,
                totalCreditUsed = int("totalCreditUsed", 0).coerceAtLeast(0),
                totalCreditPaid = int("totalCreditPaid", 0).coerceAtLeast(0),
                barterDay = int("barterDay", 0).coerceAtLeast(0),
                barterSessions = int("barterSessions", 0).coerceAtLeast(0),
                barterUnderstanding = pct("barterUnderstanding", 0),
                gameStartMs = p.getProperty("gameStartMs")?.toLongOrNull() ?: 0L
            )
        } catch (e: Exception) {
            null
        }
    }
}
