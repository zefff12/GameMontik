package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки игрового ядра: экономика, налог 13%, сон, сохранение и проходимость игры. */
class GameEngineTest {

    private fun started(seed: Long = 42L): GameState {
        val s = GameEngine.newGame(seed)
        return GameEngine.choose(GameEngine.markCreated(s), Scenarios.INTRO, 0).outcome.state
    }

    @Test
    fun startingWalletIs50AndIntroGoodChoiceLeaves40() {
        val s0 = GameEngine.newGame(1L)
        assertEquals(50, s0.coins)
        val r = GameEngine.choose(s0, Scenarios.INTRO, 0)
        assertTrue(r.outcome.ok)
        assertEquals(40, r.outcome.state.coins)
        assertEquals(10, r.outcome.state.pNeeds)                  // обед — обязательная трата
        assertTrue(r.outcome.state.introDone)
        assertEquals(Rating.GREAT, r.rating)
    }

    @Test
    fun badIntroChoiceStillKeepsProgressAndExplains() {
        val r = GameEngine.choose(GameEngine.newGame(1L), Scenarios.INTRO, 1)
        assertTrue(r.outcome.ok)
        assertEquals(20, r.outcome.state.coins)
        assertEquals(Rating.BAD, r.rating)
        assertTrue(r.choice!!.explanation.isNotBlank())
        assertTrue(r.outcome.state.food < 100)
    }

    @Test
    fun payslipUses13PercentTax() {
        val s = started()
        val res = GameEngine.work(s, Job.HELPER, taskCorrect = false)
        val slip = res.payslip!!
        assertEquals(35, slip.gross)
        assertEquals(5, slip.tax)          // 13% от 35 = 4,55 → 5
        assertEquals(30, slip.net)
        assertEquals(s.coins + 30, res.outcome.state.coins)
        assertEquals(5, res.outcome.state.totalTax)
        assertTrue(res.outcome.lessons.contains(Lesson.TAX))
    }

    @Test
    fun correctTaskGivesBonusAndSkill() {
        val s = started()
        val slip = GameEngine.work(s, Job.HELPER, taskCorrect = true).payslip!!
        assertEquals(20, slip.taskBonusPercent)
        assertEquals(42, slip.gross)       // 35 × 120% = 42
        val st = GameEngine.work(s, Job.HELPER, taskCorrect = true).outcome.state
        assertEquals(1, st.skillPoints(Skill.CREATOR))
    }

    @Test
    fun tiredMontikEarnsLess() {
        val fresh = started()
        val tired = fresh.copy(energy = 20)
        val a = GameEngine.work(fresh, Job.HELPER, false).payslip!!
        val b = GameEngine.work(tired, Job.HELPER, false).payslip!!
        assertTrue(b.gross < a.gross)
        assertEquals(40, b.efficiencyPercent)
    }

    @Test
    fun sellerNeedsShiftsAndClothes() {
        var s = started().copy(coins = 500)
        assertFalse(GameEngine.jobStatus(s, Job.SELLER).unlocked)
        s = GameEngine.buyClothing(s, "cap").state
        s = GameEngine.buyClothing(s, "tshirt").state
        assertFalse(GameEngine.jobStatus(s, Job.SELLER).unlocked)   // ещё нет смен
        repeat(3) { s = GameEngine.work(s.copy(energy = 100, food = 100, water = 100), Job.HELPER, true).outcome.state }
        assertTrue(GameEngine.jobStatus(s, Job.SELLER).unlocked)
        assertTrue(GameEngine.work(s, Job.SELLER, true).payslip!!.gross > 0)
    }

    @Test
    fun workTasksAlwaysHaveOneCorrectAnswer() {
        for (seed in 1L..200L) {
            for (job in Job.values()) {
                val s = GameEngine.newGame(seed).copy(shifts = (seed % 7).toInt(), day = (seed % 11).toInt() + 1)
                val t = GameEngine.makeTask(s, job)
                assertEquals(3, t.options.size)
                assertEquals(3, t.options.toSet().size)
                assertTrue(t.correctIndex in 0..2)
                assertTrue(t.options.all { it.toInt() > 0 })
            }
        }
    }

    @Test
    fun sleepingAtHomeIsFreeBecauseTheFlatIsRented() {
        val s = started().copy(coins = 25, energy = 10)
        val home = GameEngine.sleep(s, SleepPlace.CABIN).state
        assertEquals(25, home.coins)                                  // за ночь не платим
        assertEquals(100, home.energy)
        assertEquals(2, home.day)

        val poor = s.copy(coins = 0)
        val night = GameEngine.sleep(poor, SleepPlace.CABIN).state    // даже без денег — дома
        assertEquals(SleepPlace.CABIN, GameEngine.beginSleep(poor, SleepPlace.CABIN, poor.clockStamp).state.sleep!!.place)
        assertEquals(100, night.energy)
    }

    @Test
    fun diaryAndPlannerBonusFor20PercentSaved() {
        var s = started().copy(energy = 100, food = 100, water = 100)
        val plannerBefore = s.skillPoints(Skill.PLANNER)
        s = GameEngine.work(s, Job.HELPER, false).outcome.state           // +30
        s = GameEngine.depositCushion(s, 10).state                       // отложено 10 ≥ 20% от 30
        val slept = GameEngine.sleep(s, SleepPlace.CABIN).state
        assertEquals(30, slept.diary!!.earned)
        assertEquals(10, slept.diary!!.saved)
        assertEquals(plannerBefore + 1, slept.skillPoints(Skill.PLANNER))
    }

    @Test
    fun cushionCoversUnexpectedExpense() {
        val s = started().copy(coins = 5, cushion = 40)
        val scenario = Scenarios.byId(Scenarios.MISHAP)!!
        assertTrue(GameEngine.canChoose(s, scenario, scenario.choices[0]))
        val r = GameEngine.choose(s.copy(pendingEvent = Scenarios.MISHAP), Scenarios.MISHAP, 0).outcome.state
        assertEquals(0, r.coins)
        assertEquals(20, r.cushion)
        assertNull(r.pendingEvent)

        val broke = s.copy(coins = 5, cushion = 0)
        assertFalse(GameEngine.canChoose(broke, scenario, scenario.choices[0]))
        assertFalse(GameEngine.choose(broke, Scenarios.MISHAP, 0).outcome.ok)
    }

