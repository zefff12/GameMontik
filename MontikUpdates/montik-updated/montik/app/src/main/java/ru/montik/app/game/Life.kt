package ru.montik.app.game

/*
 * Жизнь Монтика по периодам: квартира и аренда, план бюджета и факт, звёзды привычек, уровни и настроение.
 *
 * Игровой период — [Rules.PERIOD_DAYS] игровых дней. В начале периода ребёнок распределяет доступные деньги
 * по трём направлениям: обязательное, желаемое и копилка. В конце периода игра сравнивает план с фактом и даёт
 * до трёх звёзд привычек. Звёзды копятся и открывают переезд в жильё получше — это и есть рост Монтика:
 * скромный достаток → средний → богатый.
 *
 * Здесь только правила, без экранов (экраны — ui/LifeUi.kt).
 */

/** На что потрачены деньги: от этого зависит, в какую строку плана они попадут. */
enum class SpendKind(val title: String) {
    /** Обязательное: еда, вода, квартира, нужные вещи, непредвиденные расходы. */
    NEED("Обязательное"),

    /** Желаемое: радости, игрушки, сувениры, реклама. */
    WANT("Желаемое"),

    /** Не входит в план: вложения в свой магазин, переезд, покупка цели из копилки. */
    OTHER("Другое")
}

/**
 * Жильё = уровень Монтика. [rent] платится дважды в игровой месяц, [moveCost] — один раз при переезде,
 * [starsNeeded] — сколько звёзд привычек нужно, чтобы хозяин согласился сдать квартиру.
 * [room] и [kitchen] — префиксы картинок комнаты и кухни из макета.
 */
enum class Housing(
    val level: Int,
    val title: String,
    val levelTitle: String,
    val emoji: String,
    val rent: Int,
    val moveCost: Int,
    val starsNeeded: Int,
    val about: String,
    val room: String,
    val kitchen: String
) {
    MODEST(
        1, "Скромная квартира", "Скромный достаток", "🏠", 150, 0, 0,
        "Небольшая комната и простая кухня. Недорого — можно спокойно учиться планировать деньги.",
        "fg_room", "fg_kitchen1"
    ),
    COZY(
        2, "Уютная квартира", "Средний достаток", "🏡", 400, 300, 6,
        "Уютная комната с книжной полкой и светлая современная кухня. Аренда выше — нужен стабильный доход.",
        "fg_room2", "fg_kitchen2"
    ),
    PENTHOUSE(
        3, "Пентхаус с видом на Кремль", "Богатый", "🏙", 1500, 2000, 15,
        "Большие окна, вид на Кремль и кухня с мрамором. Роскошно, но и расходы большие.",
        "fg_room3", "fg_kitchen3"
    );

    companion object {
        fun byLevel(level: Int): Housing = values().firstOrNull { it.level == level } ?: MODEST
    }
}

/**
 * План бюджета на период: [available] — сколько было в кошельке, когда составляли план,
 * и распределение по трём направлениям. Пока [confirmed] = false, план можно менять.
 */
data class BudgetPlan(
    val period: Int,
    val available: Int,
    val needs: Int,
    val wants: Int,
    val savings: Int,
    val confirmed: Boolean = false
) {
    val total: Int get() = needs + wants + savings
    val left: Int get() = available - total

    companion object {
        fun encode(p: BudgetPlan): String =
            listOf(p.period, p.available, p.needs, p.wants, p.savings, if (p.confirmed) 1 else 0).joinToString(",")

        fun decode(text: String?): BudgetPlan? {
            val f = text?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
            if (f.size != 6 || f.take(5).any { it < 0 }) return null
            return BudgetPlan(f[0], f[1], f[2], f[3], f[4], f[5] == 1)
        }
    }
}

