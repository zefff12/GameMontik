package ru.montik.app.game

import kotlin.random.Random

/** Числа, на которых держится экономика игры. */
object Rules {
    const val START_COINS = 50
    const val TAX_PERCENT = 13
    const val CABIN_PRICE = 20
    const val SHIFT_ENERGY = 35
    const val SHIFT_FOOD = 15
    const val SHIFT_WATER = 15
    const val NIGHT_FOOD = 15
    const val NIGHT_WATER = 15
    const val CUSHION_GOAL = 100
    const val CUSHION_STARTER = 20
    const val TASK_BONUS_PERCENT = 20
    const val STOP_ENERGY = 10
    const val STOP_FOOD = 8
    const val STOP_WATER = 8
    const val SHIFTS_FOR_SELLER = 3
    const val MAX_LOG = 40
    const val BENCH_ENERGY = 55
    const val EVENT_MIN_DAY = 3
    const val EVENT_GAP_DAYS = 3
    const val EVENT_CHANCE_PERCENT = 35

    /** Ниже этих значений Монтик не выходит на смену: сначала нужно поспать, поесть и попить. */
    const val MIN_WORK_ENERGY = 10
    const val MIN_WORK_FOOD = 10
    const val MIN_WORK_WATER = 10

    /** Мелкое поручение — «подстраховка», чтобы игра не зашла в тупик без денег и еды. */
    const val CHORE_COINS = 10

    /** Банк: вклад приносит проценты в конце каждого дня. */
    const val DEPOSIT_PERCENT = 10
    const val MIN_DEPOSIT = 10

    /** Банк: кредит выдают одной суммой, а вернуть нужно больше — разница и есть плата банку. */
    const val LOAN_AMOUNT = 50
    const val LOAN_REPAY = 60
}

/** Результат любого действия: новое состояние, сообщения для ребёнка и новые уроки. */
data class Outcome(
    val state: GameState,
    val messages: List<String> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val ok: Boolean = true,
    /** Медали, заработанные именно этим действием (их показывают с поздравлением). */
    val medals: List<Medal> = emptyList()
)

/** Расчётный листок: «начислено», налог 13% и «на руки». */
data class Payslip(
    val jobTitle: String,
    val base: Int,
    val efficiencyPercent: Int,
    val appearancePercent: Int,
    val taskBonusPercent: Int,
    val gross: Int,
    val tax: Int,
    val net: Int,
    val notes: List<String>
)

data class ShiftResult(val outcome: Outcome, val payslip: Payslip?)

data class ChoiceResult(val outcome: Outcome, val choice: Choice?, val rating: Rating?)

