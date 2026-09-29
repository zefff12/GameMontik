package ru.montik.app.game

/*
 * Финансовые задания — учебный контент, отдельный от экранов и от логики игры.
 * Чтобы добавить задание, достаточно дописать его в список [Tasks.all]: экраны и правила
 * (награда, открытие, оценка) работают с любым заданием одинаково.
 *
 * Задания бывают четырёх видов — не только «выбери ответ»:
 *  • [TaskKind.Pick]   — ситуация с выбором и последствиями;
 *  • [TaskKind.Basket] — собрать покупки так, чтобы уложиться в сумму и не забыть нужное;
 *  • [TaskKind.Split]  — разложить деньги по трём конвертам;
 *  • [TaskKind.Count]  — посчитать (сдачу, срок накопления, 20% от зарплаты).
 * После любого ответа ребёнок получает короткое объяснение — и за верный, и за неверный.
 */

enum class TaskTheme(val title: String, val emoji: String) {
    BUDGET("Планирование бюджета", "📋"),
    SAVING("Сбережения", "🐷"),
    PAYMENTS("Платежи и покупки", "🧾")
}

/** Вариант в задании-выборе. */
data class TaskOption(val text: String, val rating: Rating, val outcome: String, val explanation: String)

/** Товар в задании «корзина». [need] — обязательный (без него нельзя). */
data class BasketItem(val name: String, val emoji: String, val price: Int, val need: Boolean)

sealed class TaskKind {
    class Pick(val options: List<TaskOption>) : TaskKind()

    class Basket(val budget: Int, val items: List<BasketItem>) : TaskKind()

    /** Разложить [total] по трём конвертам: обязательное не меньше [minNeeds], копилка не меньше [minSavings]. */
    class Split(val total: Int, val minNeeds: Int, val minSavings: Int, val step: Int = 10) : TaskKind()

    class Count(val answer: Int, val solution: String) : TaskKind()
}

data class FinTask(
    val id: String,
    val theme: TaskTheme,
    val title: String,
    val emoji: String,
    val situation: String,
    val kind: TaskKind,
    val reward: Int = 20,
    val lesson: Lesson? = null
)

/** Итог задания: оценка, что случилось и почему. */
data class TaskOutcome(val rating: Rating, val outcome: String, val explanation: String)

