package ru.montik.app.game

/**
 * Задание при приезде в новый город: где открыть кофейню (Москва), что добавить в меню (Казань),
 * какую прогулку организовать (Петербург), что продавать на пляже (Сочи).
 * Это основы теории игр: выгода зависит не только от дохода места, но и от того, сколько там конкурентов.
 * Лучший вариант — тот, где доход на одного продавца (доход / (1 + конкуренты)) самый большой.
 */
object CityTasks {
    /** Вариант на карточке: доход в монетах и сколько конкурентов (1 — мало, 2 — средне, 3 — много). */
    class Option(val id: String, val title: String, val income: Int, val rivals: Int, val art: String) {
        val score: Float get() = income / (1f + rivals)
    }

    class Task(val city: String, val options: List<Option>) {
        val best: Option get() = options.maxBy { it.score }
    }

    /** Награда за лучший выбор и за любой другой (Монтик всё равно чему-то научился). */
    const val BEST_REWARD = 30
    const val OTHER_REWARD = 10

    val all: Map<String, Task> = listOf(
        Task(
            "moscow", listOf(
                Option("red", "Красная площадь", 120, 1, "fg3_card_moscow_red"),
                Option("vdnh", "ВДНХ", 90, 2, "fg3_card_moscow_vdnh"),
                Option("mgu", "МГУ", 80, 2, "fg3_card_moscow_mgu"),
                Option("arbat", "Арбат", 60, 3, "fg3_card_moscow_arbat"),
                Option("mall", "ТЦ Афимолл", 50, 3, "fg3_card_moscow_mall"),
                Option("park", "Парк Горького", 100, 1, "fg3_card_moscow_park")
            )
        ),
        Task(
            "kazan", listOf(
                Option("ice", "Мороженое", 80, 3, "fg3_card_kazan_ice"),
                Option("ech", "Эчпочмак", 120, 1, "fg3_card_kazan_ech"),
                Option("fries", "Картошка фри", 60, 2, "fg3_card_kazan_fries")
            )
        ),
        Task(
            "spb", listOf(
                Option("river", "Речная прогулка", 120, 1, "fg3_card_spb_river"),
                Option("bus", "Автобусный тур", 100, 2, "fg3_card_spb_bus"),
                Option("walk", "Пешая прогулка", 80, 3, "fg3_card_spb_walk")
            )
        ),
        Task(
            "sochi", listOf(
                Option("lemon", "Лимонад", 80, 2, "fg3_card_sochi_lemon"),
                Option("beds", "Лежаки", 120, 2, "fg3_card_sochi_beds"),
                Option("corn", "Горячая кукуруза", 100, 3, "fg3_card_sochi_corn")
            )
        )
    ).associateBy { it.city }

    /** Нужно ли сейчас показать задание: Монтик гуляет по городу, а задание этого города ещё не решено. */
    fun due(state: GameState): Task? {
        val trip = state.trip ?: return null
        if (trip.phase != TripPhase.CITY) return null
        if (trip.destinationId in state.cityTasks) return null
        return all[trip.destinationId]
    }

    fun solve(state: GameState, optionId: String): Outcome {
        val task = due(state) ?: return GameEngine.fail(state, "Задание уже решено.")
        val option = task.options.firstOrNull { it.id == optionId } ?: return GameEngine.fail(state, "Сначала выбери вариант.")
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(cityTasks = c.s.cityTasks + task.city)
        val best = task.best
        if (option.id == best.id) {
            c.earn(BEST_REWARD)
            c.xp(Progress.XP_DAILY_TASK)
            c.skill(Skill.ENTREPRENEUR, 2)
            c.say(
                "🏆 Отличный выбор: «${option.title}»! Доход ${option.income} монет и мало конкурентов — " +
                    "каждому продавцу достаётся больше покупателей. +$BEST_REWARD монет."
            )
        } else {
            c.earn(OTHER_REWARD)
            c.skill(Skill.ENTREPRENEUR, 1)
            c.say(
                "«${option.title}»: доход ${option.income}, но конкурентов ${rivalsWord(option.rivals)}. " +
                    "Выгоднее «${best.title}»: доход ${best.income} и конкурентов ${rivalsWord(best.rivals)}. " +
                    "Так работает теория игр: важно, сколько ещё людей выберет то же место. +$OTHER_REWARD монет."
            )
        }
        c.log("задание в городе ${task.city}: ${option.title}")
        return c.done()
    }

    private fun rivalsWord(n: Int): String = when (n) {
        1 -> "мало"
        2 -> "средне"
        else -> "много"
    }
}