/** Итоги периода: план (если был), факт и три звезды привычек. */
data class PeriodResult(
    val period: Int,
    val planned: Boolean,
    val planNeeds: Int,
    val planWants: Int,
    val planSavings: Int,
    val needs: Int,
    val wants: Int,
    val saved: Int,
    val earned: Int,
    val hungryNights: Int,
    val rentDebt: Int,
    /** ⭐ Обязательное обеспечено: квартира оплачена, Монтик не голодал. */
    val starNeeds: Boolean,
    /** ⭐ Траты уложились в план. */
    val starPlan: Boolean,
    /** ⭐ Отложено в копилку не меньше, чем по плану. */
    val starSave: Boolean
) {
    val stars: Int get() = listOf(starNeeds, starPlan, starSave).count { it }

    companion object {
        fun encode(r: PeriodResult): String = listOf(
            r.period, if (r.planned) 1 else 0, r.planNeeds, r.planWants, r.planSavings,
            r.needs, r.wants, r.saved, r.earned, r.hungryNights, r.rentDebt,
            if (r.starNeeds) 1 else 0, if (r.starPlan) 1 else 0, if (r.starSave) 1 else 0
        ).joinToString(",")

        fun decode(text: String?): PeriodResult? {
            val f = text?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
            if (f.size != 14) return null
            return PeriodResult(
                f[0], f[1] == 1, f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10],
                f[11] == 1, f[12] == 1, f[13] == 1
            )
        }
    }
}

object Life {
    // ───────────────────────── Календарь ─────────────────────────

    /** Номер периода, в который попадает игровой день. */
    fun periodOf(day: Int): Int = (day.coerceAtLeast(1) - 1) / Rules.PERIOD_DAYS + 1

    fun periodFirstDay(period: Int): Int = (period - 1) * Rules.PERIOD_DAYS + 1
    fun periodLastDay(period: Int): Int = period * Rules.PERIOD_DAYS

    /** Сколько дней периода осталось, включая сегодняшний. */
    fun daysLeftInPeriod(state: GameState): Int = periodLastDay(state.period) - state.day + 1

    /** Число игрового месяца (1..30) и номер месяца. */
    fun dayOfMonth(day: Int): Int = (day - 1) % Rules.MONTH_DAYS + 1
    fun monthOf(day: Int): Int = (day - 1) / Rules.MONTH_DAYS + 1

    /** Ближайший день оплаты квартиры не раньше [day] (15-е и 30-е число). */
    fun nextRentDay(day: Int): Int {
        val p = Rules.RENT_PERIOD_DAYS
        return ((day - 1) / p + 1) * p
    }

    // ───────────────────────── Аренда ─────────────────────────

    /** Сколько дней до оплаты квартиры (0 — сегодня). */
    fun daysUntilRent(state: GameState): Int = (state.rentDueDay - state.day).coerceAtLeast(0)

    /** Пора ли показывать отсчёт «до оплаты осталось N дней». */
    fun rentCountdown(state: GameState): Boolean =
        !state.rentPaidAhead && daysUntilRent(state) <= Rules.RENT_NOTICE_DAYS

    /** Можно ли заплатить за квартиру заранее (в дни отсчёта). */
    fun canPayRentAhead(state: GameState): Boolean = rentCountdown(state)

    /** Короткая строка про квартиру для главного экрана (null — сейчас напоминать не о чем). */
    fun rentNotice(state: GameState): String? = when {
        state.rentDebt > 0 -> "🏠 Долг за квартиру: ${state.rentDebt}"
        rentCountdown(state) -> {
            val left = daysUntilRent(state)
            "🏠 Квартира: " + when (left) {
                0 -> "оплата сегодня"
                1 -> "остался 1 день"
                in 2..4 -> "осталось $left дня"
                else -> "осталось $left дней"
            } + " · ${state.home.rent}"
        }
        else -> null
    }

    /** Заплатить за квартиру заранее: в день оплаты деньги уже не спишутся. */
    fun payRentAhead(state: GameState): Outcome {
        if (!canPayRentAhead(state)) {
            return GameEngine.fail(state, "Платить пока рано: оплата откроется за ${Rules.RENT_NOTICE_DAYS} дней до срока.")
        }
        val rent = state.home.rent
        if (state.coins < rent) {
            return GameEngine.fail(
                state,
                "На квартиру не хватает ${rent - state.coins} монет. Поработай ещё или возьми из копилки — до срока ${daysUntilRent(state)} дн."
            )
        }
        val c = GameEngine.Ctx(state)
        c.spend(rent, SpendKind.NEED, "квартира")
        c.s = c.s.copy(rentPaidAhead = true)
        c.say("🏠 Квартира оплачена заранее: −$rent монет. В день оплаты платить уже не нужно — спокойно!")
        c.log("оплатил квартиру заранее (−$rent)")
        c.skill(Skill.PLANNER, 1)
        c.teach(Lesson.RENT)
        return c.done()
    }