object Tasks {
    val all: List<FinTask> = listOf(
        FinTask(
            id = "t_split_week",
            theme = TaskTheme.BUDGET,
            title = "Карманные деньги на неделю",
            emoji = "✉️",
            situation = "У Монтика 100 монет на неделю. На обеды нужно не меньше 60. " +
                "Разложи деньги по трём конвертам: обязательное, желаемое и копилка.",
            kind = TaskKind.Split(total = 100, minNeeds = 60, minSavings = 10),
            lesson = Lesson.PLAN
        ),
        FinTask(
            id = "t_count_change",
            theme = TaskTheme.PAYMENTS,
            title = "Сдача в магазине",
            emoji = "🧾",
            situation = "Монтик купил сок за 38 и булочку за 25 монет, а заплатил 100. Сколько сдачи ему должны вернуть?",
            kind = TaskKind.Count(37, "38 + 25 = 63. 100 − 63 = 37 монет сдачи."),
            lesson = Lesson.CHANGE
        ),
        FinTask(
            id = "t_count_goal",
            theme = TaskTheme.SAVING,
            title = "Сколько дней копить?",
            emoji = "🚲",
            situation = "Самокат стоит 120 монет. Монтик откладывает по 15 монет в день. Через сколько дней он сможет его купить?",
            kind = TaskKind.Count(8, "120 : 15 = 8 дней. Если откладывать больше — цель придёт быстрее."),
            lesson = Lesson.GOAL
        ),
        FinTask(
            id = "t_basket_list",
            theme = TaskTheme.PAYMENTS,
            title = "Покупки по списку",
            emoji = "🛒",
            situation = "У Монтика 60 монет. В списке — хлеб, молоко и яблоки. В магазине ещё лежат шоколадка и игрушка. " +
                "Собери корзину: всё нужное и не дороже 60.",
            kind = TaskKind.Basket(
                budget = 60,
                items = listOf(
                    BasketItem("Хлеб", "🍞", 8, need = true),
                    BasketItem("Молоко", "🥛", 12, need = true),
                    BasketItem("Яблоки", "🍎", 15, need = true),
                    BasketItem("Шоколадка", "🍫", 10, need = false),
                    BasketItem("Игрушка", "🧸", 30, need = false)
                )
            ),
            lesson = Lesson.NEEDS
        ),
        FinTask(
            id = "t_pick_birthday",
            theme = TaskTheme.BUDGET,
            title = "Подарок другу",
            emoji = "🎂",
            situation = "У друга Монтика через неделю день рождения. Сейчас в кошельке 40 монет, а подарок стоит 60. " +
                "Как поступить?",
            kind = TaskKind.Pick(
                listOf(
                    TaskOption(
                        "Каждый день откладывать немного, чтобы к празднику хватило", Rating.GREAT,
                        "За неделю Монтик собрал нужную сумму и купил подарок.",
                        "Когда трата известна заранее, её планируют: делят на части и откладывают понемногу."
                    ),
                    TaskOption(
                        "Взять в долг у соседа", Rating.OK,
                        "Подарок куплен, но теперь нужно отдавать долг.",
                        "Долг — это будущие траты. Если время есть, лучше накопить самому."
                    ),
                    TaskOption(
                        "Ничего не делать — как-нибудь получится", Rating.BAD,
                        "В день праздника денег не хватило, и подарок купить не вышло.",
                        "Без плана деньги расходятся на мелочи. Запиши, сколько нужно и к какому дню."
                    )
                )
            ),
            lesson = Lesson.PLAN
        ),
        FinTask(
            id = "t_count_cushion",
            theme = TaskTheme.SAVING,
            title = "Пятая часть зарплаты",
            emoji = "🛟",
            situation = "Монтик получил 150 монет. Хорошее правило — откладывать пятую часть (20%). Сколько монет ему отложить?",
            kind = TaskKind.Count(30, "150 : 5 = 30. Пятая часть — это то же самое, что 20%."),
            lesson = Lesson.CUSHION
        ),
        FinTask(
            id = "t_pick_ad",
            theme = TaskTheme.PAYMENTS,
            title = "«Только сегодня!»",
            emoji = "📣",
            situation = "В телефоне всплыла яркая реклама: «Наушники со скидкой, только сегодня!» " +
                "Монтик копит на велосипед. Что ему сделать?",
            kind = TaskKind.Pick(
                listOf(
                    TaskOption(
                        "Закрыть рекламу и подумать, нужны ли наушники", Rating.GREAT,
                        "Монтик понял, что наушники у него есть, а велосипед стал ближе.",
                        "Реклама торопит, чтобы ты не успел подумать. Хорошая покупка — та, которую ты запланировал."
                    ),
                    TaskOption(
                        "Сравнить цену в других магазинах и решить завтра", Rating.OK,
                        "Оказалось, что «скидка» — обычная цена. Монтик ничего не потерял.",
                        "Сравнивать цены полезно. Но сначала стоит спросить себя: нужна ли эта вещь?"
                    ),
                    TaskOption(
                        "Купить сразу, пока скидка не пропала", Rating.BAD,
                        "Наушники пришли, но денег на велосипед стало меньше.",
                        "«Только сегодня» — приём рекламы. Покупка не по плану отодвигает настоящую цель."
                    )
                )
            ),
            lesson = Lesson.ADS
        ),
        FinTask(
            id = "t_split_salary",
            theme = TaskTheme.BUDGET,
            title = "Первая зарплата",
            emoji = "💼",
            situation = "Монтик получил 200 монет. За квартиру и еду нужно отдать не меньше 120, " +
                "а на велосипед он хочет откладывать не меньше 30. Разложи деньги по конвертам.",
            kind = TaskKind.Split(total = 200, minNeeds = 120, minSavings = 30),
            lesson = Lesson.PLAN
        ),
        FinTask(
            id = "t_pick_piggy",
            theme = TaskTheme.SAVING,
            title = "Копилка и соблазн",
            emoji = "🐷",
            situation = "В копилке Монтика 90 монет — он копит на поездку. Друзья зовут в кафе, обед там стоит 40. Как быть?",
            kind = TaskKind.Pick(
                listOf(
                    TaskOption(
                        "Пойти, но заплатить из денег на желаемое, а копилку не трогать", Rating.GREAT,
                        "Монтик провёл время с друзьями, а цель осталась на месте.",
                        "Для развлечений есть «желаемое» в плане. Копилку лучше тратить только на цель."
                    ),
                    TaskOption(
                        "Пойти и взять деньги из копилки", Rating.OK,
                        "Было весело, но поездка отодвинулась на несколько дней.",
                        "Снимать из копилки можно, но каждый раз цель уходит дальше."
                    ),
                    TaskOption(
                        "Потратить всю копилку на кафе и сладости", Rating.BAD,
                        "Копилка опустела, и копить на поездку придётся заново.",
                        "Накопления растут медленно, а тратятся быстро. Думай о цели."
                    )
                )
            ),
            lesson = Lesson.GOAL
        ),
        FinTask(
            id = "t_basket_price",
            theme = TaskTheme.PAYMENTS,
            title = "Сравни и выбери",
            emoji = "🏷",
            situation = "Монтику нужны тетрадь и ручка, а в кошельке 25 монет. Тетради бывают за 10 и за 18, ручки — за 5 и за 12. " +
                "Выбери по одной тетради и ручке так, чтобы хватило денег и осталось как можно больше.",
            kind = TaskKind.Basket(
                budget = 25,
                items = listOf(
                    BasketItem("Тетрадь", "📓", 10, need = true),
                    BasketItem("Тетрадь с блёстками", "📔", 18, need = false),
                    BasketItem("Ручка", "🖊", 5, need = true),
                    BasketItem("Ручка с фонариком", "🔦", 12, need = false)
                )
            ),
            lesson = Lesson.CLOTHES
        )
    )

