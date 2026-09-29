package ru.montik.app.game

import kotlin.random.Random

/*
 * Работа в банке — «Проверяй деньги!» (кадры макета Frame 27–29).
 * Слева на столе лежит образец купюры, справа — купюра клиента. Нужно внимательно сравнить их
 * и решить: купюра нормальная (зелёная кнопка ✓) или дефектная (красная ✗) — как в игре,
 * где сравниваешь гостя с его фото и ищешь, что не так. Чем внимательнее и быстрее — тем выше премия.
 * Экраны — ui/BankWorkUi.kt, здесь только правила.
 */

/** Что может быть не так с купюрой клиента. [art] — имя картинки купюры (fg_bank_note_<art>). */
enum class NoteDefect(val art: String, val title: String, val explain: String) {
    NONE("ok", "Нормальная", "Купюра такая же, как образец."),
    SCRIBBLE("scribble", "Изрисована", "На купюре рисунки ручкой — её нужно отправить на замену."),
    TEAR("tear", "Оторван угол", "У купюры оторван кусок — такую не выдают клиентам."),
    STAIN("stain", "Пятно", "На купюре большое пятно — она испорчена."),
    HOLE("hole", "Дырка", "В купюре дырка — она повреждена."),
    COLOR("color", "Не тот цвет", "Цвет не совпадает с образцом — возможно, это подделка!"),
    FADED("faded", "Нет рисунка", "На месте рисунка с памятником — пустое пятно. Это подделка!");

    val defective: Boolean get() = this != NONE
}

/** Ответ игрока по одной купюре. */
data class NoteAnswer(val defect: NoteDefect, val saidDefective: Boolean) {
    val correct: Boolean get() = saidDefective == defect.defective
}

object BankWork {
    /** Банк открывается, когда Монтик отработал столько смен на любой работе. */
    const val UNLOCK_SHIFTS = 2
    const val NOTES_PER_SHIFT = 8
    const val BASE = 70
    const val KEY = "BANK"

    fun unlocked(state: GameState): Boolean = state.shifts >= UNLOCK_SHIFTS

    fun lockHint(state: GameState): String? {
        val left = UNLOCK_SHIFTS - state.shifts
        return if (left > 0) "ещё смен на любой работе: $left" else null
    }

    /** Ставка растёт с опытом: +10 монет за каждые 5 смен в банке, но не больше 150. */
    fun base(state: GameState): Int = minOf(150, BASE + 10 * (state.bankShifts / 5))

    /**
     * Купюры на смену: примерно половина — с дефектом, дефекты не повторяются подряд.
     * Для одного и того же состояния — одинаковые (так их можно проверить тестом).
     */
    fun notes(state: GameState): List<NoteDefect> {
        val rnd = Random(state.seed * 7_919L + state.bankShifts * 97L + state.day * 13L + 5L)
        val defects = NoteDefect.values().filter { it.defective }
        val bad = 3 + rnd.nextInt(3) // 3–5 дефектных из 8
        val list = MutableList(NOTES_PER_SHIFT) { NoteDefect.NONE }
        val badPlaces = (0 until NOTES_PER_SHIFT).shuffled(rnd).take(bad)
        var pool = defects.shuffled(rnd)
        for ((i, place) in badPlaces.withIndex()) {
            if (i > 0 && i % pool.size == 0) pool = defects.shuffled(rnd)
            list[place] = pool[i % pool.size]
        }
        return list
    }

    /** Разумное время на всю смену: около пяти секунд на купюру. */
    fun parMillis(count: Int): Long = 6_000L + count * 5_000L

    /** Оценка смены 0..100: доля верных ответов (главное) и скорость (до 30%). */
    fun performance(answers: List<NoteAnswer>, millis: Long): Int {
        if (answers.isEmpty()) return 0
        val accuracy = answers.count { it.correct }.toFloat() / answers.size
        val speed = ShopWork.speedFactor(millis, parMillis(answers.size))
        return (accuracy * (0.7f + 0.3f * speed) * 100).toInt().coerceIn(0, 100)
    }

