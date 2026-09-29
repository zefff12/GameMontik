package ru.montik.app.game

/**
 * Игра про бартер: енот на рынке предлагает обменять товары.
 * Цель: научить ребенка справедливому обмену стоимости.
 * Появляется раз в 10 дней, дает опыт и монеты за правильные ответы.
 */
object BarterGame {
    const val BARTER_INTERVAL = 10  // дней между визитами
    const val UNDERSTANDING_PER_WIN = 5  // очков понимания
    const val COIN_REWARD = 50  // монет за 3 правильных

    data class BarterTrade(
        val id: String,
        val offers: String,  // что предлагает енот ("2 яблока")
        val wantsOptions: List<String>,  // варианты ответов ("1 апельсин", "3 конфеты", "1 хлеб")
        val correctIndex: Int,  // индекс правильного ответа
        val lesson: String  // объяснение ("2 яблока ≈ 3 конфеты по питательности")
    )

    /**
     * 10 сделок для обучения справедливому обмену.
     * Сложность растёт по мере прохождения.
     */
    val trades = listOf(
        BarterTrade(
            id = "trade_1",
            offers = "2 яблока",
            wantsOptions = listOf("1 апельсин", "3 конфеты", "5 конфет"),
            correctIndex = 1,
            lesson = "2 яблока равны 3 конфетам: одинаковая питательность и размер."
        ),
        BarterTrade(
            id = "trade_2",
            offers = "1 хлеб",
            wantsOptions = listOf("2 яблока", "5 конфет", "1 яйцо"),
            correctIndex = 1,
            lesson = "Хлеб питательнее одного яблока — нужно 2 яблока в обмен."
        ),
        BarterTrade(
            id = "trade_3",
            offers = "3 конфеты",
            wantsOptions = listOf("1 банан", "1 печенье", "2 печенья"),
            correctIndex = 2,
            lesson = "3 конфеты сладкие, печенье тоже. 2 печенья — справедливый обмен."
        ),
        BarterTrade(
            id = "trade_4",
            offers = "1 рыба",
            wantsOptions = listOf("2 яйца", "1 хлеб + 1 яйцо", "3 хлеба"),
            correctIndex = 1,
            lesson = "Рыба ценится как 1 хлеб + 1 яйцо по белкам и цене."
        ),
        BarterTrade(
            id = "trade_5",
            offers = "5 ягод",
            wantsOptions = listOf("1 яблоко", "2 конфеты", "1 яблоко + 1 конфета"),
            correctIndex = 0,
            lesson = "Ягоды меньше яблока по объему, но в обмене равны одному яблоку."
        ),
        BarterTrade(
            id = "trade_6",
            offers = "1 мёд (банка)",
            wantsOptions = listOf("2 хлеба", "3 яйца", "5 апельсинов"),
            correctIndex = 1,
            lesson = "Мёд редкий и ценный товар, стоит как 3 яйца."
        ),
        BarterTrade(
            id = "trade_7",
            offers = "горсть орехов",
            wantsOptions = listOf("1 хлеб", "2 яблока", "1 яблоко + 1 конфета"),
            correctIndex = 0,
            lesson = "Орехи питательные и дорогие — равны целому хлебу."
        ),
        BarterTrade(
            id = "trade_8",
            offers = "1 сыр",
            wantsOptions = listOf("2 яйца", "1 рыба", "3 конфеты + 1 хлеб"),
            correctIndex = 1,
            lesson = "Сыр питателен как рыба — одинаковая ценность по белкам."
        ),
        BarterTrade(
            id = "trade_9",
            offers = "1 булка хлеба",
            wantsOptions = listOf("2 яблока", "3 конфеты", "1 хлеб + 2 конфеты"),
            correctIndex = 1,
            lesson = "Булка хлеба чуть больше обычного хлеба, стоит 3 конфеты."
        ),
        BarterTrade(
            id = "trade_10",
            offers = "2 рыбы",
            wantsOptions = listOf("1 мёд", "4 яйца", "2 мёда"),
            correctIndex = 1,
            lesson = "2 рыбы равны 4 яйцам по питательности и цене на рынке."
        )
    )

    /**
     * Проверить, должен ли появиться енот (каждые 10 дней).
     */
    fun shouldShowBarter(state: GameState): Boolean {
        return (state.day - state.barterDay) >= BARTER_INTERVAL && state.day > 0
    }

    /**
     * Получить текущий набор из 3 сделок для этого визита.
     * Используется seed из gameState чтобы сделки были одинаковыми при перезагрузке.
     */
    fun getSessionTrades(state: GameState): List<BarterTrade> {
        val seed = state.day * 12345L + state.barterSessions
        val rnd = java.util.Random(seed)

        // Берём 3 рандомные сделки, но с некоторым предпочтением к сложным
        val difficulty = (state.barterUnderstanding / 20).coerceIn(0, 2)  // 0-2
        val shuffled = trades.shuffled(rnd)

        return shuffled
            .take(3 + difficulty)  // 3-5 в зависимости от уровня
            .shuffled(rnd)
            .take(3)
    }

    /**
     * Завершить визит на рынок: обновить понимание и выдать награду.
     * correctCount: сколько правильных ответов было (0-3).
     */
    fun completeMarket(state: GameState, correctCount: Int): GameState {
        // Увеличиваем понимание
        val newUnderstanding = (state.barterUnderstanding + correctCount * UNDERSTANDING_PER_WIN)
            .coerceIn(0, 100)

        // Награда: монеты за 2+ правильных ответа
        val coinReward = if (correctCount >= 2) COIN_REWARD else 0

        // Опыт за участие
        val xpReward = 20 + correctCount * 10

        return state.copy(
            barterDay = state.day,
            barterSessions = state.barterSessions + 1,
            barterUnderstanding = newUnderstanding,
            coins = state.coins + coinReward,
            xp = state.xp + xpReward
        )
    }

    /**
     * Описание текущего уровня понимания (для UI).
     */
    fun getLevelDescription(understanding: Int): String {
        return when {
            understanding < 20 -> "Совсем новичок 🐣"
            understanding < 40 -> "Учится торговать 📚"
            understanding < 60 -> "Неплохо получается! 👍"
            understanding < 80 -> "Почти мастер 🎯"
            else -> "Король торговца! 👑"
        }
    }

    /**
     * Получить подсказку для трудного вопроса.
     */
    fun getHint(trade: BarterTrade): String {
        val correct = trade.wantsOptions[trade.correctIndex]
        return "Подсказка: подумай о питательности... правильный ответ содержит $correct"
    }
}
