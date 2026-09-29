package ru.montik.app.game

/*
 * То, что делает игру живее, — без новых правил экономики:
 *   Progress — опыт и уровень Монтика (шкала «Имя / Уровень …» из макета);
 *   Talk     — что Монтик говорит, когда на него нажимают;
 *   Advice   — «Совет от Лобачевского» (карточка из макета), один раз в игровой день;
 *   Praise   — похвала от взрослого из раздела для родителей;
 *   Keepsakes — магнитики из поездок и вещи, купленные на накопленное.
 * Всё — чистый Kotlin, экраны — в ui/.
 */

// ───────────────────────── Опыт и уровень ─────────────────────────

object Progress {
    /** Сколько опыта нужно, чтобы с уровня [level] перейти на следующий: 60, 90, 120, … */
    fun toNext(level: Int): Int = 60 + 30 * (level - 1)

    /** С какого опыта начинается уровень [level] (уровень 1 — с нуля). */
    fun levelStart(level: Int): Int {
        var total = 0
        for (l in 1 until level) total += toNext(l)
        return total
    }

    fun levelOf(xp: Int): Int {
        var level = 1
        var rest = xp.coerceAtLeast(0)
        while (rest >= toNext(level)) {
            rest -= toNext(level)
            level++
        }
        return level
    }

    /** Сколько опыта набрано внутри текущего уровня и сколько в нём всего. */
    fun inLevel(xp: Int): Pair<Int, Int> {
        val level = levelOf(xp)
        return (xp - levelStart(level)) to toNext(level)
    }

    /** Доля шкалы уровня 0..1. */
    fun fraction(xp: Int): Float {
        val (have, need) = inLevel(xp)
        return have.toFloat() / need
    }

    // Сколько опыта за что даётся.
    const val XP_SHIFT = 15
    const val XP_SHOP_STAR = 5
    const val XP_TASK_GREAT = 30
    const val XP_TASK_OK = 20
    const val XP_TASK_BAD = 10
    const val XP_SAVE_DAY = 5
    const val XP_PERIOD_STAR = 15
    const val XP_GOAL = 50
    const val XP_AD_DECLINED = 10
    const val XP_GOOD_CHOICE = 10
    const val XP_MOVE_UP = 40

    fun taskXp(rating: Rating): Int = when (rating) {
        Rating.GREAT -> XP_TASK_GREAT
        Rating.OK -> XP_TASK_OK
        Rating.BAD -> XP_TASK_BAD
    }

    /** Новый уровень ждёт поздравления (его ещё не показывали). */
    fun levelUpPending(state: GameState): Int? =
        levelOf(state.xp).takeIf { it > state.levelSeen }

    fun markLevelSeen(state: GameState): GameState = state.copy(levelSeen = levelOf(state.xp))
}

// ───────────────────────── Реплики Монтика ─────────────────────────

object Talk {
    /**
     * Что Монтик может сказать прямо сейчас — по важности: сначала то, что требует внимания
     * (голод, силы, квартира), потом — про цель, план и просто хорошее настроение.
     */
    fun phrases(state: GameState): List<String> {
        val out = mutableListOf<String>()
        if (state.food < 30) out += "Ой… Кажется, я проголодался…"
        if (state.water < 30) out += "Так пить хочется! Купим воды?"
        if (state.energy < 30) out += "Я так устал… Может, поспим?"
        if (state.rentDebt > 0) out += "Мы должны за квартиру ${state.rentDebt}. Давай сначала отдадим долг."
        else if (Life.rentCountdown(state)) {
            val left = Life.daysUntilRent(state)
            out += if (left == 0) "Сегодня платим за квартиру — ${state.home.rent} монет. Хватит?"
            else "До оплаты квартиры ${daysWord(left)}. Нам нужно ${state.home.rent} монет."
        }
        if (Life.planMissing(state)) out += "Давай составим план на период? С планом спокойнее!"
        val goal = Goals.current(state)
        if (goal != null) {
            val left = Goals.remaining(state)
            out += if (left == 0) "В копилке хватает на «${goal.title}»! Ура!"
            else "Ещё $left монет — и ${goalWish(goal)}!"
        } else if (state.coins >= 50) {
            out += "У нас есть монеты. Может, выберем цель в копилке?"
        }
        if (state.mood < 35) out += "Мне скучновато… Может, небольшая радость из плана?"
        if (state.mood >= 75) out += "Мне сегодня так хорошо! Спасибо, что заботишься обо мне!"
        if (state.energy >= 60 && state.food >= 40) out += "Я полон сил! Пойдём работать?"
        out += "Мечтай, планируй, действуй!"
        return out.distinct()
    }

    private fun goalWish(goal: SavingsGoal): String =
        if (goal.tripId != null) "едем: «${goal.title}»" else "у нас будет «${goal.title}»"

