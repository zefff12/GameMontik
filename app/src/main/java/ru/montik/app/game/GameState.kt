package ru.montik.app.game

import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

/** Итог прошедшего дня для «Финансового дневника Монтика». */
data class DayDiary(val day: Int, val earned: Int, val spent: Int, val saved: Int)

/** Текущее путешествие: сколько остановок уже пройдено. */
data class TripProgress(val destinationId: String, val stopsDone: Int)

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
    /** Раскраска героя: часть тела → цвет 0xRRGGBB. Пустая карта — цвета по умолчанию. */
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
    val log: List<String> = emptyList()
) {
    val introDone: Boolean get() = Scenarios.INTRO in doneScenarios

    fun skillPoints(skill: Skill): Int = skills[skill] ?: 0
    fun skillLevel(skill: Skill): Int = skillPoints(skill) / Skill.POINTS_PER_LEVEL

    /** Прибавка к заработку за опрятный вид (в процентах). */
    val appearancePercent: Int
        get() = worn.values.sumOf { Catalog.clothing(it)?.tier?.appearancePercent ?: 0 }

    val wornCount: Int get() = worn.size

    /** Цвет части героя: свой, если ребёнок раскрасил, иначе цвет по умолчанию. */
    fun heroColor(part: HeroPart): Int = heroColors[part.id] ?: part.default
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
            val logCount = int("logCount", 0).coerceIn(0, Rules.MAX_LOG)
            val log = (0 until logCount).mapNotNull { p.getProperty("log.$it") }

            d.copy(
                seed = p.getProperty("seed")?.toLongOrNull() ?: d.seed,
                created = p.getProperty("created") == "true",
                heroName = p.getProperty("heroName")?.takeIf { it.isNotBlank() } ?: Hero.DEFAULT_NAME,
                heroColors = pairs("heroColors").mapNotNull { (k, v) ->
                    val color = v.toIntOrNull()
                    if (HeroPart.byId(k) == null || color == null) null else k to (color and 0xFFFFFF)
                }.toMap(),
                storySeen = p.getProperty("storySeen") == "true",
                day = int("day", 1).coerceAtLeast(1),
                coins = int("coins", d.coins).coerceAtLeast(0),
                cushion = int("cushion", 0).coerceAtLeast(0),
                deposit = int("deposit", 0).coerceAtLeast(0),
                debt = int("debt", 0).coerceAtLeast(0),
                energy = pct("energy", 100),
                food = pct("food", 100),
                water = pct("water", 100),
                shifts = int("shifts", 0).coerceAtLeast(0),
                tasksCorrect = int("tasksCorrect", 0).coerceAtLeast(0),
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
                log = log
            )
        } catch (e: Exception) {
            null
        }
    }
}