    fun byId(id: String): FinTask? = all.firstOrNull { it.id == id }

    /** Сколько заданий открыто: два в первый день и по одному новому каждый следующий. В демо-режиме — все. */
    fun unlockedCount(state: GameState): Int =
        if (state.demo) all.size else (2 + state.day - 1).coerceAtMost(all.size)

    fun unlocked(state: GameState): List<FinTask> = all.take(unlockedCount(state))

    /** Активное задание для главного экрана — первое открытое и ещё не пройденное. */
    fun active(state: GameState): FinTask? = unlocked(state).firstOrNull { it.id !in state.taskResults }

    fun doneCount(state: GameState): Int = all.count { it.id in state.taskResults }

    /** Награда за первое прохождение: полностью за отличный результат, меньше — за остальные. */
    fun reward(task: FinTask, rating: Rating): Int = when (rating) {
        Rating.GREAT -> task.reward
        Rating.OK -> task.reward * 2 / 3
        Rating.BAD -> maxOf(5, task.reward / 3)
    }

    // ───────────────────────── Проверка ответов ─────────────────────────

    fun checkPick(task: FinTask, index: Int): TaskOutcome? {
        val kind = task.kind as? TaskKind.Pick ?: return null
        val o = kind.options.getOrNull(index) ?: return null
        return TaskOutcome(o.rating, o.outcome, o.explanation)
    }

