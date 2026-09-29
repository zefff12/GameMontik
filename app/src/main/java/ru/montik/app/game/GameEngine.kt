package ru.montik.app.game

import kotlin.random.Random

/** Числа, на которых держится экономика игры. */
object Rules {
    const val START_COINS = 50
    const val TAX_PERCENT = 13
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
    const val EVENT_GAP_DAYS = 2
    const val EVENT_CHANCE_PERCENT = 45

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

    /** Виртуальное время: одна настоящая минута = столько минут в жизни Монтика (сутки — около 10 минут). */
    const val VIRTUAL_PER_REAL = 150

    /** Квартира: платёж каждые 15 дней (15-е и 30-е число игрового месяца), напоминание — за 5 дней. */
    const val RENT_PERIOD_DAYS = 15
    const val RENT_NOTICE_DAYS = 5
    const val MONTH_DAYS = 30

    /** Игровой период — 5 дней: в начале план бюджета, в конце итоги и звёзды привычек. */
    const val PERIOD_DAYS = 5

    /** Примерная трата на еду и воду в день — подсказка для плана. */
    const val FOOD_PER_DAY = 20

    /** Настроение: начальное и сколько уходит за ночь. */
    const val START_MOOD = 70
    const val NIGHT_MOOD = 6

    /** Реклама появляется раз в столько игровых дней. */
    const val AD_EVERY_DAYS = 3

    const val MAX_PURCHASES = 40
    const val MAX_PERIODS = 8

    /** Игра начинается в 07:00 первого дня (минут от полуночи). */
    const val START_CLOCK_MINUTES = 420

    /** Будильник звонит в 07:00, а вечером Монтик ложится в 21:00. */
    const val WAKE_MINUTES = 420
    const val BEDTIME_MINUTES = 1260

    /** Сколько нужно проспать: 8 часов и больше — «выспался», меньше 6 часов — «не выспался». */
    const val GOOD_SLEEP_MINUTES = 480
    const val OK_SLEEP_MINUTES = 360

    /** «Отложить»: будильник звонит снова через 15 виртуальных минут. */
    const val SNOOZE_MINUTES = 15

    /** Одна смена занимает три виртуальных часа. */
    const val SHIFT_VIRTUAL_MINUTES = 180

    /** Сколько сил забирает смена: выспавшемуся — меньше, невыспавшемуся — больше. */
    const val RESTED_SHIFT_ENERGY = 30
    const val TIRED_SHIFT_ENERGY = 45
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
    val notes: List<String>,
    /** Звёзды за смену в магазине (1–3, null — обычная работа) и побит ли личный рекорд. */
    val stars: Int? = null,
    val record: Boolean = false,
    /** Прибавка за опыт бизнес-конференций ([Travel]), в процентах. */
    val careerPercent: Int = 0
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

    internal class Ctx(var s: GameState) {
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
            s = s.copy(
                coins = s.coins + n,
                dayEarned = s.dayEarned + n,
                totalEarned = s.totalEarned + n,
                pEarned = s.pEarned + n
            )
        }

        /**
         * Записывает трату в факт периода и историю покупок (без движения денег):
         * [kind] — в какую строку плана она попадёт, [label] — что куплено.
         */
        fun countSpend(n: Int, kind: SpendKind, label: String?) {
            if (n <= 0) return
            s = s.copy(
                daySpent = s.daySpent + n,
                totalSpent = s.totalSpent + n,
                pNeeds = s.pNeeds + if (kind == SpendKind.NEED) n else 0,
                pWants = s.pWants + if (kind == SpendKind.WANT) n else 0
            )
            if (label != null) {
                val line = "День ${s.day}: $label −$n (${kind.title.lowercase()})"
                s = s.copy(purchases = (s.purchases + line).takeLast(Rules.MAX_PURCHASES))
            }
        }

        /** Списывает [n] монет из кошелька. Вызывающий обязан убедиться, что денег хватает. */
        fun spend(n: Int, kind: SpendKind, label: String? = null) {
            s = s.copy(coins = s.coins - n)
            countSpend(n, kind, label)
        }