    /** Погасить долг за квартиру (сколько хватает в кошельке). */
    fun payRentDebt(state: GameState): Outcome {
        if (state.rentDebt <= 0) return GameEngine.fail(state, "Долга за квартиру нет.")
        val pay = minOf(state.rentDebt, state.coins)
        if (pay <= 0) return GameEngine.fail(state, "В кошельке пусто. Сначала заработай на смене.")
        val c = GameEngine.Ctx(state)
        c.spend(pay, SpendKind.NEED, "долг за квартиру")
        c.s = c.s.copy(rentDebt = c.s.rentDebt - pay)
        if (c.s.rentDebt == 0) c.say("🏠 Долг за квартиру погашен. Хозяин благодарит Монтика!")
        else c.say("🏠 Погашено $pay монет. Осталось ${c.s.rentDebt}.")
        c.log("погасил долг за квартиру $pay")
        return c.done()
    }

    /**
     * Наступил новый день: если это день оплаты — платим за квартиру (кошелёк, потом подушка,
     * остаток — долг без выселения). За [Rules.RENT_NOTICE_DAYS] дней до срока — напоминание.
     */
    internal fun settleRent(c: GameEngine.Ctx) {
        val rent = c.s.home.rent
        if (c.s.day >= c.s.rentDueDay) {
            if (c.s.rentPaidAhead) {
                c.say("🏠 Сегодня день оплаты квартиры, но она оплачена заранее. Молодец!")
            } else {
                val fromWallet = minOf(c.s.coins, rent)
                if (fromWallet > 0) c.spend(fromWallet, SpendKind.NEED, "квартира")
                var rest = rent - fromWallet
                val fromCushion = minOf(rest, c.s.cushion)
                if (fromCushion > 0) {
                    c.s = c.s.copy(cushion = c.s.cushion - fromCushion, pSaved = c.s.pSaved - fromCushion)
                    c.countSpend(fromCushion, SpendKind.NEED, "квартира (из подушки)")
                    rest -= fromCushion
                }
                if (rest > 0) {
                    c.s = c.s.copy(rentDebt = c.s.rentDebt + rest)
                    c.say(
                        "🏠 День оплаты квартиры: внесено ${rent - rest} из $rent монет. Не хватило $rest — это долг. " +
                            "Хозяин подождёт, но долг нужно вернуть. Совет: откладывай на квартиру заранее."
                    )
                    c.log("квартира: не хватило $rest, долг")
                } else {
                    val note = if (fromCushion > 0) " ($fromCushion — из подушки безопасности)" else ""
                    c.say("🏠 Оплачена квартира: −$rent монет$note.")
                    c.log("оплатил квартиру (−$rent)")
                }
                c.teach(Lesson.RENT)
            }
            c.s = c.s.copy(rentDueDay = c.s.rentDueDay + Rules.RENT_PERIOD_DAYS, rentPaidAhead = false)
        } else if (!c.s.rentPaidAhead) {
            val left = c.s.rentDueDay - c.s.day
            if (left in 1..Rules.RENT_NOTICE_DAYS) {
                c.say("🏠 До оплаты квартиры осталось $left дн.: нужно $rent монет. Можно заплатить заранее.")
            }
        }
    }

    // ───────────────────────── План бюджета ─────────────────────────

    /** План текущего периода (null — ещё не составлен). */
    fun currentPlan(state: GameState): BudgetPlan? = state.plan?.takeIf { it.period == state.period }

    /** Нужно ли напомнить составить план. */
    fun planMissing(state: GameState): Boolean = currentPlan(state)?.confirmed != true

    /** Сколько обязательных трат ждёт в этом периоде: квартира (если срок внутри периода) и еда на каждый день. */
    fun expectedNeeds(state: GameState): Int {
        val last = periodLastDay(state.period)
        val rent = if (!state.rentPaidAhead && state.rentDueDay <= last) state.home.rent else 0
        val food = Life.daysLeftInPeriod(state) * Rules.FOOD_PER_DAY
        return rent + food + state.rentDebt
    }