    fun checkBasket(task: FinTask, selected: Set<Int>): TaskOutcome? {
        val kind = task.kind as? TaskKind.Basket ?: return null
        val chosen = selected.mapNotNull { kind.items.getOrNull(it) }
        val total = chosen.sumOf { it.price }
        val missing = kind.items.filter { it.need && it !in chosen }
        val extras = chosen.filter { !it.need }
        return when {
            total > kind.budget -> TaskOutcome(
                Rating.BAD,
                "В корзине товаров на $total монет, а в кошельке только ${kind.budget}.",
                "Покупать больше, чем есть денег, нельзя. Сначала положи нужное, а желаемое — если останется."
            )
            missing.isNotEmpty() -> TaskOutcome(
                Rating.BAD,
                "Забыли нужное: ${missing.joinToString { it.name.lowercase() }}.",
                "Нужное покупают в первую очередь — без него не обойтись."
            )
            extras.isEmpty() -> TaskOutcome(
                Rating.GREAT,
                "Всё нужное куплено за $total монет, осталось ${kind.budget - total}.",
                "Отлично: ты взял только нужное и сэкономил. Остаток можно отложить."
            )
            else -> TaskOutcome(
                Rating.OK,
                "Всё нужное есть, а ещё ${extras.joinToString { it.name.lowercase() }}. Потрачено $total из ${kind.budget}.",
                "Уложился в сумму — хорошо. Но подумай: желаемое точно было нужно сегодня?"
            )
        }
    }

    fun checkSplit(task: FinTask, needs: Int, wants: Int, savings: Int): TaskOutcome? {
        val kind = task.kind as? TaskKind.Split ?: return null
        val sum = needs + wants + savings
        return when {
            sum != kind.total -> TaskOutcome(
                Rating.BAD,
                "Разложено $sum монет, а нужно ровно ${kind.total}.",
                "В плане должны быть распределены все деньги — ни больше, ни меньше."
            )
            needs < kind.minNeeds -> TaskOutcome(
                Rating.BAD,
                "На обязательное всего $needs, а нужно не меньше ${kind.minNeeds}.",
                "Сначала обеспечивают обязательное: еду и жильё. Желаемое — после."
            )
            savings < kind.minSavings -> TaskOutcome(
                Rating.OK,
                "Обязательное обеспечено, но в копилку только $savings (хотелось не меньше ${kind.minSavings}).",
                "Регулярно откладывать даже немного — так цель становится ближе."
            )
            else -> TaskOutcome(
                Rating.GREAT,
                "Обязательное $needs, желаемое $wants, копилка $savings — идеально!",
                "Так и строят бюджет: нужное, немного на радости и обязательно — в копилку."
            )
        }
    }

    fun checkCount(task: FinTask, answer: Int?): TaskOutcome? {
        val kind = task.kind as? TaskKind.Count ?: return null
        return if (answer == kind.answer) TaskOutcome(Rating.GREAT, "Верно: ${kind.answer}!", kind.solution)
        else TaskOutcome(
            Rating.BAD,
            if (answer == null) "Ответа нет." else "Не совсем: получилось $answer, а правильно — ${kind.answer}.",
            kind.solution + " Попробуй ещё раз — ошибаться можно, так учатся."
        )
    }

    /**
     * Записать результат. За первое прохождение начисляются монеты (с указанием, за что), повторное —
     * без награды, но лучшая оценка запоминается.
     */
    fun complete(state: GameState, taskId: String, result: TaskOutcome): Outcome {
        val task = byId(taskId) ?: return GameEngine.fail(state, "Такого задания нет.")
        if (task !in unlocked(state)) return GameEngine.fail(state, "Это задание откроется позже.")
        val c = GameEngine.Ctx(state)
        val before = state.taskResults[taskId]
        val best = if (before == null || result.rating.ordinal < before.ordinal) result.rating else before
        c.s = c.s.copy(taskResults = c.s.taskResults + (taskId to best))
        if (before == null) {
            val coins = reward(task, result.rating)
            c.earn(coins)
            c.say("${task.emoji} Задание «${task.title}» выполнено: +$coins монет (награда за задание).")
            c.log("задание «${task.title}» (+$coins)")
            c.skill(Skill.PLANNER, if (result.rating == Rating.GREAT) 2 else 1)
            c.xp(Progress.taskXp(result.rating))
        } else {
            c.say("${task.emoji} Задание пройдено ещё раз. Награда даётся только за первый раз, а опыт остаётся с тобой.")
        }
        task.lesson?.let { c.teach(it) }
        return c.done()
    }
}