    /** Смена в банке: зарплата, налог, усталость, голод и жажда — как на любой работе. */
    fun work(state: GameState, performance: Int, details: List<String> = emptyList()): ShiftResult {
        if (!unlocked(state)) return ShiftResult(GameEngine.fail(state, "Банк пока не берёт на работу: ${lockHint(state)}"), null)
        GameEngine.workBlocker(state)?.let { return ShiftResult(GameEngine.fail(state, it), null) }

        val c = GameEngine.Ctx(state)
        val base = base(state)
        val eff = GameEngine.efficiency(state)
        val appearance = state.appearancePercent
        val perf = performance.coerceIn(0, 100)
        val bonus = perf / 2
        val gross = (base * (eff + appearance + bonus) + 50) / 100
        val tax = (gross * Rules.TAX_PERCENT + 50) / 100
        val net = gross - tax

        val notes = mutableListOf<String>()
        notes += details
        notes += ShopWork.performanceNote(perf)
        if (bonus > 0) notes += "Премия за внимательность и скорость: +$bonus%."
        if (eff < 100) notes += "Из-за усталости, голода или жажды Монтик работал не в полную силу ($eff%)."
        if (appearance > 0) notes += "Опрятный вид добавил +$appearance% к заработку."

        c.earn(net)
        c.s = c.s.copy(shifts = c.s.shifts + 1, bankShifts = c.s.bankShifts + 1, totalTax = c.s.totalTax + tax)
        c.meters(energy = -GameEngine.shiftEnergy(state), food = -Rules.SHIFT_FOOD, water = -Rules.SHIFT_WATER)
        c.s = c.s.copy(clockMinutes = c.s.clockMinutes + Rules.SHIFT_VIRTUAL_MINUTES)
        c.skill(Skill.BANKER, if (perf >= 80) 2 else 1)
        if (perf >= 70) c.s = c.s.copy(tasksCorrect = c.s.tasksCorrect + 1)
        c.log("смена в банке: начислено $gross, налог $tax, на руки $net")
        c.teach(Lesson.WORK)
        c.teach(Lesson.TAX)

        val stars = ShopWork.stars(perf)
        val bestBefore = state.shopBest[KEY]
        val record = bestBefore != null && perf > bestBefore
        if (bestBefore == null || perf > bestBefore) c.s = c.s.copy(shopBest = c.s.shopBest + (KEY to perf))
        c.xp(Progress.XP_SHIFT + stars * Progress.XP_SHOP_STAR)
        val slip = Payslip("Банк: проверка купюр", base, eff, appearance, bonus, gross, tax, net, notes, stars = stars, record = record)
        return ShiftResult(c.done(), slip)
    }

    fun bestStars(state: GameState): Int = state.shopBest[KEY]?.let { ShopWork.stars(it) } ?: 0
}

/*
 * Игровая приставка — её можно купить, накопив в копилке на цель «Игровая приставка».
 * Гонка на экране телевизора (кадр «Android Compact - 39»): «Время игры» и «Лимит» сверху.
 * Лимит учит, что игры — это радость, но и у неё есть мера: не больше трёх заездов в день.
 */
object ConsoleGame {
    const val GOAL_ID = "console"
    const val PLAYS_PER_DAY = 3
    const val RACE_SECONDS = 45
    const val MOOD_PER_RACE = 8

    fun owned(state: GameState): Boolean = GOAL_ID in state.goalsDone

    fun playsToday(state: GameState): Int = if (state.consoleDay == state.day) state.consolePlays else 0

    fun playsLeft(state: GameState): Int = (PLAYS_PER_DAY - playsToday(state)).coerceAtLeast(0)

    /** Почему сейчас играть нельзя (null — можно). */
    fun blocker(state: GameState): String? = when {
        !owned(state) -> "Приставки пока нет. Накопи на неё в копилке — цель «Игровая приставка»."
        state.sleep != null -> "Монтик спит — поиграем, когда проснётся."
        playsLeft(state) == 0 -> "На сегодня лимит игр исчерпан ($PLAYS_PER_DAY заезда). Глазам и голове нужен отдых — приходи завтра!"
        else -> null
    }

    /** Заезд окончен: настроение растёт, считается одна игра из дневного лимита. */
    fun finishRace(state: GameState, score: Int): Outcome {
        blocker(state)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        val plays = playsToday(state) + 1
        c.s = c.s.copy(consoleDay = state.day, consolePlays = plays)
        c.meters(energy = -3)
        c.mood(MOOD_PER_RACE, "Монтик поиграл в гонки на своей приставке — купленной на накопленное!")
        val left = PLAYS_PER_DAY - plays
        c.say(
            "🏁 Заезд окончен: $score очков. Настроение +$MOOD_PER_RACE." +
                if (left > 0) " Сегодня можно сыграть ещё $left." else " На сегодня лимит игр исчерпан — завтра можно снова."
        )
        c.log("гонки на приставке: $score очков")
        return c.done()
    }
}