        /**
         * Платит [n] монет: сначала из кошелька, при [allowCushion] — остаток из подушки безопасности.
         * Если денег не хватает, берётся сколько есть. Возвращает, сколько взято из подушки.
         */
        fun pay(n: Int, allowCushion: Boolean, kind: SpendKind, label: String? = null): Int {
            val fromWallet = minOf(s.coins, n)
            val rest = n - fromWallet
            val fromCushion = if (allowCushion) minOf(rest, s.cushion) else 0
            s = s.copy(
                coins = s.coins - fromWallet,
                cushion = s.cushion - fromCushion,
                pSaved = s.pSaved - fromCushion
            )
            countSpend(fromWallet + fromCushion, kind, label)
            return fromCushion
        }

        /** Меняет настроение и запоминает причину — её покажут ребёнку. */
        /** Опыт Монтика: шкала уровня из макета. Новый уровень поздравляется отдельно (Progress.levelUpPending). */
        fun xp(n: Int) {
            if (n <= 0) return
            val before = Progress.levelOf(s.xp)
            s = s.copy(xp = s.xp + n)
            val after = Progress.levelOf(s.xp)
            if (after > before) log("новый уровень Монтика: $after")
        }

        /** Опыт за накопления — не чаще раза в игровой день, чтобы его нельзя было накрутить. */
        fun xpForSaving() {
            if (s.xpSaveDay >= s.day) return
            s = s.copy(xpSaveDay = s.day)
            xp(Progress.XP_SAVE_DAY)
        }

