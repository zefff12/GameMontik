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
    fun startingWalletIs50AndIntroGoodChoiceLeaves20() {
        val s0 = GameEngine.newGame(1L)
        assertEquals(50, s0.coins)
        val r = GameEngine.choose(s0, Scenarios.INTRO, 0)
        assertTrue(r.outcome.ok)
        assertEquals(20, r.outcome.state.coins)
        assertTrue(r.outcome.state.prepaidNight)
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
    fun cabinCostsMoneyBenchIsFreeButWeaker() {
        val s = started().copy(prepaidNight = false, coins = 25, energy = 10)
        val cabin = GameEngine.sleep(s, SleepPlace.CABIN).state
        assertEquals(5, cabin.coins)
        assertEquals(100, cabin.energy)
        assertEquals(2, cabin.day)

        val poor = s.copy(coins = 5)
        val bench = GameEngine.sleep(poor, SleepPlace.CABIN).state     // денег нет — скамейка
        assertEquals(5, bench.coins)
        assertEquals(Rules.BENCH_ENERGY, bench.energy)
    }

    @Test
    fun prepaidNightIsUsedOnce() {
        val s = started()
        assertTrue(s.prepaidNight)
        val after = GameEngine.sleep(s, SleepPlace.CABIN).state
        assertFalse(after.prepaidNight)
        assertEquals(s.coins, after.coins)
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
        assertEquals(HeroPart.FUR.default, s.heroColor(HeroPart.FUR))

        s = s.copy(
            heroName = Hero.cleanName("  Пушистик  "),
            heroColors = mapOf(HeroPart.FUR.id to 0xE01B1B, HeroPart.CROWN.id to 0x1616D8)
        )
        assertEquals("Пушистик", s.heroName)
        assertEquals(0xE01B1B, s.heroColor(HeroPart.FUR))
        assertEquals(HeroPart.EARS.default, s.heroColor(HeroPart.EARS))

        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s.heroName, back.heroName)
        assertEquals(s.heroColors, back.heroColors)

        // Пустое имя превращается в имя по умолчанию, слишком длинное обрезается.
        assertEquals(Hero.DEFAULT_NAME, Hero.cleanName("   "))
        assertTrue(Hero.cleanName("ОченьДлинноеИмяГероя").length <= Hero.MAX_NAME)
        assertEquals(7, Hero.PALETTE.size)
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
            while (s.food < 60 && s.coins >= 10 + Rules.CABIN_PRICE) s = GameEngine.buyFood(s, "lunch").state
            while (s.water < 60 && s.coins >= 3 + Rules.CABIN_PRICE) s = GameEngine.buyFood(s, "water").state
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
}