    @Test
    fun montikCannotWorkWithoutEnergyFoodOrWater() {
        val s = started()
        assertNull(GameEngine.workBlocker(s))
        assertTrue(GameEngine.work(s, Job.HELPER, true).outcome.ok)

        val tired = s.copy(energy = Rules.MIN_WORK_ENERGY - 1)
        assertNotNull(GameEngine.workBlocker(tired))
        val tiredShift = GameEngine.work(tired, Job.HELPER, true)
        assertFalse(tiredShift.outcome.ok)
        assertNull(tiredShift.payslip)
        assertEquals(tired.coins, tiredShift.outcome.state.coins)

        val hungry = s.copy(food = Rules.MIN_WORK_FOOD - 1)
        assertNotNull(GameEngine.workBlocker(hungry))
        assertFalse(GameEngine.work(hungry, Job.HELPER, true).outcome.ok)

        val thirsty = s.copy(water = Rules.MIN_WORK_WATER - 1)
        assertNotNull(GameEngine.workBlocker(thirsty))
        assertFalse(GameEngine.work(thirsty, Job.HELPER, true).outcome.ok)

        // Ровно на границе работать уже можно.
        assertNull(GameEngine.workBlocker(s.copy(energy = Rules.MIN_WORK_ENERGY, food = Rules.MIN_WORK_FOOD, water = Rules.MIN_WORK_WATER)))
    }

    @Test
    fun choreSavesPlayerFromDeadEnd() {
        // Ни монет, ни подушки, ни еды: без поручения игра встала бы намертво.
        val stuck = started().copy(coins = 0, cushion = 0, food = 0, water = 0, energy = 100)
        assertNotNull(GameEngine.workBlocker(stuck))
        assertTrue(GameEngine.choreAvailable(stuck))

        var s = GameEngine.doChore(stuck).state
        assertEquals(Rules.CHORE_COINS, s.coins)
        // Одного поручения хватает, чтобы купить хлеб и воду и снова выйти на смену.
        assertFalse(GameEngine.choreAvailable(s))
        s = GameEngine.buyFood(s, "bread").state
        s = GameEngine.buyFood(s, "water").state
        assertNull(GameEngine.workBlocker(s))
        assertTrue(GameEngine.work(s, Job.HELPER, true).outcome.ok)

        // Когда Монтик в порядке, поручения нет.
        assertFalse(GameEngine.choreAvailable(started()))
        assertFalse(GameEngine.doChore(started()).ok)
    }

    @Test
    fun gameHasNoIllnessOrDeath() {
        val banned = listOf("болез", "заболе", "лекарств", "аптек", "умер", "смерт", "погиб", "лечит")
        val texts = mutableListOf<String>()
        for (sc in Scenarios.all) {
            texts += sc.title
            texts += sc.situation
            for (ch in sc.choices) {
                texts += ch.text
                texts += ch.outcome
                texts += ch.explanation
            }
        }
        for (l in Lesson.values()) {
            texts += l.title
            texts += l.text
            texts += l.goal
        }
        for (t in texts) {
            val low = t.lowercase()
            for (word in banned) {
                assertFalse("Текст про болезнь или смерть: «$t»", low.contains(word))
            }
        }
    }

    @Test
    fun cushionMilestonesGiveBankerPointsOnlyOnce() {
        var s = started().copy(coins = 500)
        s = GameEngine.depositCushion(s, 20).state
        assertEquals(1, s.skillPoints(Skill.BANKER))
        s = GameEngine.withdrawCushion(s, 20).state
        s = GameEngine.depositCushion(s, 20).state        // повторно очков нет
        assertEquals(1, s.skillPoints(Skill.BANKER))
        s = GameEngine.depositCushion(s, 100).state
        assertEquals(4, s.skillPoints(Skill.BANKER))
    }

    @Test
    fun clothesWearOutAndCheapOnesFirst() {
        var s = started().copy(coins = 300)
        s = GameEngine.buyClothing(s, "cap").state         // простая кепка: 3 ночи
        s = GameEngine.buyClothing(s, "boots").state       // богатые сапоги: 20 ночей
        assertEquals(2, s.wornCount)
        repeat(3) { s = GameEngine.sleep(s, SleepPlace.BENCH).state }
        assertEquals(Slot.FEET, s.worn.keys.single())      // кепка износилась, сапоги остались
        assertEquals("boots", s.worn[Slot.FEET])
    }

    @Test
    fun tripsUnlockInOrderAndCompleteAfterAllStops() {
        var s = started().copy(coins = 1000)
        assertNotNull(GameEngine.destinationStatus(s, Destinations.byId("kazan")!!))
        assertFalse(GameEngine.startTrip(s, "kazan").ok)
        val start = GameEngine.startTrip(s, "moscow")
        assertTrue(start.ok)
        s = start.state
        assertEquals(850, s.coins)
        var guard = 0
        while (s.trip != null && guard++ < 10) {
            val stop = GameEngine.currentStop(s)!!
            val idx = stop.choices.indexOfFirst { GameEngine.canChoose(s, stop, it) }
            s = GameEngine.choose(s, stop.id, idx).outcome.state
        }
        assertNull(s.trip)
        assertTrue("moscow" in s.completedTrips)
        assertTrue(s.skillPoints(Skill.TRAVELER) >= 3)
        assertNull(GameEngine.destinationStatus(s, Destinations.byId("kazan")!!))
    }

    @Test
    fun everyScenarioHasAnAffordableChoiceEvenWithZeroCoins() {
        val broke = started().copy(coins = 0, cushion = 0)
        // Вступительная ситуация показывается только в начале игры, когда в кошельке 50 монет.
        for (sc in Scenarios.all.filter { it.id != Scenarios.INTRO }) {
            assertTrue("В «${sc.title}» нет варианта без денег", sc.choices.any { GameEngine.canChoose(broke, sc, it) })
            assertTrue(sc.choices.all { it.explanation.isNotBlank() && it.outcome.isNotBlank() })
        }
        for (d in Destinations.all) assertTrue(d.stops.all { Scenarios.byId(it) != null })
    }

    @Test
    fun stateSurvivesSaveAndLoad() {
        var s = started().copy(coins = 400)
        s = GameEngine.buyClothing(s, "hat").state
        s = GameEngine.work(s, Job.HELPER, true).outcome.state
        s = GameEngine.depositCushion(s, 25).state
        s = GameEngine.depositToBank(s, 50).state
        s = GameEngine.startTrip(s, "moscow").state
        s = GameEngine.sleep(s, SleepPlace.CABIN).state
        assertTrue(s.deposit > 50)
        assertTrue(s.tasksCorrect > 0)
        val text = StateCodec.encode(s)
        val back = StateCodec.decode(text)
        assertEquals(s, back)
        assertNull(StateCodec.decode("это не сохранение"))
    }

    @Test
    fun bankPaysInterestOnlyForAFullDay() {
        var s = started().copy(coins = 200)
        assertFalse(GameEngine.depositToBank(s, Rules.MIN_DEPOSIT - 1).ok)   // слишком мало
        assertFalse(GameEngine.depositToBank(s, 500).ok)                     // больше, чем есть

        s = GameEngine.depositToBank(s, 100).state
        assertEquals(100, s.deposit)
        assertEquals(100, s.coins)
        assertEquals(10, GameEngine.dailyInterest(s))
        assertTrue(Medals.DEPOSIT_MADE in s.milestones)

        val slept = GameEngine.sleep(s, SleepPlace.BENCH).state
        assertEquals(110, slept.deposit)

        // Если забрать вклад до конца дня, процентов не будет.
        val early = GameEngine.withdrawFromBank(s, 100).state
        assertEquals(0, early.deposit)
        assertEquals(200, early.coins)
        assertEquals(0, GameEngine.sleep(early, SleepPlace.BENCH).state.deposit)
    }