    fun daysWord(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        val word = when {
            m10 == 1 && m100 != 11 -> "день"
            m10 in 2..4 && m100 !in 12..14 -> "дня"
            else -> "дней"
        }
        return "$n $word"
    }
}

// ───────────────────────── Совет от Лобачевского ─────────────────────────

data class Tip(val id: String, val text: String)

object Advice {
    val all: List<Tip> = listOf(
        Tip("plan", "Сначала составь план: сколько на нужное, сколько на желаемое и сколько отложить. Так деньги не «разбегутся»."),
        Tip("rent", "Платёж за квартиру — обязательный. Отложи на него заранее, тогда день оплаты не станет неприятным сюрпризом."),
        Tip("ads", "Реклама говорит «выгода!», но выгода — продавцу. Хорошая покупка — та, что была в твоём плане."),
        Tip("goal", "Большая мечта становится ближе, если откладывать понемногу, но регулярно. Выбери цель в копилке!"),
        Tip("cushion", "Подушка безопасности — это запас на неожиданности: сломался рюкзак, потерялся ключ. С ней не страшно."),
        Tip("needs", "Не спеши тратить всё сразу. Сначала обеспечь нужное: еду, воду и жильё — а потом уже желаемое."),
        Tip("compare", "Прежде чем купить, сравни: нужна ли вещь, сколько она стоит и не найдётся ли дешевле."),
        Tip("work", "Монеты не появляются сами: их зарабатывают трудом и временем. Поэтому тратить их стоит с умом."),
        Tip("wait", "Хочешь что-то купить? Подожди один день. Если желание осталось — значит, вещь правда нужна.")
    )

    fun byId(id: String): Tip? = all.firstOrNull { it.id == id }

    /** Совет на сегодня: сначала — к ситуации, иначе — по очереди по дням. */
    fun forToday(state: GameState): Tip {
        val topical = when {
            state.rentDebt > 0 || Life.rentCountdown(state) -> "rent"
            Life.planMissing(state) && state.day > 1 -> "plan"
            state.pendingAd != null || state.adsBought > state.adsDeclined -> "ads"
            state.goalId == null && state.coins >= 60 -> "goal"
            state.cushion == 0 && state.day >= 4 -> "cushion"
            else -> null
        }
        val tip = topical?.let { id -> byId(id)?.takeIf { it.id != state.lastTipId } }
        return tip ?: all[(state.day - 1).mod(all.size)].let { if (it.id == state.lastTipId) all[state.day.mod(all.size)] else it }
    }

    /** Показывать ли совет сегодня: один раз в игровой день, после вступления и знакомства. */
    fun due(state: GameState): Boolean =
        state.introDone && state.helpSeen && state.tipDay < state.day && state.sleep == null

    fun markSeen(state: GameState, tip: Tip): GameState = state.copy(tipDay = state.day, lastTipId = tip.id)
}

// ───────────────────────── Похвала от взрослого ─────────────────────────

object Praise {
    const val MAX_LENGTH = 120

    /** Готовые слова для взрослого: нажал — и отправил. */
    val quick = listOf(
        "Горжусь тобой!",
        "Молодец, что копишь!",
        "Отличный план!",
        "Ты здорово справляешься с деньгами!",
        "Спасибо, что не поддался рекламе!"
    )

    /** Взрослый оставляет сообщение: оно покажется ребёнку при следующем входе на главный экран. */
    fun send(state: GameState, text: String): GameState {
        val clean = text.trim().take(MAX_LENGTH)
        if (clean.isEmpty()) return state
        return state.copy(parentNote = clean, parentNoteNew = true, praises = state.praises + 1)
    }

    /** Ребёнок прочитал: сообщение остаётся в дневнике, настроение Монтика поднимается. */
    fun read(state: GameState): Outcome {
        if (!state.parentNoteNew) return Outcome(state)
        val c = GameEngine.Ctx(state.copy(parentNoteNew = false))
        c.mood(10, "Взрослый похвалил Монтика — это очень приятно!")
        c.log("похвала от взрослого: «${state.parentNote}»")
        return c.done()
    }
}

// ───────────────────────── Коллекция ─────────────────────────

/** Магнитик из поездки: собран, когда путешествие в город завершено. */
data class Magnet(val destinationId: String, val city: String, val emoji: String, val collected: Boolean)

object Keepsakes {
    fun magnets(state: GameState): List<Magnet> = Destinations.all.map {
        Magnet(it.id, it.name, it.emoji, it.id in state.visitedCities || it.id in state.completedTrips)
    }

    /** Вещи, купленные на накопленное (велосипед, приставка): они появляются в комнате. */
    fun things(state: GameState): List<SavingsGoal> =
        Goals.all.filter { it.tripId == null && it.id in state.goalsDone }
}
