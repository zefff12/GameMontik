package ru.montik.app.game

/**
 * Каталог платных скинов (цветных вариантов Монтика).
 * Цвета для каждой части героя подобраны так, чтобы выглядели гармонично.
 */
object SkinCatalog {
    data class PaidSkin(
        val id: String,
        val name: String,
        val price: Int,  // coins
        val emoji: String,
        val colors: Map<HeroPart, Int>  // заготовка → RGB
    )

    val all = listOf(
        PaidSkin(
            id = "royal_white",
            name = "Королевский",
            price = 150,
            emoji = "👑",
            colors = mapOf(
                HeroPart.FUR to 0xF5F5F5,
                HeroPart.BELLY to 0xFFFAE6,
                HeroPart.EARS to 0xFFE4E9,
                HeroPart.PACK to 0xD4AF37,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "sky_blue",
            name = "Небо",
            price = 120,
            emoji = "🌤️",
            colors = mapOf(
                HeroPart.FUR to 0x87CEEB,
                HeroPart.BELLY to 0xFFF8DC,
                HeroPart.EARS to 0xFF69B4,
                HeroPart.PACK to 0x4682B4,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "cherry_pink",
            name = "Вишня",
            price = 180,
            emoji = "🍒",
            colors = mapOf(
                HeroPart.FUR to 0xFF1493,
                HeroPart.BELLY to 0xFFE4E1,
                HeroPart.EARS to 0xFF69B4,
                HeroPart.PACK to 0x8B0000,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "forest_green",
            name = "Лес",
            price = 140,
            emoji = "🌲",
            colors = mapOf(
                HeroPart.FUR to 0x228B22,
                HeroPart.BELLY to 0xF0FFF0,
                HeroPart.EARS to 0x90EE90,
                HeroPart.PACK to 0x2F4F2F,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "grape_purple",
            name = "Виноград",
            price = 160,
            emoji = "🍇",
            colors = mapOf(
                HeroPart.FUR to 0x663399,
                HeroPart.BELLY to 0xF8F0FF,
                HeroPart.EARS to 0xDA70D6,
                HeroPart.PACK to 0x4B0082,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "sunset_orange",
            name = "Закат",
            price = 170,
            emoji = "🌅",
            colors = mapOf(
                HeroPart.FUR to 0xFF8C00,
                HeroPart.BELLY to 0xFFF5EE,
                HeroPart.EARS to 0xFFB6C1,
                HeroPart.PACK to 0xFF4500,
                HeroPart.CROWN to 0xFFD700
            )
        ),
        PaidSkin(
            id = "night_black",
            name = "Ночь",
            price = 130,
            emoji = "🌙",
            colors = mapOf(
                HeroPart.FUR to 0x1C1C1C,
                HeroPart.BELLY to 0x404040,
                HeroPart.EARS to 0x696969,
                HeroPart.PACK to 0x2F4F4F,
                HeroPart.CROWN to 0xC0C0C0
            )
        ),
        PaidSkin(
            id = "magic_rainbow",
            name = "Радуга",
            price = 250,
            emoji = "🌈",
            colors = mapOf(
                HeroPart.FUR to 0xFF6B6B,
                HeroPart.BELLY to 0xFFE66D,
                HeroPart.EARS to 0x95E1D3,
                HeroPart.PACK to 0xA8D8EA,
                HeroPart.CROWN to 0xFF6B9D
            )
        )
    )

    fun skinById(id: String): PaidSkin? = all.find { it.id == id }

    /**
     * Попытка купить скин: проверяет наличие монет и владельца.
     * Возвращает Outcome с обновлённым состоянием или ошибкой.
     */
    fun buySkin(state: GameState, skinId: String): Outcome<GameState> {
        val skin = skinById(skinId) ?: return Outcome.error("Скин не найден")

        if (skin.id in state.ownedSkins) {
            return Outcome.error("Скин уже куплен")
        }

        if (state.coins < skin.price) {
            return Outcome.error("Недостаточно монет. Нужно ${skin.price}, есть ${state.coins}")
        }

        return Outcome.ok(
            state.copy(
                coins = state.coins - skin.price,
                ownedSkins = state.ownedSkins + skin.id,
                totalSpent = state.totalSpent + skin.price
            )
        )
    }

    /**
     * Экипировать скин (установить активным).
     * skinId = null: вернуться к стандартному виду.
     */
    fun equipSkin(state: GameState, skinId: String?): GameState {
        return if (skinId == null) {
            state.copy(heroPaidSkin = null)
        } else if (skinId in state.ownedSkins) {
            state.copy(heroPaidSkin = skinId)
        } else {
            state
        }
    }

    /**
     * Получить активный платный скин (если установлен) или null.
     */
    fun getActiveSkin(state: GameState): PaidSkin? {
        val skinId = state.heroPaidSkin ?: return null
        return skinById(skinId)
    }

    /**
     * Получить цвет части героя: из активного платного скина, из free-раскраски, или из preset.
     */
    fun getHeroColor(state: GameState, part: HeroPart): Int {
        // Сначала ищем в свободной раскраске
        state.skinColors[part.id]?.let { return it }

        // Потом в активном платном скине
        getActiveSkin(state)?.colors?.get(part)?.let { return it }

        // Наконец, в заготовке
        return state.preset.color(part)
    }
}

/** Исходы операций (успех или ошибка). */
sealed class Outcome<T> {
    data class Ok<T>(val state: T) : Outcome<T>()
    data class Error<T>(val message: String) : Outcome<T>()

    val ok: Boolean get() = this is Ok
    val state: T? get() = (this as? Ok)?.state

    companion object {
        fun <T> ok(state: T) = Ok(state)
        fun <T> error(message: String) = Error<T>(message)
    }
}
