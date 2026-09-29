package ru.montik.app.game

/**
 * Кредиты в банке и кредитная история.
 *
 * Банк даёт деньги сразу, а вернуть нужно больше — это плата банку (переплата). Каждый кредит
 * нужно вернуть до срока; за каждый день просрочки набегает штраф. Закрытые кредиты попадают
 * в кредитную историю: по ней банк решает, давать ли следующий кредит (например, ипотеку).
 */
enum class LoanProduct(
    val id: String,
    val title: String,
    val emoji: String,
    val amount: Int,
    val total: Int,
    val days: Int,
    val note: String
) {
    SMALL("small", "Небольшой кредит", "💳", 50, 60, 7, "На срочные мелочи до зарплаты."),
    STUDY("study", "Кредит на учёбу", "🎓", 100, 110, 20, "Маленькая переплата: учёба потом помогает зарабатывать."),
    PHONE("phone", "Кредит на покупку", "📱", 150, 195, 14, "На телефон или приставку. Переплата большая — подумай, стоит ли."),
    MORTGAGE("mortgage", "Ипотека", "🏠", 400, 520, 40, "Большой кредит на переезд в квартиру получше. Нужна хорошая кредитная история.");

    /** Переплата — плата банку за то, что деньги дали сейчас. */
    val overpay: Int get() = total - amount
}

object Credits {
    const val MAX_ACTIVE = 3
    const val MAX_HISTORY = 50

    /** Штраф за каждый день просрочки. */
    const val PENALTY_PER_DAY = 3

    fun product(id: String): LoanProduct? = LoanProduct.values().firstOrNull { it.id == id }

    fun isOverdue(state: GameState, credit: Credit): Boolean = state.day > credit.dueDay

    fun daysLeft(state: GameState, credit: Credit): Int = credit.dueDay - state.day

    /** Сколько всего осталось вернуть по всем кредитам. */
    fun totalLeft(state: GameState): Int = state.credits.sumOf { it.left }

    /**
     * Кредитный рейтинг 0–100: сначала 50, за каждый вовремя закрытый кредит +15,
     * за закрытый с опозданием −25, за каждый просроченный сейчас −10.
     */
    fun score(state: GameState): Int {
        val onTime = state.creditHistory.count { it.onTime }
        val late = state.creditHistory.size - onTime
        val overdueNow = state.credits.count { isOverdue(state, it) }
        return (50 + onTime * 15 - late * 25 - overdueNow * 10).coerceIn(0, 100)
    }

    fun scoreTitle(score: Int): String = when {
        score >= 80 -> "Отличная"
        score >= 60 -> "Хорошая"
        score >= 40 -> "Обычная"
        else -> "Плохая"
    }

    /** Почему этот кредит сейчас взять нельзя (null — можно). */
    fun blocker(state: GameState, product: LoanProduct): String? {
        if (state.credits.any { isOverdue(state, it) }) {
            return "Есть просроченный кредит. Пока его не вернёшь, банк новый не даст."
        }
        if (state.credits.size >= MAX_ACTIVE) return "Уже $MAX_ACTIVE кредита — больше банк не даёт. Сначала верни один."
        if (state.credits.any { it.product == product.id }) return "Такой кредит уже взят. Сначала верни его."
        if (product == LoanProduct.MORTGAGE) {
            if (state.creditHistory.none { it.onTime }) {
                return "Ипотеку дают тем, у кого есть хорошая кредитная история: сначала верни вовремя хотя бы один кредит."
            }
            if (score(state) < 60) return "Кредитная история пока слабая — банк не готов дать ипотеку."
        } else if (score(state) < 30) {
            return "Кредитная история плохая: банк больше не доверяет. Возвращай долги вовремя, и доверие вернётся."
        }
        return null
    }