    @Test
    fun loanIsReturnedWithOverpayAndBlocksDeposits() {
        var s = started()
        val before = s.coins
        s = GameEngine.takeLoan(s).state
        assertEquals(before + Rules.LOAN_AMOUNT, s.coins)
        assertEquals(Rules.LOAN_REPAY, s.debt)
        assertTrue(Rules.LOAN_REPAY > Rules.LOAN_AMOUNT)

        assertFalse(GameEngine.takeLoan(s).ok)                  // второй кредит не дают
        assertFalse(GameEngine.depositToBank(s, 20).ok)         // и копить с долгом нельзя
        assertTrue(GameEngine.currentGoal(s).contains("${Rules.LOAN_REPAY}"))

        s = GameEngine.repayLoan(s, 20).state                   // частями
        assertEquals(Rules.LOAN_REPAY - 20, s.debt)
        assertFalse(Medals.LOAN_REPAID in s.milestones)

        s = s.copy(coins = 100)
        s = GameEngine.repayLoan(s, s.debt).state
        assertEquals(0, s.debt)
        assertTrue(Medals.LOAN_REPAID in s.milestones)
        assertTrue(GameEngine.depositToBank(s, 20).ok)
    }

    @Test
    fun sochiIsTheLastTripAndRestoresStrength() {
        val sochi = Destinations.byId("sochi")!!
        assertEquals(500, sochi.ticket)
        assertEquals("spb", sochi.requires)
        assertEquals(sochi.id, Destinations.all.last().id)

        // Без поездки в Петербург Сочи закрыт, даже если монет хватает.
        var s = started().copy(coins = 2000)
        assertNotNull(GameEngine.destinationStatus(s, sochi))

        s = s.copy(completedTrips = setOf("moscow", "kazan", "spb"), energy = 20, food = 20, water = 20)
        assertNull(GameEngine.destinationStatus(s, sochi))
        s = GameEngine.startTrip(s, "sochi").state
        assertEquals(1500, s.coins)

        var guard = 0
        while (s.trip != null && guard++ < 10) {
            val stop = GameEngine.currentStop(s)!!
            val idx = stop.choices.indexOfFirst { GameEngine.canChoose(s, stop, it) }
            s = GameEngine.choose(s, stop.id, idx).outcome.state
        }
        assertTrue("sochi" in s.completedTrips)
        assertEquals(100, s.energy)                     // отдых у моря восстанавливает силы
        assertTrue(s.food >= 70 && s.water >= 70)
        assertTrue(Lesson.VACATION.name in s.seenLessons)
        assertTrue(GameEngine.currentGoal(s).contains("справился"))
    }