        fun mood(delta: Int, why: String) {
            s = s.copy(mood = (s.mood + delta).coerceIn(0, 100), moodWhy = why)
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
            // 20 000 монет накоплено: Монтик задумывается о своём деле. Метка остаётся навсегда.
            if (BusinessEngine.wealth(s) >= BusinessEngine.GOAL_COINS && BusinessEngine.WEALTH_MARK !in s.milestones) {
                s = s.copy(milestones = s.milestones + BusinessEngine.WEALTH_MARK)
                say("🏬 У Монтика накопилось ${BusinessEngine.GOAL_COINS} монет! Пора подумать о собственном магазине.")
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

    internal fun fail(state: GameState, text: String) = Outcome(state, listOf(text), emptyList(), ok = false)

    // ───────────────────────── Магазин ─────────────────────────

    fun buyFood(state: GameState, foodId: String): Outcome {
        val item = Catalog.foodItem(foodId) ?: return fail(state, "Такого товара нет.")
        if (state.coins < item.price) return fail(state, "Не хватает монет: нужно ${item.price}, а есть ${state.coins}.")
        val c = Ctx(state)
        c.spend(item.price, SpendKind.NEED, item.name)
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
        c.spend(item.price, Catalog.kindOf(item), item.name)
        if (Catalog.kindOf(item) == SpendKind.WANT) c.mood(8, "Обновка радует Монтика.")
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

    /** Почему купить не получится (null — хватает): объясняем, чего не хватает и что можно сделать. */
    fun cannotAfford(state: GameState, price: Int): String? {
        if (state.coins >= price) return null
        val lack = price - state.coins
        val ways = buildList {
            add("поработать на смене")
            if (state.piggy > 0) add("взять из копилки (но цель отодвинется)")
            add("выбрать что-то дешевле или отложить покупку")
        }
        return "Не хватает $lack монет: в кошельке ${state.coins}, а нужно $price. Можно ${ways.joinToString(", ")}. В минус покупать нельзя."
    }

    /** Радость — желаемая покупка: настроение растёт, еды почти не прибавляет. */
    fun buyJoy(state: GameState, joyId: String): Outcome {
        val item = Catalog.joy(joyId) ?: return fail(state, "Такого товара нет.")
        cannotAfford(state, item.price)?.let { return fail(state, it) }
        val c = Ctx(state)
        c.spend(item.price, SpendKind.WANT, item.name)
        c.meters(food = item.food)
        c.s = c.s.copy(joys = c.s.joys + (item.id to (c.s.joys[item.id] ?: 0) + 1))
        c.mood(item.mood, "${item.emoji} ${item.name} порадовал(а) Монтика.")
        c.say("${item.emoji} ${item.name} за ${item.price} монет. Настроение +${item.mood}! Это желаемая трата.")
        c.log("радость: ${item.name.lowercase()} (−${item.price})")
        c.teach(Lesson.WANT_NEED)
        return c.done()
    }

    /** Поесть на кухне: еда покупается и сразу съедается. */
    fun eatMeal(state: GameState, mealId: String): Outcome {
        val meal = Catalog.meal(mealId) ?: return fail(state, "Такой еды нет.")
        if (state.food >= 100) return fail(state, "Монтик сыт — сейчас есть не хочется.")
        cannotAfford(state, meal.price)?.let { return fail(state, it) }
        val c = Ctx(state)
        c.spend(meal.price, SpendKind.NEED, meal.name)
        c.meters(food = meal.food)
        if (meal.mood > 0) c.mood(meal.mood, "Вкусная еда подняла настроение.")
        c.say("${meal.emoji} ${meal.name}: −${meal.price} монет, сытость +${meal.food}.")
        c.log("поел: ${meal.name.lowercase()} (−${meal.price})")
        c.teach(Lesson.NEEDS)
        return c.done()
    }

    /** «Не есть»: деньги остаются, но и сытость не растёт. */
    fun skipMeal(state: GameState): Outcome {
        val c = Ctx(state)
        c.say(
            if (state.food < 30) "🍽 Монтик решил не есть. Монеты сэкономлены, но он голоден: еда — обязательная трата."
            else "🍽 Монтик пока не голоден — можно поесть позже."
        )
        return c.done()
    }

    /** Знакомство «три решения» просмотрено. */
    fun markHelpSeen(state: GameState): GameState = state.copy(helpSeen = true)

    /** Итоги периода просмотрены. */
    fun markPeriodSeen(state: GameState): GameState =
        state.copy(periodSeen = state.periods.lastOrNull()?.period ?: state.periodSeen)

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

    /** Строка в расчётный листок: откуда прибавка за опыт конференций. */
    fun careerNote(state: GameState): String =
        "Опыт бизнес-конференций (уровень профессионала ${Travel.careerLevel(state)}): +${Travel.careerPercent(state)}% к заработку."

    /**
     * Почему Монтик прямо сейчас не может выйти на смену (null — может).
     * Силы, еда и вода обязательны: без них не работают и не живут.
     */
    fun workBlocker(state: GameState): String? = when {
        state.sleep != null -> "😴 Монтик спит. Работать можно, когда он проснётся."
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
        val career = Travel.careerPercent(state)
        val total = eff + appearance + bonus + career
        val gross = (job.base * total + 50) / 100
        val tax = (gross * Rules.TAX_PERCENT + 50) / 100
        val net = gross - tax

        val notes = mutableListOf<String>()
        if (eff < 100) notes += "Из-за усталости, голода или жажды Монтик работал не в полную силу ($eff%)."
        if (appearance > 0) notes += "Опрятный вид добавил +$appearance% к заработку."
        if (bonus > 0) notes += "Задание решено верно: премия +$bonus%."
        if (taskCorrect == false) notes += "Задание решено неверно — премии нет."
        if (career > 0) notes += careerNote(state)

        c.earn(net)
        c.s = c.s.copy(shifts = c.s.shifts + 1, totalTax = c.s.totalTax + tax)
        c.meters(energy = -shiftEnergy(state), food = -Rules.SHIFT_FOOD, water = -Rules.SHIFT_WATER)
        // Смена занимает несколько виртуальных часов: день движется вперёд, и вечер наступает быстрее.
        c.s = c.s.copy(clockMinutes = c.s.clockMinutes + Rules.SHIFT_VIRTUAL_MINUTES)
        if (state.sleepQuality == SleepQuality.TIRED) notes += "Монтик не выспался и устал быстрее обычного: смена отняла ${shiftEnergy(state)} сил."
        if (state.sleepQuality == SleepQuality.RESTED) notes += "Монтик выспался: смена отняла всего ${shiftEnergy(state)} сил."
        if (taskCorrect == true) {
            c.skill(Skill.CREATOR, 1)
            c.s = c.s.copy(tasksCorrect = c.s.tasksCorrect + 1)
        }
        c.log("смена «${job.title}»: начислено $gross, налог $tax, на руки $net")

        c.teach(Lesson.WORK)
        c.teach(Lesson.TAX)
        if (job == Job.SELLER) c.teach(Lesson.CHANGE)
        if (lowEnergy || eff < 100) c.teach(Lesson.ENERGY)

        c.xp(Progress.XP_SHIFT)
        val slip = Payslip(job.title, job.base, eff, appearance, bonus, gross, tax, net, notes, careerPercent = career)
        return ShiftResult(c.done(), slip)
    }

    /**
     * Смена в магазине. [performance] — оценка мини-игры 0..100 ([ShopWork]); от неё зависит премия до +50%.
     * Ставка растёт с должностью, налог 13% удерживается, как на любой другой работе.
     */
    fun workShop(state: GameState, game: ShopGame, performance: Int, details: List<String> = emptyList()): ShiftResult {
        if (!ShopWork.unlocked(state)) {
            return ShiftResult(fail(state, "Магазин пока недоступен: ${ShopWork.lockHint(state)}"), null)
        }
        workBlocker(state)?.let { return ShiftResult(fail(state, it), null) }

        val c = Ctx(state)
        val rank = ShopWork.rank(state)
        val lowEnergy = state.energy < 60
        val eff = efficiency(state)
        val appearance = state.appearancePercent
        val perf = performance.coerceIn(0, 100)
        val bonus = perf / 2
        val career = Travel.careerPercent(state)
        val total = eff + appearance + bonus + career
        val gross = (rank.base * total + 50) / 100
        val tax = (gross * Rules.TAX_PERCENT + 50) / 100
        val net = gross - tax

        val notes = mutableListOf<String>()
        notes += details
        notes += ShopWork.performanceNote(perf)
        if (bonus > 0) notes += "Премия за внимательность и скорость: +$bonus%."
        if (eff < 100) notes += "Из-за усталости, голода или жажды Монтик работал не в полную силу ($eff%)."
        if (appearance > 0) notes += "Опрятный вид добавил +$appearance% к заработку."
        if (career > 0) notes += careerNote(state)

        c.earn(net)
        c.s = c.s.copy(
            shifts = c.s.shifts + 1,
            shopShifts = c.s.shopShifts + 1,
            totalTax = c.s.totalTax + tax
        )
        c.meters(energy = -shiftEnergy(state), food = -Rules.SHIFT_FOOD, water = -Rules.SHIFT_WATER)
        c.s = c.s.copy(clockMinutes = c.s.clockMinutes + Rules.SHIFT_VIRTUAL_MINUTES)
        if (state.sleepQuality == SleepQuality.TIRED) notes += "Монтик не выспался и устал быстрее обычного: смена отняла ${shiftEnergy(state)} сил."
        if (state.sleepQuality == SleepQuality.RESTED) notes += "Монтик выспался: смена отняла всего ${shiftEnergy(state)} сил."

        c.skill(Skill.CREATOR, if (perf >= 80) 2 else 1)
        if (perf >= 70) c.s = c.s.copy(tasksCorrect = c.s.tasksCorrect + 1)
        c.log("смена в магазине («${game.title}»): начислено $gross, налог $tax, на руки $net")

        // Повышение в должности.
        val newRank = ShopWork.rank(c.s)
        if (newRank != rank) {
            c.say("🏅 Монтика повысили: теперь он ${newRank.title.lowercase()}! Ставка за смену — ${newRank.base} монет.")
            c.log("повышение: ${newRank.title}")
        }

        c.teach(Lesson.WORK)
        c.teach(Lesson.TAX)
        if (game == ShopGame.CASHIER && rank.withChange) c.teach(Lesson.CHANGE)
        if (lowEnergy || eff < 100) c.teach(Lesson.ENERGY)

        // Звёзды за смену и личный рекорд по этой мини-игре.
        val stars = ShopWork.stars(perf)
        val bestBefore = state.shopBest[game.name]
        val record = bestBefore != null && perf > bestBefore
        if (bestBefore == null || perf > bestBefore) c.s = c.s.copy(shopBest = c.s.shopBest + (game.name to perf))
        c.xp(Progress.XP_SHIFT + stars * Progress.XP_SHOP_STAR)
        val slip = Payslip(
            "${rank.title}: ${game.title.lowercase()}", rank.base, eff, appearance, bonus, gross, tax, net, notes,
            stars = stars, record = record, careerPercent = career
        )
        return ShiftResult(c.done(), slip)
    }

    // ───────────────────────── Сон, конец дня, дневник ─────────────────────────

    /** Запускает виртуальные часы при первом запуске (опорная точка — настоящее время [realNowMs]). */
    fun startClock(state: GameState, realNowMs: Long): GameState =
        if (state.clockStamp <= 0L) state.copy(clockStamp = realNowMs) else state

    /** Что происходит со сном сейчас (null — Монтик не спит). */
    fun sleepStatus(state: GameState, realNowMs: Long): SleepStatus? {
        val ses = state.sleep ?: return null
        val now = VirtualClock.now(state, realNowMs)
        val slept = (now - ses.startV).coerceAtLeast(0L).toInt()
        val remaining = (ses.alarmV - now).coerceAtLeast(0L).toInt()
        val total = maxOf((ses.alarmV - ses.startV).toInt(), slept, 1)
        return SleepStatus(
            virtualNow = now,
            sleptMinutes = slept,
            remainingMinutes = remaining,
            totalMinutes = total,
            ringing = now >= ses.alarmV,
            alarmTimeOfDay = VirtualClock.timeOfDay(ses.alarmV)
        )
    }

    /** Когда Монтик ляжет и когда прозвенит будильник, если уложить его прямо сейчас. */
    fun planSleep(state: GameState, realNowMs: Long): SleepPlan =
        VirtualClock.planSleep(VirtualClock.now(state, realNowMs))

    /** Сколько сил забирает одна смена: выспавшийся Монтик тратит меньше, невыспавшийся — больше. */
    fun shiftEnergy(state: GameState): Int = when (state.sleepQuality) {
        SleepQuality.RESTED -> Rules.RESTED_SHIFT_ENERGY
        SleepQuality.NORMAL -> Rules.SHIFT_ENERGY
        SleepQuality.TIRED -> Rules.TIRED_SHIFT_ENERGY
    }

    /**
     * Где Монтик спит. Он снимает квартиру, поэтому ночь дома бесплатная: за жильё платят
     * дважды в месяц (см. [Life.settleRent]). Скамейка осталась только для старых сохранений.
     */
    private fun bedFor(place: SleepPlace): SleepPlace = place

    /**
     * Уложить Монтика спать. Если сейчас день, он ложится вечером (в 21:00); будильник — на ближайшие 07:00.
     * Сон идёт по виртуальным часам и продолжается, даже когда игра закрыта.
     */
    fun beginSleep(state: GameState, place: SleepPlace, realNowMs: Long): Outcome {
        if (state.sleep != null) return fail(state, "Монтик уже спит.")
        val c = Ctx(state)
        c.teach(Lesson.ENERGY)
        val used = bedFor(place)

        // Ночью Монтик тратит еду и воду.
        c.meters(food = -Rules.NIGHT_FOOD, water = -Rules.NIGHT_WATER)

        val plan = VirtualClock.planSleep(VirtualClock.now(c.s, realNowMs))
        c.s = c.s.copy(
            sleep = SleepSession(plan.bedV, plan.alarmV, used),
            clockMinutes = plan.bedV,
            clockStamp = realNowMs
        )
        c.log("лёг спать в ${VirtualClock.formatV(plan.bedV)}, будильник на ${VirtualClock.formatV(plan.alarmV)}")
        return c.done()
    }

    /** «Отложить»: Монтик спит дальше, будильник зазвонит через [Rules.SNOOZE_MINUTES] виртуальных минут. */
    fun snooze(state: GameState, realNowMs: Long): Outcome {
        val ses = state.sleep ?: return fail(state, "Монтик сейчас не спит.")
        val now = VirtualClock.now(state, realNowMs)
        if (now < ses.alarmV) return fail(state, "Будильник ещё не звонил.")
        val next = ses.copy(alarmV = now + Rules.SNOOZE_MINUTES, snoozes = ses.snoozes + 1)
        return Outcome(state.copy(sleep = next))
    }

    /** Разбудить Монтика (выключить будильник или встать раньше): считаем, сколько он проспал, и начинаем день. */
    fun wakeUp(state: GameState, realNowMs: Long): Outcome =
        finishSleep(state, VirtualClock.now(state, realNowMs), realNowMs)

    /** Мгновенная ночь: сразу до будильника. Нужна тестам и «перемотке» — в игре сон идёт по часам. */
    fun sleep(state: GameState, place: SleepPlace): Outcome {
        val begun = beginSleep(state, place, state.clockStamp)
        if (!begun.ok) return begun
        val ses = begun.state.sleep ?: return begun
        val woke = finishSleep(begun.state, ses.alarmV, state.clockStamp)
        return Outcome(
            woke.state,
            begun.messages + woke.messages,
            begun.lessons + woke.lessons,
            woke.ok,
            begun.medals + woke.medals
        )
    }

    private fun finishSleep(state: GameState, wakeV: Long, realNowMs: Long): Outcome {
        val ses = state.sleep ?: return fail(state, "Монтик сейчас не спит.")
        val c = Ctx(state)
        val slept = (wakeV - ses.startV).coerceAtLeast(0L).toInt()
        val bench = ses.place == SleepPlace.BENCH

        // Сколько сил вернул сон: чем дольше спал, тем больше; на скамейке — не выше потолка.
        val ceiling = if (bench) Rules.BENCH_ENERGY else 100
        val gained = ceiling * minOf(slept, Rules.GOOD_SLEEP_MINUTES) / Rules.GOOD_SLEEP_MINUTES
        var energy = maxOf(c.s.energy, gained)

        val quality = when {
            slept < Rules.OK_SLEEP_MINUTES -> SleepQuality.TIRED
            slept >= Rules.GOOD_SLEEP_MINUTES && !bench -> SleepQuality.RESTED
            else -> SleepQuality.NORMAL
        }
        val time = VirtualClock.duration(slept)
        when (quality) {
            SleepQuality.RESTED ->
                c.say("🌞 Монтик проспал $time и отлично выспался! Сил хватит надолго: смена отнимет всего ${Rules.RESTED_SHIFT_ENERGY}.")
            SleepQuality.TIRED ->
                c.say("🥱 Монтик проспал только $time и не выспался. Он будет уставать быстрее: смена отнимет ${Rules.TIRED_SHIFT_ENERGY} сил.")
            SleepQuality.NORMAL ->
                c.say("😌 Монтик проспал $time.")
        }
        if (bench && slept >= Rules.OK_SLEEP_MINUTES) {
            c.say("🪑 На скамейке спится плохо: сил восстановилось только на ${Rules.BENCH_ENERGY}%. Усталость снижает заработок.")
        }
        if (c.s.food == 0 || c.s.water == 0) {
            energy -= 25
            c.s = c.s.copy(pHungry = c.s.pHungry + 1)
            c.say("🍲 Монтик лёг спать голодным или без воды и отдохнул плохо (−25 сил). Еда и вода нужны каждый день.")
        }
        c.s = c.s.copy(
            energy = energy.coerceIn(0, 100),
            sleep = null,
            sleepQuality = quality,
            clockMinutes = wakeV,
            clockStamp = realNowMs
        )
        endNight(c)
        return c.done()
    }

    /** Итоги ночи: дневник, проценты по вкладу, износ одежды, новый день и возможное событие. */
    private fun endNight(c: Ctx) {
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
        val oldDay = c.s.day
        val newDay = oldDay + 1
        c.s = c.s.copy(
            day = newDay,
            owned = owned,
            worn = worn,
            diary = diary,
            dayEarned = 0,
            daySpent = 0,
            daySaved = 0
        )

        // Настроение понемногу снижается: радость от покупок не вечная.
        if (c.s.mood > 0) {
            c.s = c.s.copy(mood = (c.s.mood - Rules.NIGHT_MOOD).coerceAtLeast(0))
            if (c.s.mood < 30) c.s = c.s.copy(moodWhy = "Монтику немного скучно. Небольшая радость из плана «желаемое» поднимет настроение.")
        }

        // Квартира: день оплаты или напоминание.
        Life.settleRent(c)

        // Кредиты: штраф за просрочку и напоминание о сроке.
        Credits.settleDay(c)

        // Конец игрового периода: план и факт, звёзды привычек.
        Life.closePeriodIfNeeded(c, oldDay)

        // Раз в три дня — рекламное предложение.
        Ads.schedule(c)

        // Свой магазин: день торговли, расходы, отчёт.
        BusinessEngine.settleDay(c)

        // Возможное непредвиденное событие.
        val rnd = Random(c.s.seed * 7_919L + newDay)
        if (newDay >= Rules.EVENT_MIN_DAY &&
            newDay - c.s.lastEventDay >= Rules.EVENT_GAP_DAYS &&
            c.s.pendingEvent == null &&
            rnd.nextInt(100) < Rules.EVENT_CHANCE_PERCENT
        ) {
            // Событие дня: иногда неприятность, чаще — доброе (соседка, находка, подарок, скидка).
            val event = Scenarios.EVENTS[rnd.nextInt(Scenarios.EVENTS.size)]
            c.s = c.s.copy(pendingEvent = event, lastEventDay = newDay)
        }
    }

    // ───────────────────────── Подушка безопасности ─────────────────────────

    fun depositCushion(state: GameState, amount: Int): Outcome {
        if (amount <= 0) return fail(state, "Выбери, сколько монет отложить.")
        if (amount > state.coins) return fail(state, "В кошельке только ${state.coins} монет.")
        val c = Ctx(state)
        c.s = c.s.copy(
            coins = c.s.coins - amount,
            cushion = c.s.cushion + amount,
            daySaved = c.s.daySaved + amount,
            pSaved = c.s.pSaved + amount
        )
        c.say("🛟 Отложено $amount монет. В подушке: ${c.s.cushion}.")
        c.xpForSaving()
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
        c.s = c.s.copy(
            coins = c.s.coins + amount,
            cushion = c.s.cushion - amount,
            daySaved = c.s.daySaved - amount,
            pSaved = c.s.pSaved - amount
        )
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
            daySaved = c.s.daySaved + amount,
            pSaved = c.s.pSaved + amount
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
            daySaved = c.s.daySaved - amount,
            pSaved = c.s.pSaved - amount
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
        c.spend(pay, SpendKind.NEED, "возврат кредита")
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
        // Остановку путешествия можно пройти снова, если Монтик опять полетел в этот город.
        val tripStop = currentStop(state)?.id == scenarioId
        if (scenarioId in state.doneScenarios && scenarioId !in Scenarios.REPEATABLE && !tripStop) {
            return ChoiceResult(fail(state, "Эта ситуация уже пройдена."), choice, null)
        }

        val c = Ctx(state)
        val kind = if (choice.need || scenario.coverFromCushion) SpendKind.NEED else SpendKind.WANT
        // Доход из события (соседка заплатила, подарок): в кошелёк.
        if (choice.coins > 0) c.earn(choice.coins)
        if (choice.coins < 0) {
            val fromCushion = c.pay(-choice.coins, scenario.coverFromCushion, kind, scenario.title)
            if (fromCushion > 0) {
                c.say("🛟 В кошельке не хватило монет — $fromCushion взято из подушки безопасности. Для этого она и нужна!")
            }
        }
        if (choice.loss > 0) {
            // Потерять можно только то, что есть в кошельке: честно говорим, сколько пропало на самом деле.
            val lost = minOf(choice.loss, c.s.coins)
            c.pay(choice.loss, allowCushion = false, kind = SpendKind.NEED, label = "потеря: ${scenario.title}")
            c.say(
                when {
                    lost <= 0 -> "👛 В кошельке было пусто — терять было нечего. Но урок тот же!"
                    lost < choice.loss -> "👛 Пропало $lost монет — всё, что было в кошельке."
                    else -> "👛 Пропало $lost монет."
                }
            )
        }
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
                    // Все остановки пройдены — впереди бизнес-конференция, потом перелёт домой ([Travel]).
                    c.s = c.s.copy(trip = trip.copy(stopsDone = doneStops, phase = TripPhase.CONFERENCE))
                    c.say("🎤 Прогулка по городу ${dest.name} окончена. Впереди — бизнес-конференция «${dest.conference}»!")
                } else {
                    c.s = c.s.copy(trip = trip.copy(stopsDone = doneStops))
                }
            }
        }
        if (choice.mood != 0) c.mood(choice.mood, choice.outcome)
        if (choice.save > 0) {
            // «Отложить»: в копилку, если цель выбрана, иначе — в подушку безопасности.
            if (c.s.goalId != null) {
                c.s = c.s.copy(
                    piggy = c.s.piggy + choice.save,
                    piggyDeposited = c.s.piggyDeposited + choice.save,
                    piggyFirstDay = if (c.s.piggyFirstDay <= 0) c.s.day else c.s.piggyFirstDay
                )
                c.say("🐷 ${choice.save} монет — в копилку на цель.")
            } else {
                c.s = c.s.copy(cushion = c.s.cushion + choice.save)
                c.say("🛟 ${choice.save} монет — в подушку безопасности.")
            }
            c.s = c.s.copy(daySaved = c.s.daySaved + choice.save, pSaved = c.s.pSaved + choice.save)
        }
        if (scenario.id != Scenarios.INTRO) {
            c.xp(if (choice.rating == Rating.GREAT) Progress.XP_GOOD_CHOICE else if (choice.rating == Rating.OK) 5 else 0)
        }
        c.log("«${scenario.title}»: ${choice.text}")
        return ChoiceResult(c.done(), choice, choice.rating)
    }