    fun take(state: GameState, productId: String): Outcome {
        val product = product(productId) ?: return GameEngine.fail(state, "Такого кредита нет.")
        blocker(state, product)?.let { return GameEngine.fail(state, it) }
        val c = GameEngine.Ctx(state)
        val credit = Credit(
            id = "c${state.creditSeq + 1}",
            product = product.id,
            amount = product.amount,
            total = product.total,
            paid = 0,
            dayTaken = state.day,
            dueDay = state.day + product.days
        )
        c.s = c.s.copy(
            coins = c.s.coins + product.amount,
            credits = c.s.credits + credit,
            creditSeq = c.s.creditSeq + 1
        )
        c.say(
            "${product.emoji} Банк дал ${product.amount} монет. Вернуть нужно ${product.total} " +
                "до ${credit.dueDay}-го дня: ${product.overpay} монет — плата банку. " +
                "Если опоздать, каждый день добавит штраф $PENALTY_PER_DAY монеты."
        )
        c.log("взял кредит «${product.title}» ${product.amount} (вернуть ${product.total})")
        c.teach(Lesson.CREDIT)
        return c.done()
    }

    /** Внести [amount] монет в счёт кредита (сколько хватает в кошельке). */
    fun pay(state: GameState, creditId: String, amount: Int): Outcome {
        val credit = state.credits.firstOrNull { it.id == creditId } ?: return GameEngine.fail(state, "Такого кредита нет.")
        if (amount <= 0) return GameEngine.fail(state, "Выбери, сколько монет внести.")
        val pay = minOf(amount, credit.left, state.coins)
        if (pay <= 0) return GameEngine.fail(state, "В кошельке нет монет. Заработай на смене.")
        val title = product(credit.product)?.title ?: "кредит"
        val c = GameEngine.Ctx(state)
        c.spend(pay, SpendKind.NEED, "платёж: $title")
        val updated = credit.copy(paid = credit.paid + pay)
        if (updated.left <= 0) {
            val onTime = !isOverdue(state, credit)
            val record = CreditRecord(credit.product, credit.amount, updated.paid, credit.dayTaken, state.day, onTime)
            c.s = c.s.copy(
                credits = c.s.credits.filterNot { it.id == credit.id },
                creditHistory = (c.s.creditHistory + record).takeLast(MAX_HISTORY)
            )
            c.say(
                if (onTime) "✅ «$title» полностью возвращён вовремя! В кредитной истории — хорошая отметка."
                else "✅ «$title» возвращён, но с опозданием. Банк это запомнил: в истории отметка «просрочка»."
            )
            c.log("закрыл кредит «$title»" + if (onTime) "" else " (с опозданием)")
            if (Medals.LOAN_REPAID !in c.s.milestones) {
                c.s = c.s.copy(milestones = c.s.milestones + Medals.LOAN_REPAID)
            }
            if (onTime) c.skill(Skill.BANKER, 2)
        } else {
            c.s = c.s.copy(credits = c.s.credits.map { if (it.id == credit.id) updated else it })
            c.say("💳 Внесено $pay монет по «$title». Осталось ${updated.left}.")
            c.log("платёж по кредиту $pay")
        }
        c.teach(Lesson.CREDIT)
        return c.done()
    }

    /** Новый игровой день: просроченные кредиты получают штраф, о близком сроке — напоминание. */
    internal fun settleDay(c: GameEngine.Ctx) {
        if (c.s.credits.isEmpty()) return
        val day = c.s.day
        val updated = c.s.credits.map { credit ->
            val title = product(credit.product)?.title ?: "кредит"
            val left = credit.dueDay - day
            when {
                left < 0 -> {
                    if (left == -1) {
                        c.say("⚠️ Срок по «$title» прошёл! Каждый день просрочки — штраф $PENALTY_PER_DAY монеты, а кредитная история портится.")
                    }
                    credit.copy(penalty = credit.penalty + PENALTY_PER_DAY)
                }
                left <= 2 -> {
                    c.say("⏰ По «$title» осталось вернуть ${credit.left} монет, срок — через $left дн.")
                    credit
                }
                else -> credit
            }
        }
        c.s = c.s.copy(credits = updated)
    }
}