    @Test
    fun medalsAreGivenOnceAndAreNeverLost() {
        val s = started()
        assertTrue(Medals.earned(s).isEmpty())

        val firstShift = GameEngine.work(s, Job.HELPER, true)
        val medal = firstShift.outcome.medals.firstOrNull { it.id == "first_pay" }
        assertNotNull(medal)

        // Та же медаль второй раз не выдаётся.
        val secondShift = GameEngine.work(firstShift.outcome.state, Job.HELPER, true)
        assertTrue(secondShift.outcome.medals.none { it.id == "first_pay" })

        // Медаль за подушку остаётся, даже если подушку потом потратить.
        var rich = s.copy(coins = 500)
        rich = GameEngine.depositCushion(rich, Rules.CUSHION_GOAL).state
        assertTrue(Medals.earned(rich).any { it.id == "saver" })
        val spent = GameEngine.withdrawCushion(rich, rich.cushion).state
        assertEquals(0, spent.cushion)
        assertTrue(Medals.earned(spent).any { it.id == "saver" })

        // У каждой медали есть понятная подсказка и уникальный id.
        assertEquals(Medals.all.size, Medals.all.map { it.id }.toSet().size)
        assertTrue(Medals.all.all { it.hint.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun heroNameAndColoursSurviveSaveAndLoad() {
        var s = started()
        assertEquals(Hero.DEFAULT_NAME, s.heroName)
        // Новая игра начинается с белого человечка: все части белые, раскраска пустая.
        assertEquals(HeroPreset.WHITE, s.preset)
        assertEquals(0xFFFFFF, s.heroColor(HeroPart.FUR))
        assertTrue(s.heroColors.isEmpty())

        s = s.copy(
            heroPreset = HeroPreset.BLUE.id,
            heroName = Hero.cleanName("  Пушистик  "),
            heroColors = mapOf(HeroPart.FUR.id to 0xE01B1B, HeroPart.CROWN.id to 0x1616D8)
        )
        assertEquals("Пушистик", s.heroName)
        assertEquals(0xE01B1B, s.heroColor(HeroPart.FUR))
        assertEquals(HeroPart.EARS.default, s.heroColor(HeroPart.EARS))
        assertEquals(HeroPreset.BLUE.id, StateCodec.decode(StateCodec.encode(s))!!.heroPreset)

        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s.heroName, back.heroName)
        assertEquals(s.heroColors, back.heroColors)

        // Пустое имя превращается в имя по умолчанию, слишком длинное обрезается.
        assertEquals(Hero.DEFAULT_NAME, Hero.cleanName("   "))
        assertTrue(Hero.cleanName("ОченьДлинноеИмяГероя").length <= Hero.MAX_NAME)
        assertEquals(7, Hero.PALETTE.size)
    }

    @Test
    fun heroPresetsAreDistinctAndSafeToLoad() {
        // Первая заготовка — белый человечек, дальше синий и зелёный, как в макете.
        assertEquals(HeroPreset.WHITE, HeroPreset.DEFAULT)
        assertTrue(HeroPreset.WHITE.isBlank)
        assertTrue(HeroPreset.values().size >= 4)
        assertEquals(HeroPreset.values().size, HeroPreset.values().map { it.id }.toSet().size)
        assertEquals(HeroPreset.values().size, HeroPreset.values().map { it.color(HeroPart.FUR) }.toSet().size)
        assertEquals(HeroPreset.BLUE, HeroPreset.byId("blue"))
        assertEquals(HeroPreset.GREEN, HeroPreset.byId("green"))
        assertNull(HeroPreset.byId("нет-такого"))
        assertEquals(HeroPart.FUR.default, HeroPreset.BLUE.color(HeroPart.FUR))

        // Раскраска поверх заготовки: перекрашенная часть своя, остальные — цвета заготовки.
        val s = started().copy(heroPreset = HeroPreset.GREEN.id, heroColors = mapOf(HeroPart.CROWN.id to 0xE01B1B))
        assertEquals(0xE01B1B, s.heroColor(HeroPart.CROWN))
        assertEquals(HeroPreset.GREEN.color(HeroPart.FUR), s.heroColor(HeroPart.FUR))
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(HeroPreset.GREEN.id, back.heroPreset)

        // Неизвестная заготовка в сохранении не ломает игру: до создания героя это белый человечек.
        val broken = StateCodec.encode(s.copy(created = false)).replace("heroPreset=green", "heroPreset=xxx")
        assertEquals(HeroPreset.WHITE, StateCodec.decode(broken)!!.preset)

        // Старое сохранение без заготовки: Монтик был синим — таким и остаётся.
        val old = StateCodec.encode(s.copy(created = true)).lines().filterNot { it.startsWith("heroPreset") }.joinToString("\n")
        assertEquals(HeroPreset.BLUE, StateCodec.decode(old)!!.preset)
    }

    // ───────────────────────── Работа в магазине ─────────────────────────

    /** Монтик, который уже отработал [shifts] смен и может выйти в магазин. */
    private fun shopper(shopShifts: Int = 0): GameState =
        started().copy(shifts = 3 + shopShifts, shopShifts = shopShifts)

    @Test
    fun shopOpensAfterThreeShiftsAndPaysByRank() {
        val fresh = started()
        assertFalse(ShopWork.unlocked(fresh))
        assertNotNull(ShopWork.lockHint(fresh))
        val blocked = GameEngine.workShop(fresh, ShopGame.CASHIER, 100)
        assertFalse(blocked.outcome.ok)

        // Должности растут вместе с числом смен, ставка тоже.
        assertEquals(ShopRank.TRAINEE, ShopWork.rank(0))
        assertEquals(ShopRank.TRAINEE, ShopWork.rank(4))
        assertEquals(ShopRank.CASHIER, ShopWork.rank(5))
        assertEquals(ShopRank.SENIOR, ShopWork.rank(12))
        assertEquals(ShopRank.MANAGER, ShopWork.rank(22))
        assertEquals(ShopRank.MANAGER, ShopWork.rank(500))
        assertTrue(ShopRank.values().map { it.base }.zipWithNext().all { (a, b) -> a < b })
        assertNull(ShopRank.MANAGER.next)
        assertEquals(5, ShopWork.shiftsToNextRank(shopper(0)))

        // Стажёр, идеальная смена: 80 × (100% + 50% премии) = 120, налог 13% = 16, на руки 104.
        val s = shopper()
        assertTrue(ShopWork.unlocked(s))
        val res = GameEngine.workShop(s, ShopGame.CASHIER, 100)
        assertTrue(res.outcome.ok)
        val slip = res.payslip!!
        assertEquals(120, slip.gross)
        assertEquals(16, slip.tax)
        assertEquals(104, slip.net)
        assertEquals(s.coins + 104, res.outcome.state.coins)
        assertEquals(1, res.outcome.state.shopShifts)
        assertEquals(s.shifts + 1, res.outcome.state.shifts)
        assertTrue(res.outcome.state.energy < s.energy)
        assertTrue(res.outcome.state.clockMinutes > s.clockMinutes)

        // Без премии — ровно ставка.
        val plain = GameEngine.workShop(s, ShopGame.SHELVES, 0).payslip!!
        assertEquals(80, plain.gross)
        // Уставший и голодный Монтик получает меньше.
        val tired = GameEngine.workShop(s.copy(energy = 40), ShopGame.CASHIER, 100).payslip!!
        assertTrue(tired.gross < slip.gross)
    }

    @Test
    fun shopShiftPromotesMontikAndRaisesThePay() {
        val s = shopper(4)                                   // пятая смена сделает его кассиром
        val res = GameEngine.workShop(s, ShopGame.SHELVES, 80)
        assertEquals(ShopRank.CASHIER, ShopWork.rank(res.outcome.state))
        assertTrue(res.outcome.messages.any { it.contains("повысили") })
        val next = GameEngine.workShop(res.outcome.state.copy(energy = 100, food = 100, water = 100), ShopGame.CASHIER, 80)
        assertTrue(next.payslip!!.base > res.payslip!!.base)
        assertTrue(Medals.earned(next.outcome.state).any { it.id == "cashier" })
    }

    @Test
    fun cashierCustomersAreRepeatableAndTheirChangeIsCorrect() {
        val s = shopper(6)                                   // кассир: сдачу нужно считать
        val a = ShopWork.customers(s)
        val b = ShopWork.customers(s)
        assertEquals(a, b)
        assertEquals(ShopWork.rank(s).customers, a.size)
        for (c in a) {
            assertTrue(c.items.size in 2..ShopWork.rank(s).maxItems)
            assertEquals(c.items.size, c.items.map { it.id }.toSet().size)
            assertTrue(c.paid > c.total)
            assertEquals(3, c.changeOptions.size)
            assertEquals(3, c.changeOptions.toSet().size)
            assertTrue(c.changeOptions.all { it > 0 })
            assertEquals(c.change, c.changeOptions[c.correctChangeIndex])
        }
        // Другой день — другие покупатели.
        assertTrue(ShopWork.customers(s.copy(day = 9)) != a)
    }

    @Test
    fun shelfBasketHasOneProductPerShelf() {
        for (shifts in listOf(0, 5, 12, 22)) {
            val s = shopper(shifts)
            val basket = ShopWork.shelfBasket(s)
            val layout = ShopWork.standLayout(s)
            val expected = if (layout == StandLayout.BIG) minOf(6, ShopWork.rank(s).shelfItems + 1) else ShopWork.rank(s).shelfItems
            assertEquals(expected, basket.size)
            assertTrue(basket.all { layout.slotFor(it) >= 0 })
            assertEquals(basket.size, basket.map { layout.slotFor(it) }.toSet().size)
            assertTrue(basket.all { it.shelf != null })
            assertEquals(basket.size, basket.map { it.shelf }.toSet().size)
            assertEquals(basket, ShopWork.shelfBasket(s))
        }
        // У каждой полки есть хотя бы один товар, у каждого товара — понятная цена.
        for (shelf in ShelfCategory.values()) assertTrue(ShopCatalog.shelved.any { it.shelf == shelf })
        assertTrue(ShopCatalog.products.all { it.price > 0 && it.name.isNotBlank() })
        assertEquals(ShopCatalog.products.size, ShopCatalog.products.map { it.id }.toSet().size)
    }

    @Test
    fun shopPerformanceRewardsAttentionAndSpeed() {
        val fast = CustomerResult(items = 3, doubleScans = 0, changeCorrect = true, millis = 5_000, parMillis = 20_000)
        val perfect = ShopWork.cashierPerformance(listOf(fast, fast, fast))
        assertEquals(100, perfect)
        // Ошибки: дважды пробитый товар и неверная сдача.
        val sloppy = fast.copy(doubleScans = 2, changeCorrect = false)
        assertTrue(ShopWork.cashierPerformance(listOf(sloppy, fast, fast)) < perfect)
        assertTrue(ShopWork.cashierPerformance(listOf(sloppy)) < ShopWork.cashierPerformance(listOf(fast.copy(changeCorrect = false))))
        // Медленная работа теряет только часть оценки, а не всю.
        val slow = fast.copy(millis = 200_000)
        val slowScore = ShopWork.cashierPerformance(listOf(slow))
        assertTrue(slowScore in 60..75)
        assertEquals(0, ShopWork.cashierPerformance(emptyList()))

        assertEquals(100, ShopWork.shelvesPerformance(5, 0, 1_000))
        assertTrue(ShopWork.shelvesPerformance(5, 3, 1_000) < 70)
        assertTrue(ShopWork.shelvesPerformance(5, 0, 500_000) in 60..75)
        assertEquals(0, ShopWork.shelvesPerformance(0, 0, 0))
        assertEquals(1f, ShopWork.speedFactor(1, 10))
        assertEquals(0f, ShopWork.speedFactor(1_000, 10))
    }

    // ───────────────────────── Свой бизнес ─────────────────────────

    /** Монтик с 25 000 монет, готовый открыть магазин. */
    private fun richMontik(coins: Int = 25_000): GameState {
        val s = started().copy(coins = coins)
        return GameEngine.workShop(s.copy(shifts = 3), ShopGame.CASHIER, 50).outcome.state.copy(coins = coins)
    }

    @Test
    fun businessIdeaAppearsWhenTwentyThousandIsSaved() {
        val poor = started()
        assertFalse(BusinessEngine.offerPending(poor))
        assertFalse(BusinessEngine.canStart(poor))
        assertTrue(BusinessEngine.goal(poor).contains("20000") || BusinessEngine.goal(poor).contains("20 000"))

        // Накопления считаются целиком: кошелёк, подушка и вклад за вычетом долга.
        assertEquals(19_500, BusinessEngine.wealth(poor.copy(coins = 10_000, cushion = 5_000, deposit = 5_000, debt = 500)))

        // Метка ставится, когда действие закончено — например, вышли на смену.
        val rich = richMontik()
        assertTrue(BusinessEngine.WEALTH_MARK in rich.milestones)
        assertTrue(BusinessEngine.offerPending(rich))
        assertTrue(Medals.earned(rich).any { it.id == "rich20k" })

        // «Пока рано»: идея больше не всплывает сама, но начать можно позже.
        val later = BusinessEngine.postpone(rich).state
        assertFalse(BusinessEngine.offerPending(later))
        assertTrue(BusinessEngine.canStart(later))
        // Деньги потратили: метка остаётся, медаль не пропадает.
        val spent = later.copy(coins = 10)
        assertTrue(BusinessEngine.canStart(spent))
        assertTrue(Medals.earned(spent).any { it.id == "rich20k" })
    }

    @Test
    fun businessGoesThroughAllStagesInOrder() {
        var s = richMontik()
        // Нельзя перескочить этапы.
        assertFalse(BusinessEngine.rentPlace(s, "center").ok)
        assertFalse(BusinessEngine.buyStock(s, 40).ok)

        s = BusinessEngine.begin(s).also { assertTrue(it.ok) }.state
        assertEquals(BizStage.PLACE, s.business!!.stage)
        assertTrue(s.seenLessons.contains(Lesson.INVEST.name))
        assertFalse(BusinessEngine.chooseType(s, "food").ok)   // сначала место
        assertFalse(BusinessEngine.build(s).ok)

        val before = s.coins
        s = BusinessEngine.rentPlace(s, "district").also { assertTrue(it.ok) }.state
        assertEquals(before - BizPlace.DISTRICT.price, s.coins)
        assertEquals(BizStage.TYPE, s.business!!.stage)

        s = BusinessEngine.chooseType(s, "toys").also { assertTrue(it.ok) }.state
        assertEquals(BizStage.BUILD, s.business!!.stage)

        val beforeBuild = s.coins
        s = BusinessEngine.build(s).also { assertTrue(it.ok) }.state
        assertEquals(beforeBuild - BizPlace.DISTRICT.buildCost, s.coins)
        assertEquals(BizStage.EQUIP, s.business!!.stage)

        s = BusinessEngine.buyEquipment(s, 2).also { assertTrue(it.ok) }.state
        assertEquals(2, s.business!!.equip)
        assertEquals(BizStage.STOCK, s.business!!.stage)
        assertFalse(s.business!!.isOpen)

        val beforeStock = s.coins
        s = BusinessEngine.buyStock(s, 80).also { assertTrue(it.ok) }.state
        val b = s.business!!
        assertTrue(b.isOpen)
        assertEquals(80, b.stock)
        assertEquals(beforeStock - BizType.TOYS.unitCost * 80, s.coins)
        assertTrue(Medals.earned(s).any { it.id == "owner" })
        assertTrue(s.skillPoints(Skill.ENTREPRENEUR) > 0)

        // Всё сохраняется и загружается без потерь.
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(b, back.business)
    }

    @Test
    fun businessRefusesWhenThereIsNotEnoughMoney() {
        var s = BusinessEngine.begin(richMontik()).state
        val poor = s.copy(coins = 100)
        val r = BusinessEngine.rentPlace(poor, "center")
        assertFalse(r.ok)
        assertEquals(100, r.state.coins)
        assertEquals(BizStage.PLACE, r.state.business!!.stage)

        s = BusinessEngine.rentPlace(s, "alley").state
        s = BusinessEngine.chooseType(s, "food").state
        s = BusinessEngine.build(s).state
        assertFalse(BusinessEngine.buyEquipment(s.copy(coins = 10), 3).ok)
        assertFalse(BusinessEngine.buyEquipment(s, 9).ok)
    }

    /** Открытый магазин: переулок, продукты, простое оборудование, товара на 80 покупателей. */
    private fun openedShop(place: String = "district", type: String = "food", equip: Int = 1, stock: Int = 200): GameState {
        var s = BusinessEngine.begin(richMontik(60_000)).state
        s = BusinessEngine.rentPlace(s, place).state
        s = BusinessEngine.chooseType(s, type).state
        s = BusinessEngine.build(s).state
        s = BusinessEngine.buyEquipment(s, equip).state
        s = BusinessEngine.buyStock(s, stock).state
        assertTrue(s.business!!.isOpen)
        return s
    }

    @Test
    fun aDayOfTradingProducesAReportAndMoneyInTheTill() {
        val s = openedShop(stock = 200)
        val night = GameEngine.sleep(s, SleepPlace.CABIN).state
        val b = night.business!!
        val r = b.report!!
        assertEquals(1, b.daysOpen)
        assertTrue(r.served > 0)
        assertTrue(r.served <= BusinessEngine.capacity(b.copy(broken = false)))
        assertEquals(r.served * BizType.FOOD.basket, r.revenue)
        assertEquals(r.served * BizType.FOOD.unitCost, r.goodsCost)
        assertEquals(r.revenue - r.goodsCost - r.expenses, r.profit)
        assertEquals(200 - r.served, b.stock)
        assertEquals(b.totalRevenue, r.revenue)
        assertEquals(b.totalProfit, r.profit)
        // Расходы уже вычтены из кассы, остальное лежит там до снятия.
        assertEquals(r.revenue - r.expenses, b.till)
        assertTrue(night.seenLessons.contains(Lesson.PROFIT.name))

        // Снятие из кассы: налог 6%, остальное — деньги Монтика.
        val coinsBefore = night.coins
        val res = BusinessEngine.collect(night)
        assertTrue(res.ok)
        val expectedTax = BusinessEngine.tax(b.till)
        assertEquals(coinsBefore + b.till - expectedTax, res.state.coins)
        assertEquals(0, res.state.business!!.till)
        assertFalse(BusinessEngine.collect(res.state).ok)   // касса пуста
    }

    @Test
    fun runningOutOfGoodsLosesCustomers() {
        val s = openedShop(stock = 40)                          // товара — на один день
        val night = GameEngine.sleep(s.copy(seed = 5L), SleepPlace.CABIN).state
        val second = GameEngine.sleep(night, SleepPlace.CABIN).state   // на второй день товара нет
        val r = second.business!!.report!!
        assertEquals(0, r.served)
        assertEquals(0, r.revenue)
        assertTrue(r.lost > 0)
        assertTrue(second.business!!.stock == 0)
        assertTrue(second.seenLessons.contains(Lesson.STOCK.name))
        // Аренда всё равно платится: из кассы, потом из кошелька.
        assertTrue(r.expenses > 0)
    }

    @Test
    fun restockUpgradeAdvertiseAndExpandCostMoney() {
        val s = openedShop(equip = 1, stock = 40)
        val b0 = s.business!!
        val stock = BusinessEngine.buyStock(s, 100)
        assertTrue(stock.ok)
        assertEquals(140, stock.state.business!!.stock)
        assertEquals(s.coins - BizType.FOOD.unitCost * 100, stock.state.coins)

        // Улучшение оборудования — только разница в цене, и только вверх.
        val up = BusinessEngine.buyEquipment(s, 3)
        assertTrue(up.ok)
        assertEquals(s.coins - (BizEquip.FINE.price - BizEquip.BASIC.price), up.state.coins)
        assertEquals(3, up.state.business!!.equip)
        assertFalse(BusinessEngine.buyEquipment(up.state, 2).ok)
        assertTrue(BusinessEngine.capacity(up.state.business!!) > BusinessEngine.capacity(b0))

        val ad = BusinessEngine.advertise(s)
        assertTrue(ad.ok)
        assertEquals(BusinessEngine.AD_DAYS, ad.state.business!!.adDays)
        assertFalse(BusinessEngine.advertise(ad.state).ok)       // уже идёт
        assertTrue(BusinessEngine.demand(ad.state.business!!) >= BusinessEngine.demand(b0))

        val big = BusinessEngine.expand(s)
        assertTrue(big.ok)
        assertEquals(1, big.state.business!!.expansion)
        assertTrue(BusinessEngine.dailyRent(big.state.business!!) > BusinessEngine.dailyRent(b0))
        val bigger = BusinessEngine.expand(BusinessEngine.expand(big.state).state)
        assertFalse(bigger.ok)                                   // предел расширения

        // Чинить нечего, пока ничего не сломалось.
        assertFalse(BusinessEngine.repair(s).ok)
        val broken = s.copy(business = b0.copy(broken = true))
        assertTrue(BusinessEngine.capacity(broken.business!!) < BusinessEngine.capacity(b0))
        val fixed = BusinessEngine.repair(broken)
        assertTrue(fixed.ok)
        assertFalse(fixed.state.business!!.broken)
    }

    @Test
    fun everyKindOfShopCanEarnAProfit() {
        // При нормальном оборудовании и запасе магазин любого вида приносит прибыль в обычный день.
        for (place in BizPlace.values()) {
            for (type in BizType.values()) {
                val s = openedShop(place.id, type.id, equip = 2, stock = 200)
                val profit = BusinessEngine.expectedProfit(s.business!!)
                println("${place.title} / ${type.title}, оборудование 2: ожидаемая прибыль в день $profit")
                assertTrue("${place.title}/${type.title}: $profit", profit > 0)
            }
        }
    }

    @Test
    fun aFullShopPaysOffItsOwnBuildingInReasonableTime() {
        // Средний путь: спальный район, продукты, хорошее оборудование, запас на 100 покупателей.
        var s = openedShop("district", "food", equip = 2, stock = 100)
        val invested = 60_000 - s.coins
        var days = 0
        var totalProfit = 0
        while (days < 60 && totalProfit < invested) {
            val stockNeed = BusinessEngine.expectedServed(s.business!!) * 3
            if (s.business!!.stock < stockNeed) s = BusinessEngine.buyStock(s, stockNeed).state
            s = GameEngine.sleep(s.copy(food = 100, water = 100), SleepPlace.CABIN).state
            totalProfit = s.business!!.totalProfit
            days++
        }
        println("Магазин окупился за $days дн. (вложено $invested)")
        assertTrue("Не окупился за 60 дней", days < 60)
        assertTrue("Окупился слишком быстро: $days дн.", days >= 5)
    }

    @Test
    fun aPlayerCanSaveTwentyThousandForABusiness() {
        val days = simulateToTwentyThousand()
        println("Бот накопил 20 000 монет за $days дн.")
        assertTrue("Слишком долго: $days дн.", days in 5..45)
    }

    /** «Идеальный» игрок в магазине: ест, работает, всё лишнее кладёт на вклад. */
    private fun simulateToTwentyThousand(): Int {
        var s = GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(11L)), Scenarios.INTRO, 0).outcome.state
        for (guard in 1..80) {
            s.pendingEvent?.let { id ->
                val sc = Scenarios.byId(id)!!
                val i = sc.choices.indexOfFirst { GameEngine.canChoose(s, sc, it) }
                s = GameEngine.choose(s, id, i).outcome.state
            }
            while (s.food < 60 && s.coins >= 10) s = GameEngine.buyFood(s, "lunch").state
            while (s.water < 60 && s.coins >= 3) s = GameEngine.buyFood(s, "water").state
            while (s.energy >= 55 && GameEngine.workBlocker(s) == null) {
                val res = if (ShopWork.unlocked(s)) {
                    GameEngine.workShop(s, if (s.shopShifts % 2 == 0) ShopGame.CASHIER else ShopGame.SHELVES, 85)
                } else {
                    GameEngine.work(s, Job.HELPER, true)
                }
                s = res.outcome.state
                if (BusinessEngine.wealth(s) >= BusinessEngine.GOAL_COINS) return s.day
                // Между сменами Монтик ест и пьёт, чтобы работать дальше.
                while (s.food < 40 && s.coins >= 10) s = GameEngine.buyFood(s, "lunch").state
                while (s.water < 40 && s.coins >= 3) s = GameEngine.buyFood(s, "water").state
            }
            // Всё, что больше запаса на еду и ночлег, — на вклад: проценты работают на Монтика.
            val keep = 120
            if (s.debt == 0 && s.coins > keep + Rules.MIN_DEPOSIT) s = GameEngine.depositToBank(s, s.coins - keep).state
            s = GameEngine.sleep(s, SleepPlace.CABIN).state
            if (BusinessEngine.wealth(s) >= BusinessEngine.GOAL_COINS) return s.day
        }
        return 999
    }