    /**
     * Составить или поменять план. Сумма не может быть больше, чем есть в кошельке сейчас.
     * [confirm] = true — подтвердить: после этого план не меняется до конца периода.
     */
    fun setPlan(state: GameState, needs: Int, wants: Int, savings: Int, confirm: Boolean): Outcome {
        if (needs < 0 || wants < 0 || savings < 0) return GameEngine.fail(state, "Суммы не могут быть меньше нуля.")
        val existing = currentPlan(state)
        if (existing?.confirmed == true) {
            return GameEngine.fail(state, "План на этот период уже подтверждён. Новый план — в следующем периоде.")
        }
        val available = state.coins
        val total = needs + wants + savings
        if (total > available) {
            return GameEngine.fail(state, "В плане $total монет, а в кошельке только $available. Убавь одну из сумм на ${total - available}.")
        }
        val plan = BudgetPlan(state.period, available, needs, wants, savings, confirm)
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(plan = plan)
        c.teach(Lesson.PLAN)
        if (confirm) {
            c.say("📋 План на период ${plan.period} готов: обязательное $needs, желаемое $wants, в копилку $savings. Остаток ${plan.left}.")
            c.log("план периода ${plan.period}: $needs / $wants / $savings")
            if (savings > 0 && savings * 100 >= available * 10) c.skill(Skill.PLANNER, 1)
        }
        return c.done()
    }

    // ───────────────────────── Итоги периода ─────────────────────────

    /** Допуск: небольшой перерасход не отнимает звезду (±10%, но не меньше 5 монет). */
    fun tolerance(planned: Int): Int = maxOf(5, planned / 10)

    /** Итоги текущего периода по тому, что уже случилось (для экрана «план и факт»). */
    fun evaluate(state: GameState): PeriodResult {
        val plan = currentPlan(state)?.takeIf { it.confirmed }
        val needsOk = state.rentDebt == 0 && state.pHungry == 0
        val planOk = plan != null &&
            state.pWants <= plan.wants + tolerance(plan.wants) &&
            state.pNeeds <= plan.needs + tolerance(plan.needs)
        val saveOk = if (plan != null) state.pSaved > 0 && state.pSaved >= plan.savings else state.pSaved > 0
        return PeriodResult(
            period = state.period,
            planned = plan != null,
            planNeeds = plan?.needs ?: 0,
            planWants = plan?.wants ?: 0,
            planSavings = plan?.savings ?: 0,
            needs = state.pNeeds,
            wants = state.pWants,
            saved = state.pSaved,
            earned = state.pEarned,
            hungryNights = state.pHungry,
            rentDebt = state.rentDebt,
            starNeeds = needsOk,
            starPlan = planOk,
            starSave = saveOk
        )
    }

    /** Простые слова: почему звезда получена или нет и что сделать дальше. */
    fun explain(r: PeriodResult): List<String> {
        val lines = mutableListOf<String>()
        lines += if (r.starNeeds) "⭐ Обязательное обеспечено: квартира оплачена, Монтик не голодал."
        else buildString {
            append("☆ Обязательное: ")
            if (r.rentDebt > 0) append("остался долг за квартиру ${r.rentDebt}. ")
            if (r.hungryNights > 0) append("Монтик ложился спать голодным (${r.hungryNights} раз). ")
            append("Сначала оплачивай нужное — еду, воду и квартиру.")
        }
        lines += when {
            !r.planned -> "☆ План: в этом периоде плана не было. Составь его в начале периода — и получишь звезду."
            r.starPlan -> "⭐ Траты уложились в план: желаемое ${r.wants} из ${r.planWants}, обязательное ${r.needs} из ${r.planNeeds}."
            else -> buildString {
                append("☆ План: ")
                if (r.wants > r.planWants + tolerance(r.planWants)) append("на желаемое ушло ${r.wants} вместо ${r.planWants}. ")
                if (r.needs > r.planNeeds + tolerance(r.planNeeds)) append("обязательное — ${r.needs} вместо ${r.planNeeds}: не забывай про квартиру и еду. ")
                append("В следующем периоде сравни план с тем, что было на самом деле.")
            }
        }
        lines += when {
            r.starSave && r.planned -> "⭐ Копилка: отложено ${r.saved} (по плану ${r.planSavings})."
            r.starSave -> "⭐ Копилка: отложено ${r.saved}. Небольшие регулярные накопления приближают цель!"
            r.saved <= 0 -> "☆ Копилка: в этом периоде ничего не отложено. Даже 10 монет — уже шаг к цели."
            else -> "☆ Копилка: отложено ${r.saved}, а по плану ${r.planSavings}. Попробуй дотянуть до плана."
        }
        return lines
    }