/** Задание на смене: вопрос с тремя вариантами ответа. */
data class WorkTask(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

enum class SleepPlace { CABIN, BENCH }

data class JobStatus(val job: Job, val unlocked: Boolean, val hint: String?)

object GameEngine {
    const val MILESTONE_STARTER = "cushion_started"
    const val MILESTONE_CUSHION = "cushion_ready"

    fun newGame(seed: Long): GameState = GameState(seed = seed)

    // ───────────────────────── Внутренний помощник ─────────────────────────

    private class Ctx(var s: GameState) {
        val messages = mutableListOf<String>()
        val lessons = mutableListOf<Lesson>()

        /** Медали, которые уже были до действия: по разнице видно, какие заработаны только что. */
        private val medalsBefore = Medals.earned(s).map { it.id }.toSet()

        fun say(text: String) {
            messages += text
        }

        fun log(text: String) {
            s = s.copy(log = (s.log + "День ${s.day}: $text").takeLast(Rules.MAX_LOG))
        }

        fun teach(lesson: Lesson) {
            if (lesson.name !in s.seenLessons) {
                s = s.copy(seenLessons = s.seenLessons + lesson.name)
                lessons += lesson
            }
        }

        fun earn(n: Int) {
            s = s.copy(coins = s.coins + n, dayEarned = s.dayEarned + n, totalEarned = s.totalEarned + n)
        }

        /** Списывает [n] монет. Вызывающий обязан убедиться, что денег хватает. */
        fun spend(n: Int) {
            s = s.copy(coins = s.coins - n, daySpent = s.daySpent + n, totalSpent = s.totalSpent + n)
        }

        /**
         * Платит [n] монет: сначала из кошелька, при [allowCushion] — остаток из подушки безопасности.
         * Возвращает, сколько взято из подушки.
         */
        fun pay(n: Int, allowCushion: Boolean): Int {
            val fromWallet = minOf(s.coins, n)
            val rest = n - fromWallet
            val fromCushion = if (allowCushion) minOf(rest, s.cushion) else 0
            s = s.copy(
                coins = s.coins - fromWallet,
                cushion = s.cushion - fromCushion,
                daySpent = s.daySpent + fromWallet + fromCushion,
                totalSpent = s.totalSpent + fromWallet + fromCushion
            )
            return fromCushion
        }

        fun meters(energy: Int = 0, food: Int = 0, water: Int = 0) {
            s = s.copy(
                energy = (s.energy + energy).coerceIn(0, 100),
                food = (s.food + food).coerceIn(0, 100),
                water = (s.water + water).coerceIn(0, 100)
            )
        }

        fun skill(skill: Skill?, points: Int) {
            if (skill == null || points <= 0) return
            val before = s.skillLevel(skill)
            s = s.copy(skills = s.skills + (skill to (s.skillPoints(skill) + points)))
            val after = s.skillLevel(skill)
            if (after > before) say("${skill.emoji} Новый уровень навыка «${skill.title}»: $after!")
        }

        /** Отмечает навсегда то, что потом можно потратить или снять (иначе медаль бы «пропала»). */
        private fun markMilestones() {
            if (s.wornCount >= 3 && Medals.STYLE_THREE !in s.milestones) {
                s = s.copy(milestones = s.milestones + Medals.STYLE_THREE)
            }
        }

        fun done(ok: Boolean = true): Outcome {
            if (!ok) return Outcome(s, messages.toList(), lessons.toList(), false)
            markMilestones()
            val fresh = Medals.earned(s).filter { it.id !in medalsBefore }
            for (m in fresh) log("медаль «${m.title}»")
            return Outcome(s, messages.toList(), lessons.toList(), true, fresh)
        }
    }

    private fun fail(state: GameState, text: String) = Outcome(state, listOf(text), emptyList(), ok = false)

    // ───────────────────────── Магазин ─────────────────────────

    fun buyFood(state: GameState, foodId: String): Outcome {
        val item = Catalog.foodItem(foodId) ?: return fail(state, "Такого товара нет.")
        if (state.coins < item.price) return fail(state, "Не хватает монет: нужно ${item.price}, а есть ${state.coins}.")
        val c = Ctx(state)
        c.spend(item.price)
        c.meters(food = item.food, water = item.water)
        c.say("${item.emoji} ${item.name} куплен(а) за ${item.price} монет.")
        c.log("купил ${item.name.lowercase()} (−${item.price})")
        c.teach(Lesson.NEEDS)
        return c.done()
    }

    fun buyClothing(state: GameState, itemId: String): Outcome {
        val item = Catalog.clothing(itemId) ?: return fail(state, "Такой вещи нет.")
        if (state.coins < item.price) return fail(state, "Не хватает монет: нужно ${item.price}, а есть ${state.coins}.")
        val c = Ctx(state)
        c.spend(item.price)
        c.s = c.s.copy(
            owned = c.s.owned + (item.id to item.durabilityDays),
            worn = c.s.worn + (item.slot to item.id)
        )
        c.say("${item.emoji} ${item.name} куплена за ${item.price} монет и уже надета. Прослужит ${item.durabilityDays} ноч.")
        c.log("купил ${item.name.lowercase()} (−${item.price})")
        c.teach(Lesson.CLOTHES)
        c.teach(Lesson.WANT_NEED)
        return c.done()
    }

    fun wear(state: GameState, itemId: String): Outcome {
        val item = Catalog.clothing(itemId)
        if (item == null || itemId !in state.owned) return fail(state, "У Монтика нет такой вещи.")
        val c = Ctx(state)
        c.s = c.s.copy(worn = c.s.worn + (item.slot to item.id))
        c.say("${item.emoji} ${item.name} надета.")
        return c.done()
    }

    fun takeOff(state: GameState, slot: Slot): Outcome {
        if (slot !in state.worn) return fail(state, "Здесь ничего не надето.")
        return Outcome(state.copy(worn = state.worn - slot))
    }

    // ───────────────────────── Работа ─────────────────────────

    fun jobStatus(state: GameState, job: Job): JobStatus = when (job) {
        Job.HELPER -> JobStatus(job, true, null)
        Job.SELLER -> {
            val needShifts = maxOf(0, Rules.SHIFTS_FOR_SELLER - state.shifts)
            val needClothes = maxOf(0, 2 - state.wornCount)
            val hints = mutableListOf<String>()
            if (needShifts > 0) hints += "ещё смен: $needShifts"
            if (needClothes > 0) hints += "надень ещё вещей: $needClothes"
            if (hints.isEmpty()) JobStatus(job, true, null)
            else JobStatus(job, false, hints.joinToString(", "))
        }
    }

    /**
     * Почему Монтик прямо сейчас не может выйти на смену (null — может).
     * Силы, еда и вода обязательны: без них не работают и не живут.
     */
    fun workBlocker(state: GameState): String? = when {
        state.energy < Rules.MIN_WORK_ENERGY ->
            "😴 Монтик совсем без сил. Сначала нужно поспать: работать можно, когда сил хотя бы ${Rules.MIN_WORK_ENERGY}."
        state.food < Rules.MIN_WORK_FOOD ->
            "🍲 Монтик слишком голоден. Сначала нужно поесть: работать можно, когда еды хотя бы ${Rules.MIN_WORK_FOOD}."
        state.water < Rules.MIN_WORK_WATER ->
            "💧 Монтику нечем утолить жажду. Сначала нужно попить: работать можно, когда воды хотя бы ${Rules.MIN_WORK_WATER}."
        else -> null
    }

    /**
     * Можно ли взять мелкое поручение. Это страховка от тупика: она появляется,
     * только когда Монтик не может работать и денег нет даже на хлеб и воду.
     */
    fun choreAvailable(state: GameState): Boolean =
        workBlocker(state) != null && state.coins < Rules.CHORE_COINS && state.cushion == 0

    /** Мелкое поручение: немного монет без затрат сил, чтобы купить еду и воду и вернуться к работе. */
    fun doChore(state: GameState): Outcome {
        if (!choreAvailable(state)) {
            return fail(state, "Поручение сейчас не нужно: у Монтика есть силы или деньги на еду.")
        }
        val c = Ctx(state)
        c.earn(Rules.CHORE_COINS)
        c.say("🤝 Монтик помог соседке донести сумку и получил ${Rules.CHORE_COINS} монет. Теперь хватит на хлеб и воду.")
        c.log("мелкое поручение (+${Rules.CHORE_COINS})")
        c.teach(Lesson.NEEDS)
        return c.done()
    }

    /** Эффективность работы в процентах: зависит от сил, сытости и жажды. */
    fun efficiency(state: GameState): Int {
        var e = when {
            state.energy >= 60 -> 100
            state.energy >= 30 -> 70
            else -> 40
        }
        if (state.food < 20) e -= 15
        if (state.water < 20) e -= 15
        return maxOf(20, e)
    }

    /** Придумывает задание для смены. Для одного и того же состояния результат одинаков. */
    fun makeTask(state: GameState, job: Job): WorkTask {
        val rnd = Random(state.seed * 1_000_003L + state.shifts * 31L + state.day)
        return when (job) {
            Job.HELPER -> when (rnd.nextInt(3)) {
                0 -> {
                    val a = rnd.nextInt(3, 10)
                    val b = rnd.nextInt(3, 10)
                    task(
                        "В одной коробке $a шурупов, в другой — $b гаек. Сколько всего деталей нужно отправить заказчику?",
                        a + b, rnd,
                        "$a + $b = ${a + b}. Мастер ценит, когда работа сделана внимательно."
                    )
                }
                1 -> {
                    val boxes = rnd.nextInt(2, 6)
                    val per = rnd.nextInt(3, 8)
                    task(
                        "Нужно разложить детали по $boxes коробкам, в каждой по $per штук. Сколько всего деталей?",
                        boxes * per, rnd,
                        "$boxes × $per = ${boxes * per}. Умножение помогает считать быстро."
                    )
                }
                else -> {
                    val all = rnd.nextInt(15, 30)
                    val bad = rnd.nextInt(2, 8)
                    task(
                        "Из $all деталей $bad оказались бракованными. Сколько хороших деталей осталось?",
                        all - bad, rnd,
                        "$all − $bad = ${all - bad}. Брак нужно откладывать в сторону."
                    )
                }
            }
            Job.SELLER -> {
                val given = if (rnd.nextBoolean()) 50 else 100
                val price = rnd.nextInt(7, if (given == 50) 46 else 91)
                task(
                    "Покупатель дал $given монет, а товар стоит $price. Сколько сдачи нужно вернуть?",
                    given - price, rnd,
                    "$given − $price = ${given - price}. Сдача — это разница между тем, что дали, и ценой."
                )
            }
        }
    }

    private fun task(question: String, answer: Int, rnd: Random, explanation: String): WorkTask {
        val wrong = linkedSetOf<Int>()
        val candidates = listOf(answer + 1, answer - 1, answer + 2, answer - 2, answer + 10, answer - 10)
            .filter { it > 0 && it != answer }
            .shuffled(rnd)
        for (w in candidates) {
            if (wrong.size < 2) wrong += w
        }
        val options = (wrong + answer).toList().shuffled(rnd)
        return WorkTask(question, options.map { "$it" }, options.indexOf(answer), explanation)
    }

    /**
     * Смена: [taskCorrect] — правильно ли решено задание (null — задание пропущено).
     */
    fun work(state: GameState, job: Job, taskCorrect: Boolean?): ShiftResult {
        val status = jobStatus(state, job)
        if (!status.unlocked) return ShiftResult(fail(state, "Эта работа пока недоступна: ${status.hint}"), null)
        workBlocker(state)?.let { return ShiftResult(fail(state, it), null) }

        val c = Ctx(state)
        val lowEnergy = state.energy < 60
        val eff = efficiency(state)
        val appearance = state.appearancePercent
        val bonus = if (taskCorrect == true) Rules.TASK_BONUS_PERCENT else 0
        val total = eff + appearance + bonus
        val gross = (job.base * total + 50) / 100
        val tax = (gross * Rules.TAX_PERCENT + 50) / 100
        val net = gross - tax

        val notes = mutableListOf<String>()
        if (eff < 100) notes += "Из-за усталости, голода или жажды Монтик работал не в полную силу ($eff%)."
        if (appearance > 0) notes += "Опрятный вид добавил +$appearance% к заработку."
        if (bonus > 0) notes += "Задание решено верно: премия +$bonus%."
        if (taskCorrect == false) notes += "Задание решено неверно — премии нет."

        c.earn(net)
        c.s = c.s.copy(shifts = c.s.shifts + 1, totalTax = c.s.totalTax + tax)
        c.meters(energy = -Rules.SHIFT_ENERGY, food = -Rules.SHIFT_FOOD, water = -Rules.SHIFT_WATER)
        if (taskCorrect == true) {
            c.skill(Skill.CREATOR, 1)
            c.s = c.s.copy(tasksCorrect = c.s.tasksCorrect + 1)
        }
        c.log("смена «${job.title}»: начислено $gross, налог $tax, на руки $net")

        c.teach(Lesson.WORK)
        c.teach(Lesson.TAX)
        if (job == Job.SELLER) c.teach(Lesson.CHANGE)
        if (lowEnergy || eff < 100) c.teach(Lesson.ENERGY)

        val slip = Payslip(job.title, job.base, eff, appearance, bonus, gross, tax, net, notes)
        return ShiftResult(c.done(), slip)
    }

    // ───────────────────────── Сон, конец дня, дневник ─────────────────────────

    /** Уложить Монтика спать. Если денег на «домик» не хватает, он ночует на скамейке. */
    fun sleep(state: GameState, place: SleepPlace): Outcome {
        val c = Ctx(state)
        c.teach(Lesson.ENERGY)

        var restEnergy = 100
        var usedPlace = place
        if (place == SleepPlace.CABIN) {
            when {
                c.s.prepaidNight -> {
                    c.s = c.s.copy(prepaidNight = false)
                    c.say("🏠 Ночь в домике уже оплачена — Монтик хорошо выспался.")
                }
                c.s.coins >= Rules.CABIN_PRICE -> {
                    c.spend(Rules.CABIN_PRICE)
                    c.say("🏠 Ночь в домике: −${Rules.CABIN_PRICE} монет. Монтик выспался!")
                }
                else -> {
                    usedPlace = SleepPlace.BENCH
                    c.say("Не хватило ${Rules.CABIN_PRICE} монет на домик — пришлось ночевать на скамейке.")
                }
            }
        }
        if (usedPlace == SleepPlace.BENCH) {
            restEnergy = Rules.BENCH_ENERGY
            c.say("🪑 На скамейке спится плохо: сил восстановилось только на $restEnergy%. Усталость снижает заработок.")
        }

        // Ночью Монтик тратит еду и воду.
        c.meters(food = -Rules.NIGHT_FOOD, water = -Rules.NIGHT_WATER)
        if (c.s.food == 0 || c.s.water == 0) {
            restEnergy -= 25
            c.say("🍲 Монтик лёг спать голодным или без воды и отдохнул плохо (−25 сил). Еда и вода нужны каждый день.")
        }
        c.s = c.s.copy(energy = restEnergy.coerceIn(0, 100))

        // Дневник: сколько заработал, потратил, отложил.
        val saved = maxOf(0, c.s.daySaved)
        val diary = DayDiary(c.s.day, c.s.dayEarned, c.s.daySpent, saved)
        c.teach(Lesson.DIARY)
        if (diary.earned > 0 && diary.saved * 100 >= diary.earned * 20) {
            c.skill(Skill.PLANNER, 1)
            c.say("🪙 Ты отложил не меньше 20% заработка — так делают настоящие планировщики!")
        }
        c.log("день закончен: +${diary.earned}, −${diary.spent}, отложено ${diary.saved}")

        // Банк: за полный день на вкладе начисляются проценты.
        val interest = dailyInterest(c.s)
        if (interest > 0) {
            c.s = c.s.copy(deposit = c.s.deposit + interest)
            c.say("🏦 Банк начислил $interest монет на вклад. Теперь на вкладе ${c.s.deposit} — деньги работают сами.")
            c.log("проценты по вкладу +$interest")
            c.teach(Lesson.DEPOSIT)
        }
        if (c.s.debt > 0) {
            c.say("💳 Не забудь: банку нужно вернуть ${c.s.debt} монет. Чем раньше вернёшь, тем спокойнее.")
        }

        // Одежда изнашивается.
        val owned = c.s.owned.toMutableMap()
        val worn = c.s.worn.toMutableMap()
        for ((slot, id) in c.s.worn) {
            val left = (owned[id] ?: 0) - 1
            if (left <= 0) {
                owned.remove(id)
                worn.remove(slot)
                val item = Catalog.clothing(id)
                c.say("${item?.emoji ?: "👕"} ${item?.name ?: "Вещь"} износилась. Дешёвые вещи служат недолго — сравнивай цену и качество!")
            } else {
                owned[id] = left
            }
        }

        // Новый день.
        val newDay = c.s.day + 1
        c.s = c.s.copy(
            day = newDay,
            owned = owned,
            worn = worn,
            diary = diary,
            dayEarned = 0,
            daySpent = 0,
            daySaved = 0
        )

        // Возможное непредвиденное событие.
        val rnd = Random(c.s.seed * 7_919L + newDay)
        if (newDay >= Rules.EVENT_MIN_DAY &&
            newDay - c.s.lastEventDay >= Rules.EVENT_GAP_DAYS &&
            c.s.pendingEvent == null &&
            rnd.nextInt(100) < Rules.EVENT_CHANCE_PERCENT
        ) {
            c.s = c.s.copy(pendingEvent = Scenarios.MISHAP, lastEventDay = newDay)
        }
        return c.done()
    }

    // ───────────────────────── Подушка безопасности ─────────────────────────

    fun depositCushion(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return fail(state, "Выбери, сколько монет отложить.")
        if (amount > state.coins) return fail(state, "В кошельке только ${state.coins} монет.")
        val c = Ctx(state)
        c.s = c.s.copy(coins = c.s.coins - amount, cushion = c.s.cushion + amount, daySaved = c.s.daySaved + amount)
        c.say("🛟 Отложено $amount монет. В подушке: ${c.s.cushion}.")
        c.log("отложил в подушку $amount")
        c.teach(Lesson.CUSHION)
        // Очки «Банкира» даются за вехи, а не за каждый вклад — так их нельзя накрутить.
        if (c.s.cushion >= Rules.CUSHION_STARTER && MILESTONE_STARTER !in c.s.milestones) {
            c.s = c.s.copy(milestones = c.s.milestones + MILESTONE_STARTER)
            c.skill(Skill.BANKER, 1)
        }
        if (c.s.cushion >= Rules.CUSHION_GOAL && MILESTONE_CUSHION !in c.s.milestones) {
            c.s = c.s.copy(milestones = c.s.milestones + MILESTONE_CUSHION)
            c.skill(Skill.BANKER, 3)
            c.say("🎉 Подушка безопасности готова: ${Rules.CUSHION_GOAL} монет! Теперь трудные дни не страшны.")
        }
        return c.done()
    }

    fun withdrawCushion(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return fail(state, "Выбери, сколько монет забрать.")
        if (amount > state.cushion) return fail(state, "В подушке только ${state.cushion} монет.")
        val c = Ctx(state)
        c.s = c.s.copy(coins = c.s.coins + amount, cushion = c.s.cushion - amount, daySaved = c.s.daySaved - amount)
        c.say("Из подушки взято $amount монет. Помни: она нужна для трудных дней, а не для игрушек.")
        c.log("забрал из подушки $amount")
        return c.done()
    }

    // ───────────────────────── Банк: вклад и кредит ─────────────────────────

    /** Сколько банк начислит на вклад в конце дня. */
    fun dailyInterest(state: GameState): Int = state.deposit * Rules.DEPOSIT_PERCENT / 100

    /** Положить деньги на вклад. Пока есть долг, вклад открыть нельзя: сначала нужно вернуть кредит. */
    fun depositToBank(state: GameState, amount: Int): Outcome {
        if (state.debt > 0) {
            return fail(state, "Сначала верни кредит: держать долг и вклад одновременно невыгодно.")
        }
        if (amount < Rules.MIN_DEPOSIT) return fail(state, "На вклад кладут от ${Rules.MIN_DEPOSIT} монет.")
        if (amount > state.coins) return fail(state, "В кошельке только ${state.coins} монет.")
        val c = Ctx(state)
        c.s = c.s.copy(
            coins = c.s.coins - amount,
            deposit = c.s.deposit + amount,
            daySaved = c.s.daySaved + amount
        )
        c.say("🏦 На вклад положено $amount монет. Завтра банк добавит ${dailyInterest(c.s)}.")
        c.log("вклад +$amount")
        c.teach(Lesson.DEPOSIT)
        if (Medals.DEPOSIT_MADE !in c.s.milestones) {
            c.s = c.s.copy(milestones = c.s.milestones + Medals.DEPOSIT_MADE)
            c.skill(Skill.BANKER, 2)
        }
        return c.done()
    }

    /** Забрать деньги с вклада. Проценты за начатый день не начисляются: деньги должны полежать. */
    fun withdrawFromBank(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return fail(state, "Выбери, сколько монет забрать.")
        if (amount > state.deposit) return fail(state, "На вкладе только ${state.deposit} монет.")
        val c = Ctx(state)
        c.s = c.s.copy(
            coins = c.s.coins + amount,
            deposit = c.s.deposit - amount,
            daySaved = c.s.daySaved - amount
        )
        c.say("🏦 С вклада снято $amount монет. Проценты начисляются только за полный день.")
        c.log("снял с вклада $amount")
        return c.done()
    }

    fun takeLoan(state: GameState): Outcome {
        if (state.debt > 0) return fail(state, "У Монтика уже есть долг: ${state.debt} монет. Сначала верни его.")
        val c = Ctx(state)
        c.s = c.s.copy(coins = c.s.coins + Rules.LOAN_AMOUNT, debt = Rules.LOAN_REPAY)
        c.say(
            "💳 Банк дал ${Rules.LOAN_AMOUNT} монет. Вернуть нужно ${Rules.LOAN_REPAY}: " +
                "${Rules.LOAN_REPAY - Rules.LOAN_AMOUNT} монет — плата банку."
        )
        c.log("взял кредит ${Rules.LOAN_AMOUNT} (вернуть ${Rules.LOAN_REPAY})")
        c.teach(Lesson.CREDIT)
        return c.done()
    }

    /** Вернуть долг целиком или частями. */
    fun repayLoan(state: GameState, amount: Int): Outcome {
        if (state.debt <= 0) return fail(state, "Долга нет.")
        if (amount <= 0) return fail(state, "Выбери, сколько монет вернуть.")
        val pay = minOf(amount, state.debt, state.coins)
        if (pay <= 0) return fail(state, "В кошельке нет монет. Заработай на смене.")
        val c = Ctx(state)
        c.spend(pay)
        c.s = c.s.copy(debt = c.s.debt - pay)
        c.teach(Lesson.CREDIT)
        if (c.s.debt == 0) {
            c.say("💳 Кредит возвращён полностью! Теперь весь заработок снова твой.")
            c.log("вернул кредит")
            if (Medals.LOAN_REPAID !in c.s.milestones) {
                c.s = c.s.copy(milestones = c.s.milestones + Medals.LOAN_REPAID)
                c.skill(Skill.BANKER, 2)
            }
        } else {
            c.say("💳 Возвращено $pay монет. Осталось вернуть ${c.s.debt}.")
            c.log("вернул по кредиту $pay")
        }
        return c.done()
    }

    // ───────────────────────── Сценарии «ситуация → выбор» ─────────────────────────

    /** Можно ли выбрать этот вариант (хватает ли денег на цену). */
    fun canChoose(state: GameState, scenario: Scenario, choice: Choice): Boolean {
        val cost = maxOf(0, -choice.coins)
        val available = state.coins + if (scenario.coverFromCushion) state.cushion else 0
        return available >= cost
    }

    fun choose(state: GameState, scenarioId: String, index: Int): ChoiceResult {
        val scenario = Scenarios.byId(scenarioId)
            ?: return ChoiceResult(fail(state, "Такой ситуации нет."), null, null)
        val choice = scenario.choices.getOrNull(index)
            ?: return ChoiceResult(fail(state, "Такого варианта нет."), null, null)
        if (!canChoose(state, scenario, choice)) {
            return ChoiceResult(fail(state, "Не хватает монет для этого варианта."), choice, null)
        }
        if (scenarioId in state.doneScenarios && scenarioId != Scenarios.MISHAP) {
            return ChoiceResult(fail(state, "Эта ситуация уже пройдена."), choice, null)
        }

        val c = Ctx(state)
        if (choice.coins < 0) {
            val fromCushion = c.pay(-choice.coins, scenario.coverFromCushion)
            if (fromCushion > 0) {
                c.say("🛟 В кошельке не хватило монет — $fromCushion взято из подушки безопасности. Для этого она и нужна!")
            }
        }
        if (choice.loss > 0) c.pay(choice.loss, allowCushion = false)
        c.meters(choice.energy, choice.food, choice.water)
        c.skill(choice.skill, choice.skillPoints)
        if (choice.prepaidNight) c.s = c.s.copy(prepaidNight = true)
        scenario.lesson?.let { c.teach(it) }

        c.s = c.s.copy(doneScenarios = c.s.doneScenarios + scenario.id)
        if (c.s.pendingEvent == scenario.id) c.s = c.s.copy(pendingEvent = null)

        // Остановка путешествия.
        val trip = c.s.trip
        if (trip != null) {
            val dest = Destinations.byId(trip.destinationId)
            if (dest != null && dest.stops.getOrNull(trip.stopsDone) == scenario.id) {
                c.meters(-Rules.STOP_ENERGY, -Rules.STOP_FOOD, -Rules.STOP_WATER)
                val doneStops = trip.stopsDone + 1
                if (doneStops >= dest.stops.size) {
                    c.s = c.s.copy(trip = null, completedTrips = c.s.completedTrips + dest.id)
                    c.skill(Skill.TRAVELER, 3)
                    c.say("${dest.emoji} Путешествие в ${dest.name} завершено! Монтик вернулся домой с новыми знаниями.")
                    c.log("вернулся из путешествия: ${dest.name}")
                    if (dest.restful) {
                        c.s = c.s.copy(energy = 100, food = maxOf(c.s.food, 70), water = maxOf(c.s.water, 70))
                        c.say("🌊 Отдых у моря пошёл на пользу: Монтик вернулся полным сил и готов к новым делам!")
                        c.teach(Lesson.VACATION)
                    }
                } else {
                    c.s = c.s.copy(trip = TripProgress(dest.id, doneStops))
                }
            }
        }
        c.log("«${scenario.title}»: ${choice.text}")
        return ChoiceResult(c.done(), choice, choice.rating)
    }

    // ───────────────────────── Путешествия ─────────────────────────

    fun destinationStatus(state: GameState, dest: Destination): String? {
        if (dest.id in state.completedTrips) return "Уже побывали"
        if (state.trip != null) return "Сначала закончи текущее путешествие"
        val req = dest.requires
        if (req != null && req !in state.completedTrips) {
            val name = Destinations.byId(req)?.name ?: "предыдущий город"
            return "Сначала поездка: $name"
        }
        return null
    }

    fun startTrip(state: GameState, destId: String): Outcome {
        val dest = Destinations.byId(destId) ?: return fail(state, "Такого города нет.")
        destinationStatus(state, dest)?.let { return fail(state, it) }
        if (state.coins < dest.ticket) {
            return fail(state, "На билет не хватает монет: нужно ${dest.ticket}, а есть ${state.coins}.")
        }
        val c = Ctx(state)
        c.spend(dest.ticket)
        c.s = c.s.copy(trip = TripProgress(dest.id, 0))
        c.say("🚂 Билет в ${dest.name} куплен за ${dest.ticket} монет. Поехали!")
        c.log("поехал: ${dest.name} (билет −${dest.ticket})")
        c.teach(Lesson.BUDGET)
        return c.done()
    }

    /** Текущее задание путешествия (null, если Монтик дома). */
    fun currentStop(state: GameState): Scenario? {
        val trip = state.trip ?: return null
        val dest = Destinations.byId(trip.destinationId) ?: return null
        return dest.stops.getOrNull(trip.stopsDone)?.let { Scenarios.byId(it) }
    }

    // ───────────────────────── Общее ─────────────────────────

    fun pendingScenario(state: GameState): Scenario? {
        if (!state.introDone) return Scenarios.byId(Scenarios.INTRO)
        return state.pendingEvent?.let { Scenarios.byId(it) }
    }

    /** Отметить, что рисунок готов: игра начинается. */
    fun markCreated(state: GameState): GameState = state.copy(created = true)

    /** Цель игры на сейчас — для главного экрана и родителя. */
    fun currentGoal(state: GameState): String {
        if (state.debt > 0) return "💳 Вернуть банку ${state.debt} монет"
        state.trip?.let { t ->
            val d = Destinations.byId(t.destinationId)
            if (d != null) return "${d.emoji} Путешествие: ${d.name} (остановка ${t.stopsDone + 1} из ${d.stops.size})"
        }
        val next = Destinations.all.firstOrNull { it.id !in state.completedTrips }
            ?: return "🏆 Монтик объехал всю страну и отдохнул у моря. Ты справился!"
        return "${next.emoji} Накопить на билет в ${next.name}: ${state.coins} из ${next.ticket} монет"
    }
}