    @Test
    fun aPlayerCanReachMoscowInReasonableTime() {
        val days = simulateToMoscow()
        println("Бот дошёл до поездки в Москву за $days дн.")
        assertTrue("Слишком долго: $days дн.", days in 1..25)
    }

    /** Простой «идеальный» игрок: ест, работает, откладывает 20%, одевается и едет в Москву. */
    private fun simulateToMoscow(): Int {
        var s = GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(7L)), Scenarios.INTRO, 0).outcome.state
        for (guard in 1..60) {
            s.pendingEvent?.let { id ->
                val sc = Scenarios.byId(id)!!
                val i = sc.choices.indexOfFirst { GameEngine.canChoose(s, sc, it) }
                s = GameEngine.choose(s, id, i).outcome.state
            }
            while (s.food < 60 && s.coins >= 10) s = GameEngine.buyFood(s, "lunch").state
            while (s.water < 60 && s.coins >= 3) s = GameEngine.buyFood(s, "water").state
            var earnedToday = 0
            while (s.energy >= 55 && GameEngine.workBlocker(s) == null) {
                val job = if (GameEngine.jobStatus(s, Job.SELLER).unlocked) Job.SELLER else Job.HELPER
                val res = GameEngine.work(s, job, true)
                earnedToday += res.payslip!!.net
                s = res.outcome.state
            }
            if (s.wornCount < 2 && s.coins >= 22) {
                s = GameEngine.buyClothing(s, "cap").state
                s = GameEngine.buyClothing(s, "tshirt").state
            }
            val save = earnedToday / 5
            if (save > 0 && s.coins >= save) s = GameEngine.depositCushion(s, save).state
            if (s.coins >= 150 + 45) {
                s = GameEngine.startTrip(s, "moscow").state
                while (s.trip != null) {
                    val stop = GameEngine.currentStop(s)!!
                    val i = stop.choices.indexOfFirst { it.rating == Rating.GREAT && GameEngine.canChoose(s, stop, it) }
                    val j = if (i >= 0) i else stop.choices.indexOfFirst { GameEngine.canChoose(s, stop, it) }
                    s = GameEngine.choose(s, stop.id, j).outcome.state
                }
                return s.day
            }
            s = GameEngine.sleep(s, SleepPlace.CABIN).state
        }
        return 999
    }

    // ───────────────────────── Сон и виртуальное время ─────────────────────────

    private val t0 = 1_000_000L
    private val realMin = 60_000L

    /** Сколько настоящих миллисекунд должно пройти, чтобы у Монтика прошло [virtualMinutes] виртуальных минут. */
    private fun real(virtualMinutes: Int): Long = virtualMinutes * realMin / Rules.VIRTUAL_PER_REAL

    /** Игра, у которой виртуальные часы показывают [atMinutes] в настоящий миг [t0]. */
    private fun clocked(atMinutes: Long, base: GameState = started()): GameState =
        base.copy(clockMinutes = atMinutes, clockStamp = t0)

    @Test
    fun virtualClockRunsHundredFiftyTimesFasterThanReal() {
        assertEquals(150, Rules.VIRTUAL_PER_REAL)                           // 1 минута = 150 виртуальных (в 3 раза быстрее прежнего)
        val s = clocked(420L)
        assertEquals(420L, VirtualClock.now(s, t0))
        assertEquals(570L, VirtualClock.now(s, t0 + realMin))
        assertEquals(420L + 600L, VirtualClock.now(s, t0 + 4 * realMin))    // 4 минуты = 10 часов
    }

    @Test
    fun clockKeepsRunningWhileAppIsClosed() {
        val s = clocked(420L)
        val restored = StateCodec.decode(StateCodec.encode(s))!!
        // Игру закрыли и открыли через 2 минуты: прошло 300 виртуальных минут.
        assertEquals(720L, VirtualClock.now(restored, t0 + 2 * realMin))
    }

    @Test
    fun daytimeBedIsEveningAndAlarmIsNextMorning() {
        val day = VirtualClock.planSleep(10 * 60L)                        // 10:00 первого дня
        assertEquals(21 * 60L, day.bedV)
        assertEquals(1440L + 420L, day.alarmV)
        assertEquals(600, day.minutes)

        val evening = VirtualClock.planSleep(1440L + 23 * 60L)            // 23:00 второго дня
        assertEquals(1440L + 23 * 60L, evening.bedV)
        assertEquals(2 * 1440L + 420L, evening.alarmV)

        val lateNight = VirtualClock.planSleep(1440L + 3 * 60L)           // 03:00 — будильник в тот же день
        assertEquals(1440L + 3 * 60L, lateNight.bedV)
        assertEquals(1440L + 420L, lateNight.alarmV)
        assertEquals(240, lateNight.minutes)
    }

    @Test
    fun sleepTimerShowsSleptAndRemainingHours() {
        val s = clocked(21 * 60L).copy(coins = 30)
        val r = GameEngine.beginSleep(s, SleepPlace.CABIN, t0)
        assertTrue(r.ok)
        val b = r.state
        assertNotNull(b.sleep)
        assertEquals(30, b.coins)                                         // ночь дома бесплатная

        val start = GameEngine.sleepStatus(b, t0)!!
        assertEquals(0, start.sleptMinutes)
        assertEquals(600, start.remainingMinutes)
        assertFalse(start.ringing)

        val mid = GameEngine.sleepStatus(b, t0 + real(300))!!         // 5 часов сна
        assertEquals(300, mid.sleptMinutes)
        assertEquals(300, mid.remainingMinutes)
        assertTrue(kotlin.math.abs(mid.progress - 0.5f) < 0.001f)

        val end = GameEngine.sleepStatus(b, t0 + real(600))!!
        assertTrue(end.ringing)
        assertEquals(0, end.remainingMinutes)
        assertEquals(7 * 60, end.alarmTimeOfDay)
    }

    @Test
    fun sleepingMontikCannotWorkOrLieDownTwice() {
        val b = GameEngine.beginSleep(clocked(21 * 60L), SleepPlace.CABIN, t0).state
        assertNotNull(GameEngine.workBlocker(b))
        assertFalse(GameEngine.beginSleep(b, SleepPlace.CABIN, t0).ok)
    }

    @Test
    fun snoozeMakesAlarmRingAgainAfterFifteenVirtualMinutes() {
        val b = GameEngine.beginSleep(clocked(21 * 60L), SleepPlace.CABIN, t0).state
        val alarmAt = t0 + real(600)                                       // 07:00
        assertFalse(GameEngine.snooze(b, t0 + real(100)).ok)            // будильник ещё не звонил

        val z = GameEngine.snooze(b, alarmAt)
        assertTrue(z.ok)
        assertEquals(1, z.state.sleep!!.snoozes)
        val soon = GameEngine.sleepStatus(z.state, alarmAt + real(10))!!   // прошло 10 виртуальных минут
        assertFalse(soon.ringing)
        assertEquals(5, soon.remainingMinutes)
        val again = GameEngine.sleepStatus(z.state, alarmAt + real(15))!!  // 15 виртуальных минут
        assertTrue(again.ringing)
        assertEquals(615, again.sleptMinutes)                              // Монтик спал всё это время
    }

    @Test
    fun goodSleepRestoresEnergyAndMakesShiftsCheaper() {
        val s = clocked(21 * 60L).copy(energy = 20)
        val b = GameEngine.beginSleep(s, SleepPlace.CABIN, t0).state
        val w = GameEngine.wakeUp(b, t0 + real(600))
        assertTrue(w.ok)
        val awake = w.state
        assertNull(awake.sleep)
        assertEquals(100, awake.energy)
        assertEquals(SleepQuality.RESTED, awake.sleepQuality)
        assertEquals(2, awake.day)
        assertEquals(Rules.RESTED_SHIFT_ENERGY, GameEngine.shiftEnergy(awake))
        assertEquals(1440L + 420L, VirtualClock.now(awake, t0 + real(600)))   // снова 07:00

        // Выспавшийся Монтик успевает больше смен, чем обычный.
        fun shiftsUntilTired(start: GameState): Int {
            var t = start.copy(food = 100, water = 100)
            var n = 0
            while (GameEngine.workBlocker(t) == null) {
                t = GameEngine.work(t, Job.HELPER, false).outcome.state.copy(food = 100, water = 100)
                n++
            }
            return n
        }
        val normal = awake.copy(sleepQuality = SleepQuality.NORMAL)
        assertEquals(4, shiftsUntilTired(awake))
        assertEquals(3, shiftsUntilTired(normal))
    }

    @Test
    fun shortSleepLeavesMontikTiredAndShiftsCostMore() {
        // Монтик не ложился до 03:00 — до будильника остаётся всего 4 часа.
        val s = clocked(3 * 60L).copy(energy = 20)
        val b = GameEngine.beginSleep(s, SleepPlace.CABIN, t0).state
        assertEquals(240, GameEngine.sleepStatus(b, t0)!!.totalMinutes)
        val w = GameEngine.wakeUp(b, t0 + real(240)).state
        assertEquals(SleepQuality.TIRED, w.sleepQuality)
        assertEquals(50, w.energy)                                          // 100 × 4 ч / 8 ч
        assertEquals(Rules.TIRED_SHIFT_ENERGY, GameEngine.shiftEnergy(w))
        val after = GameEngine.work(w.copy(food = 100, water = 100), Job.HELPER, false).outcome.state
        assertEquals(50 - Rules.TIRED_SHIFT_ENERGY, after.energy)
    }

    @Test
    fun wakingEarlyRestoresOnlyPartOfEnergy() {
        val s = clocked(21 * 60L).copy(energy = 20)
        val b = GameEngine.beginSleep(s, SleepPlace.CABIN, t0).state
        val w = GameEngine.wakeUp(b, t0 + real(300)).state              // проспал 5 часов
        assertEquals(SleepQuality.TIRED, w.sleepQuality)
        assertEquals(62, w.energy)
        assertEquals(2, w.day)
        // Разбудили сразу после того, как лёг: энергия не падает ниже прежней.
        val quick = GameEngine.wakeUp(GameEngine.beginSleep(s, SleepPlace.CABIN, t0).state, t0).state
        assertEquals(20, quick.energy)
    }

    @Test
    fun benchSleepIsNeverRested() {
        val s = clocked(21 * 60L).copy(energy = 10, coins = 0)
        val b = GameEngine.beginSleep(s, SleepPlace.BENCH, t0).state       // скамейка — только в старых сохранениях
        assertEquals(SleepPlace.BENCH, b.sleep!!.place)
        val w = GameEngine.wakeUp(b, t0 + real(600)).state
        assertEquals(SleepQuality.NORMAL, w.sleepQuality)
        assertEquals(Rules.BENCH_ENERGY, w.energy)
    }

    @Test
    fun sleepingStateAndQualitySurviveSaveAndLoad() {
        val b = GameEngine.beginSleep(clocked(21 * 60L), SleepPlace.CABIN, t0).state
        val back = StateCodec.decode(StateCodec.encode(b))!!
        assertEquals(b.sleep, back.sleep)
        assertEquals(b.clockMinutes, back.clockMinutes)
        assertEquals(b.clockStamp, back.clockStamp)
        // Приложение открыли через полчаса: Монтик проспал 5 часов, будильник ещё не звонит.
        val status = GameEngine.sleepStatus(back, t0 + real(300))!!
        assertEquals(300, status.sleptMinutes)

        val awake = GameEngine.wakeUp(b, t0 + real(600)).state
        assertEquals(SleepQuality.RESTED, StateCodec.decode(StateCodec.encode(awake))!!.sleepQuality)
    }

    @Test
    fun oldSaveWithoutClockStartsEveryDayAtSevenAm() {
        val s = started().copy(day = 4)
        val text = StateCodec.encode(s).lines()
            .filterNot { it.startsWith("clock") || it.startsWith("sleep") }
            .joinToString("\n")
        val back = StateCodec.decode(text)!!
        assertEquals(3 * 1440L + 420L, back.clockMinutes)
        assertNull(back.sleep)
        assertEquals(SleepQuality.NORMAL, back.sleepQuality)
    }

    @Test
    fun startClockOnlyTicksOnceAndShiftsMoveTheDayForward() {
        val fresh = GameEngine.startClock(started(), t0)
        assertEquals(t0, fresh.clockStamp)
        assertEquals(fresh, GameEngine.startClock(fresh, t0 + 999))       // второй вызов ничего не меняет
        val worked = GameEngine.work(fresh.copy(food = 100, water = 100), Job.HELPER, false).outcome.state
        assertEquals(420L + Rules.SHIFT_VIRTUAL_MINUTES, VirtualClock.now(worked, t0))
    }
}