    // ───────────────────────── Путешествия ─────────────────────────

    fun destinationStatus(state: GameState, dest: Destination): String? {
        // В город, где уже побывали, можно полететь снова: новая конференция — новый опыт.
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
        c.spend(dest.ticket, SpendKind.WANT, "билет: ${dest.name}")
        c.s = c.s.copy(trip = TripProgress(dest.id, 0, TripPhase.FLY_OUT))
        c.say("✈️ Билет на самолёт в ${dest.name} куплен за ${dest.ticket} монет. Полетели!")
        c.log("поехал: ${dest.name} (билет −${dest.ticket})")
        c.teach(Lesson.BUDGET)
        return c.done()
    }

    /** Текущее задание путешествия (null, если Монтик дома). */
    fun currentStop(state: GameState): Scenario? {
        val trip = state.trip ?: return null
        if (trip.phase != TripPhase.CITY) return null
        val dest = Destinations.byId(trip.destinationId) ?: return null
        return dest.stops.getOrNull(trip.stopsDone)?.let { Scenarios.byId(it) }
    }

    // ───────────────────────── Общее ─────────────────────────

    fun pendingScenario(state: GameState): Scenario? {
        if (!state.introDone) return Scenarios.byId(Scenarios.INTRO)
        return state.pendingEvent?.let { Scenarios.byId(it) }
    }

