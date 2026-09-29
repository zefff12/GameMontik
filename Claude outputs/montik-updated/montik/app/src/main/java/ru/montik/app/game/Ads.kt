package ru.montik.app.game

/*
 * «Рекламное предложение» — учебная ситуация про импульсивные покупки. Раз в [Rules.AD_EVERY_DAYS]
 * игровых дня Монтику показывают яркую рекламу из макета. Крестик — деньги остаются в кошельке,
 * «Купить» — монеты списываются как желаемая трата. После любого выбора ребёнок видит объяснение.
 *
 * Реклама вымышленная: ненастоящие товары без брендов, покупка только за игровые монеты.
 */

/** Рекламное предложение. [art] — картинка из макета, [basePrice] — цена для первого уровня. */
data class AdOffer(
    val id: String,
    val art: String,
    val item: String,
    val emoji: String,
    val basePrice: Int,
    val slogan: String
)

object Ads {
    val all: List<AdOffer> = listOf(
        AdOffer("car", "fg_ad_car", "Машинка", "🚗", 90, "Купи свою машину сейчас!"),
        AdOffer("headphones", "fg_ad_headphones", "Наушники", "🎧", 70, "Купи свои наушники сейчас!"),
        AdOffer("car_mood", "fg_ad_car2", "Машинка", "🚗", 85, "Хорошая машина — хорошее настроение!"),
        AdOffer("favourite", "fg_ad_thing", "Любимая вещь", "🎁", 60, "Купи свою любимую вещь сейчас!"),
        AdOffer("phone", "fg_ad_phone", "Телефон", "📱", 95, "Купи свой телефон сейчас!"),
        AdOffer("car_worm", "fg_ad_car3", "Машинка", "🚗", 80, "Купи свою машину сейчас!")
    )

    fun byId(id: String?): AdOffer? = all.firstOrNull { it.id == id }

    /** Цена растёт вместе с уровнем Монтика: реклама всегда «подстраивается» под кошелёк. */
    fun price(state: GameState, ad: AdOffer): Int = ad.basePrice * when (state.housing) {
        1 -> 1
        2 -> 3
        else -> 10
    }

    fun pending(state: GameState): AdOffer? = byId(state.pendingAd)

    /** Наступил новый день: каждый третий день появляется новая реклама (если предыдущую уже закрыли). */
    internal fun schedule(c: GameEngine.Ctx) {
        val day = c.s.day
        if (day % Rules.AD_EVERY_DAYS != 0 || day == c.s.lastAdDay || c.s.pendingAd != null) return
        val ad = all[c.s.adsShown % all.size]
        c.s = c.s.copy(pendingAd = ad.id, lastAdDay = day, adsShown = c.s.adsShown + 1)
    }

    /** Крестик: ничего не куплено, деньги остались. */
    fun decline(state: GameState): Outcome {
        val ad = pending(state) ?: return GameEngine.fail(state, "Рекламы нет.")
        val price = price(state, ad)
        val c = GameEngine.Ctx(state)
        c.s = c.s.copy(pendingAd = null, adsDeclined = c.s.adsDeclined + 1)
        c.say(
            "✋ Монтик закрыл рекламу — $price монет остались в кошельке. " +
                "Реклама кричит «выгода», но выгодно это продавцу. Хорошая покупка — та, что в плане."
        )
        c.log("закрыл рекламу: ${ad.item.lowercase()} (сберёг $price)")
        c.teach(Lesson.ADS)
        c.xp(Progress.XP_AD_DECLINED)
        if (c.s.adsDeclined == 3) {
            c.skill(Skill.PLANNER, 2)
            c.say("🛡 Три раза подряд ты не поддался рекламе. Навык «Планировщик» растёт!")
        }
        return c.done()
    }

    /** «Купить»: списываются монеты (желаемое), настроение ненадолго растёт. В долг купить нельзя. */
    fun buy(state: GameState): Outcome {
        val ad = pending(state) ?: return GameEngine.fail(state, "Рекламы нет.")
        val price = price(state, ad)
        if (state.coins < price) {
            return GameEngine.fail(
                state,
                "Не хватает ${price - state.coins} монет: реклама обещает выгоду, но в кошельке только ${state.coins}. " +
                    "Покупать в долг по рекламе — плохая идея. Нажми крестик."
            )
        }
        val c = GameEngine.Ctx(state)
        c.spend(price, SpendKind.WANT, "${ad.item} по рекламе")
        c.s = c.s.copy(
            pendingAd = null,
            adsBought = c.s.adsBought + 1,
            joys = c.s.joys + ("ad_${ad.id}" to (c.s.joys["ad_${ad.id}"] ?: 0) + 1)
        )
        c.mood(12, "Новая вещь по рекламе порадовала — но радость от импульсивной покупки быстро проходит.")
        c.say(
            "${ad.emoji} Куплено по рекламе: ${ad.item.lowercase()} за $price монет. Это желаемая трата — " +
                "она попадёт в план в строку «желаемое». Подумай в следующий раз: было ли это в плане?"
        )
        c.log("купил по рекламе: ${ad.item.lowercase()} (−$price)")
        c.teach(Lesson.ADS)
        return c.done()
    }
}