    /**
     * Период закончился (наступил первый день следующего): подводим итоги, начисляем звёзды,
     * очищаем факт и историю покупок. Вызывается ядром при смене дня.
     */
    internal fun closePeriodIfNeeded(c: GameEngine.Ctx, previousDay: Int) {
        val oldPeriod = periodOf(previousDay)
        if (periodOf(c.s.day) == oldPeriod) return
        // Итог считаем для старого периода (день уже сменился, поэтому подставляем его).
        val r = evaluate(c.s.copy(day = previousDay))
        val stars = c.s.stars + r.stars
        c.s = c.s.copy(
            periods = (c.s.periods + r).takeLast(Rules.MAX_PERIODS),
            stars = stars,
            pNeeds = 0, pWants = 0, pSaved = 0, pEarned = 0, pHungry = 0,
            purchases = emptyList()
        )
        c.log("итоги периода ${r.period}: звёзд ${r.stars} из 3")
        c.say("📊 Период ${r.period} закончился: звёзд привычек — ${r.stars} из 3. Посмотри итоги и составь новый план!")
        if (r.stars == 3) c.skill(Skill.PLANNER, 2)
        c.xp(r.stars * Progress.XP_PERIOD_STAR)
        val next = nextHousing(c.s)
        if (next != null && stars >= next.starsNeeded && stars - r.stars < next.starsNeeded) {
            c.say("${next.emoji} Звёзд хватает на переезд: «${next.title}». Загляни в раздел «Жильё»!")
        }
        c.teach(Lesson.PLAN)
    }

    // ───────────────────────── Уровни и переезд ─────────────────────────

    fun nextHousing(state: GameState): Housing? = Housing.values().firstOrNull { it.level == state.housing + 1 }

    /** Почему переехать нельзя (null — можно). */
    fun moveBlocker(state: GameState, target: Housing): String? = when {
        target.level == state.housing -> "Монтик уже живёт здесь."
        target.level > state.housing + 1 -> "Сначала нужно пожить на уровне ${state.housing + 1}."
        target.level < state.housing -> null
        state.stars < target.starsNeeded -> "Нужно звёзд привычек: ${target.starsNeeded}, а есть ${state.stars}."
        state.rentDebt > 0 -> "Сначала погаси долг за квартиру: ${state.rentDebt}."
        state.coins < target.moveCost -> "На переезд нужно ${target.moveCost} монет в кошельке, а есть ${state.coins}."
        else -> null
    }

    /** Переезд. Вверх — за звёзды и деньги на переезд; вниз (подешевле) — бесплатно, если стало трудно платить. */
    fun moveTo(state: GameState, level: Int): Outcome {
        val target = Housing.values().firstOrNull { it.level == level } ?: return GameEngine.fail(state, "Такого жилья нет.")
        moveBlocker(state, target)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        if (target.level > state.housing) {
            if (target.moveCost > 0) c.spend(target.moveCost, SpendKind.OTHER, "переезд")
            c.s = c.s.copy(housing = target.level, milestones = c.s.milestones + "moved_up")
            c.mood(20, "Переезд в новую квартиру — большое событие!")
            c.xp(Progress.XP_MOVE_UP)
            c.say(
                "${target.emoji} Монтик переехал: «${target.title}»! Теперь его уровень — «${target.levelTitle}». " +
                    "Аренда — ${target.rent} монет дважды в месяц."
            )
            c.log("переехал: ${target.title}")
        } else {
            c.s = c.s.copy(housing = target.level)
            c.say(
                "${target.emoji} Монтик переехал в жильё попроще: «${target.title}». Аренда теперь ${target.rent}. " +
                    "Это разумно, если расходы стали слишком большими."
            )
            c.log("переехал попроще: ${target.title}")
        }
        c.teach(Lesson.RENT)
        return c.done()
    }

    // ───────────────────────── Настроение ─────────────────────────

    /** Короткое описание настроения для главного экрана. */
    fun moodTitle(mood: Int): String = when {
        mood >= 75 -> "😄 Отличное"
        mood >= 45 -> "🙂 Хорошее"
        mood >= 20 -> "😐 Спокойное"
        else -> "😕 Скучает"
    }

    /** Почему настроение такое — если причина не записана, объясняем по уровню. */
    fun moodReason(state: GameState): String = state.moodWhy.ifBlank {
        when {
            state.mood >= 45 -> "Монтику хорошо: он сыт и отдохнул."
            else -> "Монтику немного скучно. Маленькая радость из плана «желаемое» поднимет настроение."
        }
    }
}