    // ───────────────────────── Демонстрационный режим ─────────────────────────

    /** Включить или выключить демо-режим (для экспертной проверки). */
    fun setDemo(state: GameState, on: Boolean): GameState = state.copy(demo = on)

    /**
     * Демо-режим: перемотка к началу следующего периода. Ночи проходят по обычным правилам
     * (еда и вода тратятся, квартира оплачивается, начисляются проценты), просто без ожидания.
     */
    fun skipToNextPeriod(state: GameState): Outcome {
        if (!state.demo) return fail(state, "Перемотка доступна только в демо-режиме.")
        if (state.sleep != null) return fail(state, "Монтик спит — дождись утра или разбуди его.")
        val target = state.period + 1
        var s = state
        val messages = mutableListOf<String>()
        val lessons = mutableListOf<Lesson>()
        val medals = mutableListOf<Medal>()
        var guard = 0
        while (s.period < target && guard < Rules.PERIOD_DAYS + 1) {
            val night = sleep(s, SleepPlace.CABIN)
            if (!night.ok) break
            s = night.state
            messages += night.messages.filter { it.startsWith("🏠") || it.startsWith("📊") || it.startsWith("🏦") }
            lessons += night.lessons
            medals += night.medals
            guard++
        }
        messages += "⏩ Демо: перемотано к периоду ${s.period} (день ${s.day})."
        return Outcome(s, messages.distinct(), lessons.distinct(), true, medals.distinct())
    }

