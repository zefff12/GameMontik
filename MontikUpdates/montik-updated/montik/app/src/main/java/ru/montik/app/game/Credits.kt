package ru.montik.app.game

import java.util.UUID

/**
 * Система кредитов: Монтик может брать кредиты на разные покупки.
 * Кредит нужно возвращать с процентами в течение месяца.
 */
object Credits {
    const val MAX_ACTIVE_CREDITS = 3
    const val DEFAULT_PERIOD_DAYS = 30
    const val DEFAULT_INTEREST_ANNUAL = 0.12f  // 12% в год

    fun canTakeLoan(state: GameState): Boolean {
        // Проверяем, не превышен ли лимит активных кредитов
        return state.credits.size < MAX_ACTIVE_CREDITS
    }

    /**
     * Взять кредит на определённую сумму и цель.
     * amount: 10-500 монет
     * purpose: "food", "housing", "clothes", "entertainment", "skills"
     * Ежемесячный платёж рассчитывается с учётом процентов.
     */
    fun takeLoan(
        state: GameState,
        amount: Int,
        purpose: String,
        periodDays: Int = DEFAULT_PERIOD_DAYS
    ): Outcome<GameState> {
        if (!canTakeLoan(state)) {
            return Outcome.error("Максимум $MAX_ACTIVE_CREDITS кредитов одновременно")
        }

        if (amount < 10 || amount > 500) {
            return Outcome.error("Сумма от 10 до 500 монет")
        }

        // Рассчитываем месячный платёж с процентами
        val monthlyRate = DEFAULT_INTEREST_ANNUAL / 12
        val monthlyPayment = (amount * (1 + monthlyRate)).toInt()

        val credit = Credit(
            id = UUID.randomUUID().toString(),
            amount = amount,
            purpose = purpose,
            dayTaken = state.day,
            daysRemaining = periodDays,
            monthlyPayment = monthlyPayment,
            interestRate = DEFAULT_INTEREST_ANNUAL
        )

        return Outcome.ok(
            state.copy(
                coins = state.coins + amount,  // Деньги зачисляют сразу
                debt = state.debt + monthlyPayment,  // Долг растёт
                credits = state.credits + credit,
                totalCreditUsed = state.totalCreditUsed + amount
            )
        )
    }

    /**
     * Внести платёж по кредиту.
     */
    fun makePayment(state: GameState, creditId: String): Outcome<GameState> {
        val credit = state.credits.find { it.id == creditId }
            ?: return Outcome.error("Кредит не найден")

        if (state.coins < credit.monthlyPayment) {
            return Outcome.error("Недостаточно монет для платежа: ${credit.monthlyPayment}")
        }

        val newCredits = state.credits - credit
        val completed = CreditRecord(
            amount = credit.amount,
            purpose = credit.purpose,
            dayCompleted = state.day,
            totalPaid = credit.monthlyPayment
        )

        return Outcome.ok(
            state.copy(
                coins = state.coins - credit.monthlyPayment,
                debt = (state.debt - credit.monthlyPayment).coerceAtLeast(0),
                credits = newCredits,
                creditHistory = state.creditHistory + completed,
                totalSpent = state.totalSpent + credit.monthlyPayment,
                totalCreditPaid = state.totalCreditPaid + credit.monthlyPayment
            )
        )
    }

    /**
     * Вернуть информацию по кредиту (для UI).
     */
    fun getCreditInfo(credit: Credit): String {
        val daysLeft = credit.daysRemaining
        val daysWord = when {
            daysLeft % 10 == 1 && daysLeft != 11 -> "день"
            daysLeft % 10 in 2..4 && daysLeft !in 12..14 -> "дня"
            else -> "дней"
        }
        return "${credit.amount} монет на «${credit.purpose}». " +
                "Платёж: ${credit.monthlyPayment}. " +
                "Осталось $daysLeft $daysWord."
    }

    /**
     * Получить сводку по всем кредитам.
     */
    fun getSummary(state: GameState): String {
        if (state.credits.isEmpty()) {
            return "Кредитов нет"
        }
        val total = state.credits.sumOf { it.amount }
        val payment = state.credits.sumOf { it.monthlyPayment }
        return "Всего в кредитах: $total монет. " +
                "Ежемесячный платёж: $payment. " +
                "Активных кредитов: ${state.credits.size}."
    }
}
