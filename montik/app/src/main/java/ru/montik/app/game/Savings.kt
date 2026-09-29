package ru.montik.app.game

/*
 * Копилка на цель. Ребёнок выбирает цель с понятной ценой, понемногу откладывает в копилку
 * и видит: сколько накоплено, сколько осталось и примерно через сколько дней цель будет достигнута
 * (по средней сумме пополнения). Снять деньги можно только после отдельного подтверждения:
 * перед ним игра показывает, как уменьшится накопленное и насколько отодвинется цель.
 */

/** Цель накопления. Для поездки [tripId] — город из [Destinations]: билет покупается из копилки. */
data class SavingsGoal(
    val id: String,
    val title: String,
    val emoji: String,
    val cost: Int,
    val about: String,
    val tripId: String? = null
)

/** Что будет, если снять деньги из копилки: накоплено до/после и срок до цели до/после. */
data class WithdrawPreview(
    val amount: Int,
    val savedBefore: Int,
    val savedAfter: Int,
    val daysBefore: Int?,
    val daysAfter: Int?
)

object Goals {
    val all: List<SavingsGoal> = listOf(
        SavingsGoal(
            "trip_moscow", "Поездка в Москву", "🏰", 150,
            "Билет на поезд до столицы. В пути — три задания.", tripId = "moscow"
        ),
        SavingsGoal(
            "trip_kazan", "Поездка в Казань", "🕌", 200,
            "Город на Волге. Открывается после Москвы.", tripId = "kazan"
        ),
        SavingsGoal(
            "trip_spb", "Поездка в Санкт-Петербург", "🌉", 250,
            "Северная столица. Открывается после Казани.", tripId = "spb"
        ),
        SavingsGoal(
            "trip_sochi", "Отдых на море в Сочи", "🌊", 500,
            "Самая большая поездка. Открывается после Петербурга.", tripId = "sochi"
        ),
        SavingsGoal("bike", "Велосипед", "🚲", 300, "Кататься по набережной. Хорошее настроение надолго."),
        SavingsGoal("console", "Игровая приставка", "🎮", 600, "Большая покупка: копить придётся терпеливо.")
    )

    fun byId(id: String?): SavingsGoal? = all.firstOrNull { it.id == id }

    fun current(state: GameState): SavingsGoal? = byId(state.goalId)

    /** Цель уже недоступна (поездка совершена или вещь куплена). */
    fun isDone(state: GameState, goal: SavingsGoal): Boolean =
        goal.id in state.goalsDone || (goal.tripId != null && goal.tripId in state.completedTrips)

    /** Почему цель пока нельзя выбрать (null — можно). */
    fun blocker(state: GameState, goal: SavingsGoal): String? {
        if (isDone(state, goal)) return "Уже достигнута"
        val trip = goal.tripId?.let { Destinations.byId(it) } ?: return null
        val req = trip.requires
        if (req != null && req !in state.completedTrips) return "Сначала: ${Destinations.byId(req)?.name ?: "предыдущий город"}"
        return null
    }

    /** Сколько ещё не хватает до цели. */
    fun remaining(state: GameState): Int {
        val goal = current(state) ?: return 0
        return (goal.cost - state.piggy).coerceAtLeast(0)
    }

    /** Средняя сумма пополнения в день — по всему, что положено в копилку с первого пополнения. */
    fun averagePerDay(state: GameState): Int {
        if (state.piggyDeposited <= 0 || state.piggyFirstDay <= 0) return 0
        val days = (state.day - state.piggyFirstDay + 1).coerceAtLeast(1)
        return (state.piggyDeposited / days).coerceAtLeast(1)
    }

    /** Через сколько дней цель будет достигнута при таком темпе (null — пока не посчитать: не было пополнений). */
    fun etaDays(state: GameState, piggy: Int = state.piggy): Int? {
        val goal = current(state) ?: return null
        val left = goal.cost - piggy
        if (left <= 0) return 0
        val avg = averagePerDay(state)
        if (avg <= 0) return null
        return (left + avg - 1) / avg
    }

    fun choose(state: GameState, goalId: String): Outcome {
        val goal = byId(goalId) ?: return GameEngine.fail(state, "Такой цели нет.")
        blocker(state, goal)?.let { return GameEngine.fail(state, "Эту цель пока нельзя выбрать: $it.") }
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(goalId = goal.id)
        c.say("${goal.emoji} Новая цель: «${goal.title}» — ${goal.cost} монет. В копилке уже ${state.piggy}.")
        c.log("выбрал цель: ${goal.title}")
        c.teach(Lesson.GOAL)
        return c.done()
    }