    /** Отметить, что рисунок готов: игра начинается. */
    fun markCreated(state: GameState): GameState = state.copy(created = true)

    /** Цель игры на сейчас — для главного экрана и родителя. */
    fun currentGoal(state: GameState): String {
        if (state.debt > 0) return "💳 Вернуть банку ${state.debt} монет"
        state.credits.firstOrNull { Credits.isOverdue(state, it) }?.let {
            return "💳 Срочно вернуть просроченный кредит: ${it.left} монет"
        }
        if (state.rentDebt > 0) return "🏠 Погасить долг за квартиру: ${state.rentDebt} монет"
        Goals.current(state)?.let { g ->
            return "${g.emoji} Цель: ${g.title} — в копилке ${state.piggy} из ${g.cost}"
        }
        state.trip?.let { t ->
            val d = Destinations.byId(t.destinationId)
            if (d != null) return "${d.emoji} Путешествие: ${d.name} — " + when (t.phase) {
                TripPhase.FLY_OUT -> "летим туда"
                TripPhase.CITY -> "остановка ${t.stopsDone + 1} из ${d.stops.size}"
                TripPhase.CONFERENCE -> "бизнес-конференция"
                TripPhase.FLY_HOME -> "летим домой"
            }
        }
        val next = Destinations.all.firstOrNull { it.id !in state.completedTrips }
            ?: return "🏆 Монтик объехал всю страну и отдохнул у моря. Ты справился! " + BusinessEngine.goal(state)
        return "${next.emoji} Накопить на билет в ${next.name}: ${state.coins} из ${next.ticket} монет"
    }
}