    /** Положить в копилку. */
    fun deposit(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return GameEngine.fail(state, "Выбери, сколько монет отложить.")
        if (amount > state.coins) return GameEngine.fail(state, "В кошельке только ${state.coins} монет.")
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(
            coins = c.s.coins - amount,
            piggy = c.s.piggy + amount,
            piggyDeposited = c.s.piggyDeposited + amount,
            piggyFirstDay = if (c.s.piggyFirstDay <= 0) c.s.day else c.s.piggyFirstDay,
            daySaved = c.s.daySaved + amount,
            pSaved = c.s.pSaved + amount
        )
        val goal = current(c.s)
        if (goal != null) {
            val left = (goal.cost - c.s.piggy).coerceAtLeast(0)
            c.say(
                if (left == 0) "🐷 В копилке ${c.s.piggy} — на «${goal.title}» уже хватает! 🎉"
                else "🐷 Отложено $amount. В копилке ${c.s.piggy} из ${goal.cost}, осталось $left."
            )
        } else {
            c.say("🐷 Отложено $amount. В копилке ${c.s.piggy}. Выбери цель — так копить интереснее.")
        }
        c.log("в копилку +$amount")
        c.xpForSaving()
        c.teach(Lesson.GOAL)
        if (Medals.PIGGY_STARTED !in c.s.milestones) {
            c.s = c.s.copy(milestones = c.s.milestones + Medals.PIGGY_STARTED)
            c.skill(Skill.BANKER, 1)
        }
        return c.done()
    }

    /** Что будет, если снять [amount]: показывается до подтверждения. */
    fun previewWithdraw(state: GameState, amount: Int): WithdrawPreview {
        val a = amount.coerceIn(0, state.piggy)
        return WithdrawPreview(a, state.piggy, state.piggy - a, etaDays(state), etaDays(state, state.piggy - a))
    }

    /** Снять из копилки. Интерфейс вызывает это только после отдельного подтверждения ребёнка. */
    fun withdraw(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return GameEngine.fail(state, "Выбери, сколько монет снять.")
        if (amount > state.piggy) return GameEngine.fail(state, "В копилке только ${state.piggy} монет.")
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(
            coins = c.s.coins + amount,
            piggy = c.s.piggy - amount,
            daySaved = c.s.daySaved - amount,
            pSaved = c.s.pSaved - amount
        )
        val goal = current(c.s)
        c.say(
            "🐷 Из копилки снято $amount. Осталось ${c.s.piggy}" +
                (if (goal != null) " из ${goal.cost}: цель отодвинулась." else ".")
        )
        c.log("из копилки −$amount")
        return c.done()
    }

    /** Цель накоплена: потратить копилку на неё (поездка начинается, вещь покупается). */
    fun reach(state: GameState): Outcome {
        val goal = current(state) ?: return GameEngine.fail(state, "Сначала выбери цель.")
        if (state.piggy < goal.cost) return GameEngine.fail(state, "В копилке ${state.piggy} из ${goal.cost}: осталось ${goal.cost - state.piggy}.")
        val tripId = goal.tripId
        if (tripId != null) {
            val dest = Destinations.byId(tripId) ?: return GameEngine.fail(state, "Такого города нет.")
            GameEngine.destinationStatus(state, dest)?.let { return GameEngine.fail(state, it) }
        }
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(piggy = c.s.piggy - goal.cost, goalId = null)
        c.countSpend(goal.cost, SpendKind.OTHER, "${goal.title} (из копилки)")
        if (tripId != null) {
            c.s = c.s.copy(trip = TripProgress(tripId, 0))
            c.say("${goal.emoji} Цель достигнута! Билет куплен на деньги из копилки. Поехали!")
            c.log("поехал на деньги из копилки: ${goal.title}")
            c.teach(Lesson.BUDGET)
        } else {
            c.s = c.s.copy(goalsDone = c.s.goalsDone + goal.id, joys = c.s.joys + (goal.id to 1))
            c.mood(35, "Монтик купил то, на что долго копил, — «${goal.title}»!")
            c.say("${goal.emoji} Цель достигнута: «${goal.title}» куплен(а) на накопленные деньги! Терпение окупилось.")
            c.log("купил на накопленное: ${goal.title}")
        }
        c.skill(Skill.BANKER, 2)
        c.xp(Progress.XP_GOAL)
        c.s = c.s.copy(milestones = c.s.milestones + Medals.GOAL_REACHED)
        return c.done()
    }
}
