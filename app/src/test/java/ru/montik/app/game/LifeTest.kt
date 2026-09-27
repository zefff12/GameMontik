package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверки по ТЗ: квартира и аренда, игровые периоды, план и факт бюджета, звёзды и уровни,
 * копилка на цель, задания, реклама, покупки и демо-режим.
 */
class LifeTest {

    private fun started(seed: Long = 42L): GameState =
        GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(seed)), Scenarios.INTRO, 0).outcome.state

    /** Ночь, перед которой Монтик сыт и напоился (чтобы проверять деньги, а не голод). */
    private fun night(s: GameState): Outcome = GameEngine.sleep(s.copy(food = 100, water = 100), SleepPlace.CABIN)

    private fun nights(s0: GameState, n: Int): GameState {
        var s = s0
        repeat(n) { s = night(s).state }
        return s
    }

    // ───────────────────────── Квартира ─────────────────────────

    @Test
    fun rentIsPaidOnThe15thAnd30thWithAFiveDayCountdown() {
        var s = started().copy(coins = 1000)
        assertEquals(15, s.rentDueDay)
        assertNull(Life.rentNotice(s))                                     // день 1: до оплаты далеко
        s = nights(s, 8)                                                   // день 9
        assertNull(Life.rentNotice(s))
        val toTen = night(s)                                               // день 10: за 5 дней — напоминание
        assertTrue(toTen.messages.any { it.contains("осталось 5") })
        s = toTen.state
        assertNotNull(Life.rentNotice(s))
        val before = s.coins
        s = nights(s, 5)                                                   // день 15 — оплата
        assertEquals(15, s.day)
        assertEquals(before - Housing.MODEST.rent, s.coins)
        assertEquals(30, s.rentDueDay)                                     // следующий платёж — 30-го
        assertEquals(0, s.rentDebt)
        s = nights(s, 15)                                                  // день 30 — второй платёж в месяце
        assertEquals(before - 2 * Housing.MODEST.rent, s.coins)
        assertEquals(45, s.rentDueDay)
    }

    @Test
    fun rentCanBePaidAheadOnlyDuringTheCountdown() {
        val early = started().copy(coins = 500, day = 5)
        assertFalse(Life.payRentAhead(early).ok)                          // рано
        val s = started().copy(coins = 500, day = 12)
        val paid = Life.payRentAhead(s)
        assertTrue(paid.ok)
        assertEquals(350, paid.state.coins)
        assertTrue(paid.state.rentPaidAhead)
        assertNull(Life.rentNotice(paid.state))                            // отсчёт больше не нужен
        val after = nights(paid.state, 3)                                  // день 15: второй раз не списали
        assertEquals(350, after.coins)
        assertFalse(after.rentPaidAhead)
        assertEquals(30, after.rentDueDay)
    }

    @Test
    fun missingRentTakesTheCushionThenBecomesDebtWithoutEviction() {
        val s = started().copy(coins = 50, cushion = 40, day = 14)
        val due = night(s).state
        assertEquals(0, due.coins)
        assertEquals(0, due.cushion)
        assertEquals(60, due.rentDebt)
        assertEquals(1, due.housing)                                       // никто не выселяет
        assertTrue(GameEngine.beginSleep(due, SleepPlace.CABIN, due.clockStamp).ok)
        assertTrue(GameEngine.currentGoal(due).contains("долг за квартиру"))
        val pay = Life.payRentDebt(due.copy(coins = 100))
        assertTrue(pay.ok)
        assertEquals(0, pay.state.rentDebt)
        assertEquals(40, pay.state.coins)
    }

    // ───────────────────────── План и факт ─────────────────────────

    @Test
    fun planCannotExceedTheWalletAndLocksAfterConfirmation() {
        val s = started().copy(coins = 100)
        assertFalse(Life.setPlan(s, 60, 30, 20, confirm = true).ok)       // 110 > 100
        val draft = Life.setPlan(s, 50, 30, 10, confirm = false)
        assertTrue(draft.ok)
        assertEquals(10, draft.state.plan!!.left)                          // остаток виден
        val changed = Life.setPlan(draft.state, 50, 20, 20, confirm = true)   // до подтверждения можно менять
        assertTrue(changed.ok)
        assertTrue(changed.state.plan!!.confirmed)
        assertFalse(Life.setPlan(changed.state, 10, 10, 10, confirm = true).ok)
        assertFalse(Life.planMissing(changed.state))
    }

    @Test
    fun aGoodPeriodEarnsThreeStars() {
        var s = started().copy(coins = 200, pNeeds = 0, pWants = 0)
        s = Life.setPlan(s, needs = 60, wants = 20, savings = 30, confirm = true).state
        s = GameEngine.buyFood(s.copy(food = 20), "lunch").state          // обязательное
        s = GameEngine.buyJoy(s, "icecream").state                         // желаемое, 8
        s = Goals.deposit(s, 30).state                                     // в копилку
        val now = Life.evaluate(s)
        assertTrue(now.starPlan)
        assertTrue(now.starSave)
        s = nights(s, 5)                                                   // период закончился
        assertEquals(2, s.period)
        val r = s.periods.last()
        assertEquals(1, r.period)
        assertEquals(3, r.stars)
        assertEquals(3, s.stars)
        assertEquals(0, s.pNeeds)                                          // факт нового периода с нуля
        assertTrue(s.purchases.isEmpty())
        assertTrue(Medals.earned(s).any { it.id == "planner" })
    }

    @Test
    fun overspendingOnWantsCostsThePlanStarAndIsExplained() {
        var s = started().copy(coins = 200)
        s = Life.setPlan(s, needs = 50, wants = 0, savings = 0, confirm = true).state
        s = GameEngine.buyJoy(s, "cinema").state
        val r = Life.evaluate(s)
        assertFalse(r.starPlan)
        assertFalse(r.starSave)
        val text = Life.explain(r).joinToString(" ")
        assertTrue(text.contains("на желаемое ушло 25"))
        assertTrue(text.contains("ничего не отложено"))
    }

    @Test
    fun hungerOrRentDebtCostsTheNeedsStar() {
        val s = started().copy(pHungry = 1)
        assertFalse(Life.evaluate(s).starNeeds)
        assertFalse(Life.evaluate(started().copy(rentDebt = 10)).starNeeds)
        assertTrue(Life.evaluate(started()).starNeeds)
    }

    @Test
    fun spendingIsSortedIntoNeedsAndWants() {
        var s = started().copy(coins = 500, pNeeds = 0, pWants = 0, purchases = emptyList())
        s = GameEngine.buyFood(s, "water").state
        s = GameEngine.buyClothing(s, "tshirt").state                      // простая — нужное
        s = GameEngine.buyClothing(s, "crown").state                       // богатая — желаемое
        assertEquals(3 + 12, s.pNeeds)
        assertEquals(90, s.pWants)
        assertEquals(3, s.purchases.size)
        assertTrue(s.purchases.last().contains("желаемое"))
    }

    @Test
    fun purchaseWithoutEnoughMoneyIsRefusedAndExplained() {
        val poor = started().copy(coins = 5, piggy = 20)
        val r = GameEngine.buyJoy(poor, "boardgame")
        assertFalse(r.ok)
        assertEquals(5, r.state.coins)
        assertTrue(r.messages.first().contains("Не хватает 40"))
        assertTrue(r.messages.first().contains("копилки"))
    }

    // ───────────────────────── Уровни ─────────────────────────

    @Test
    fun movingUpNeedsStarsAndMoneyMovingDownIsFree() {
        val s = started().copy(coins = 300, stars = 5)
        assertFalse(Life.moveTo(s, 2).ok)                                 // не хватает звёзд
        assertFalse(Life.moveTo(s.copy(stars = 6, coins = 100), 2).ok)    // не хватает денег
        assertFalse(Life.moveTo(s.copy(stars = 99), 3).ok)                // через уровень нельзя
        val moved = Life.moveTo(s.copy(stars = 6), 2)
        assertTrue(moved.ok)
        assertEquals(2, moved.state.housing)
        assertEquals(0, moved.state.coins)
        assertEquals("Средний достаток", moved.state.home.levelTitle)
        assertTrue(Medals.earned(moved.state).any { it.id == "newhome" })
        val down = Life.moveTo(moved.state, 1)
        assertTrue(down.ok)
        assertEquals(1, down.state.housing)
        assertEquals(3, Housing.values().size)                             // три уровня: скромный, средний, богатый
    }

    @Test
    fun rentAndAdPricesGrowWithTheLevel() {
        val s = started()
        val ad = Ads.all.first()
        assertTrue(Housing.COZY.rent > Housing.MODEST.rent)
        assertTrue(Housing.PENTHOUSE.rent > Housing.COZY.rent)
        assertTrue(Ads.price(s.copy(housing = 3), ad) > Ads.price(s.copy(housing = 2), ad))
        assertTrue(Ads.price(s.copy(housing = 2), ad) > Ads.price(s, ad))
    }

    // ───────────────────────── Копилка ─────────────────────────

    @Test
    fun piggyShowsProgressAndAnHonestEta() {
        var s = started().copy(coins = 100)
        assertFalse(Goals.choose(s, "trip_kazan").ok)                     // сначала Москва
        s = Goals.choose(s, "bike").state
        s = Goals.deposit(s, 30).state
        assertEquals(30, s.piggy)
        assertEquals(270, Goals.remaining(s))
        s = s.copy(day = 3)                                               // 30 монет за 3 дня — 10 в день
        assertEquals(10, Goals.averagePerDay(s))
        assertEquals(27, Goals.etaDays(s))
        val p = Goals.previewWithdraw(s, 10)
        assertEquals(20, p.savedAfter)
        assertEquals(27, p.daysBefore)
        assertEquals(28, p.daysAfter)                                     // цель отодвигается — это видно до снятия
        val w = Goals.withdraw(s, 10)
        assertTrue(w.ok)
        assertEquals(20, w.state.piggy)
        assertEquals(80, w.state.coins)
    }

    @Test
    fun reachingAGoalSpendsThePiggy() {
        val bike = Goals.choose(started(), "bike").state.copy(piggy = 300)
        val r = Goals.reach(bike)
        assertTrue(r.ok)
        assertEquals(0, r.state.piggy)
        assertTrue("bike" in r.state.goalsDone)
        assertNull(r.state.goalId)
        assertTrue(Medals.earned(r.state).any { it.id == "goal" })
        assertFalse(Goals.choose(r.state, "bike").ok)                     // второй раз не нужно

        val trip = Goals.choose(started(), "trip_moscow").state.copy(piggy = 160)
        val t = Goals.reach(trip)
        assertTrue(t.ok)
        assertEquals("moscow", t.state.trip!!.destinationId)
        assertEquals(10, t.state.piggy)
        assertFalse(Goals.reach(Goals.choose(started(), "bike").state.copy(piggy = 10)).ok)
    }

    // ───────────────────────── Задания ─────────────────────────

    @Test
    fun thereAreAtLeastSixTasksInThreeThemesAndEveryKindIsChecked() {
        assertTrue(Tasks.all.size >= 6)
        assertEquals(TaskTheme.values().toSet(), Tasks.all.map { it.theme }.toSet())
        for (t in Tasks.all) {
            val kind = t.kind
            val good = when (kind) {
                is TaskKind.Pick -> Tasks.checkPick(t, kind.options.indexOfFirst { it.rating == Rating.GREAT })
                is TaskKind.Basket -> Tasks.checkBasket(t, kind.items.indices.filter { kind.items[it].need }.toSet())
                is TaskKind.Split -> Tasks.checkSplit(t, kind.minNeeds, kind.total - kind.minNeeds - kind.minSavings, kind.minSavings)
                is TaskKind.Count -> Tasks.checkCount(t, kind.answer)
            }
            assertEquals("${t.id} должно иметь правильное решение", Rating.GREAT, good!!.rating)
            assertTrue(good.explanation.isNotBlank())
        }
    }

    @Test
    fun wrongAnswersAreExplainedToo() {
        val basket = Tasks.byId("t_basket_list")!!
        assertEquals(Rating.BAD, Tasks.checkBasket(basket, setOf(0, 1))!!.rating)          // забыли яблоки
        assertEquals(Rating.BAD, Tasks.checkBasket(basket, setOf(0, 1, 2, 4))!!.rating)    // дороже 60
        assertEquals(Rating.OK, Tasks.checkBasket(basket, setOf(0, 1, 2, 3))!!.rating)     // нужное + шоколадка
        val split = Tasks.byId("t_split_week")!!
        assertEquals(Rating.BAD, Tasks.checkSplit(split, 50, 40, 10)!!.rating)            // мало на обязательное
        assertEquals(Rating.BAD, Tasks.checkSplit(split, 60, 30, 20)!!.rating)            // больше 100
        assertEquals(Rating.OK, Tasks.checkSplit(split, 70, 30, 0)!!.rating)              // без копилки
        val count = Tasks.checkCount(Tasks.byId("t_count_change")!!, 40)!!
        assertEquals(Rating.BAD, count.rating)
        assertTrue(count.explanation.contains("100 − 63"))
    }

    @Test
    fun tasksPayCoinsOnceAndUnlockDayByDay() {
        val s = started()
        assertEquals(2, Tasks.unlocked(s).size)
        assertEquals(Tasks.all.size, Tasks.unlocked(s.copy(demo = true)).size)
        val t = Tasks.all.first()
        val wrong = Tasks.complete(s, t.id, TaskOutcome(Rating.BAD, "", ""))
        assertTrue(wrong.ok)
        assertEquals(s.coins + Tasks.reward(t, Rating.BAD), wrong.state.coins)
        assertTrue(wrong.messages.first().contains("награда"))            // видно, за что начислено
        val retry = Tasks.complete(wrong.state, t.id, TaskOutcome(Rating.GREAT, "", ""))
        assertEquals(wrong.state.coins, retry.state.coins)                 // второй раз без монет
        assertEquals(Rating.GREAT, retry.state.taskResults[t.id])
        assertFalse(Tasks.complete(s, Tasks.all.last().id, TaskOutcome(Rating.GREAT, "", "")).ok)   // ещё закрыто
        assertEquals(Tasks.all[1], Tasks.active(retry.state))
    }

    // ───────────────────────── Реклама ─────────────────────────

    @Test
    fun anAdAppearsEveryThirdDay() {
        var s = started().copy(coins = 500)
        s = nights(s, 1)
        assertNull(s.pendingAd)                                            // день 2
        s = nights(s, 1)
        assertEquals(3, s.day)
        val first = Ads.pending(s)
        assertNotNull(first)
        val coins = s.coins
        val closed = Ads.decline(s)
        assertTrue(closed.ok)
        assertEquals(coins, closed.state.coins)                            // крестик — деньги на месте
        assertNull(closed.state.pendingAd)
        s = nights(closed.state, 3)                                        // день 6 — новая реклама
        val second = Ads.pending(s)!!
        assertTrue(second.id != first!!.id)
        val price = Ads.price(s, second)
        val wantsBefore = s.pWants
        val bought = Ads.buy(s)
        assertTrue(bought.ok)
        assertEquals(s.coins - price, bought.state.coins)
        assertEquals(wantsBefore + price, bought.state.pWants)             // попадает в «желаемое»
        assertEquals(1, bought.state.adsBought)
    }

    @Test
    fun anAdCannotBeBoughtOnCredit() {
        val s = started().copy(coins = 3, pendingAd = Ads.all.first().id)
        val r = Ads.buy(s)
        assertFalse(r.ok)
        assertEquals(Ads.all.first().id, r.state.pendingAd)                // реклама висит, пока её не закроют
        assertTrue(r.messages.first().contains("крестик"))
    }

    // ───────────────────────── Настроение и кухня ─────────────────────────

    @Test
    fun moodRisesWithJoysFallsOvernightAndIsExplained() {
        var s = started().copy(coins = 100, mood = 40)
        s = GameEngine.buyJoy(s, "toy").state
        assertEquals(65, s.mood)
        assertTrue(Life.moodReason(s).contains("Конструктор"))
        s = night(s).state
        assertEquals(65 - Rules.NIGHT_MOOD, s.mood)
    }

    @Test
    fun kitchenMealsCostMoneyAndFeed() {
        val s = started().copy(coins = 30, food = 20)
        val burger = GameEngine.eatMeal(s, "burger")
        assertTrue(burger.ok)
        assertEquals(10, burger.state.coins)
        assertEquals(60, burger.state.food)
        val apple = GameEngine.eatMeal(s.copy(coins = 2), "apple")
        assertFalse(apple.ok)
        val skip = GameEngine.skipMeal(s)
        assertEquals(s.coins, skip.state.coins)
        assertTrue(skip.messages.first().contains("голоден"))
    }

    // ───────────────────────── Демо-режим и сохранение ─────────────────────────

    @Test
    fun demoModeSkipsToTheNextPeriodWithTheSameRules() {
        val s = started().copy(coins = 400)
        assertFalse(GameEngine.skipToNextPeriod(s).ok)                    // только в демо
        var d = GameEngine.setDemo(s, true)
        repeat(5) {
            val r = GameEngine.skipToNextPeriod(d.copy(food = 100, water = 100))
            assertTrue(r.ok)
            d = r.state
        }
        assertEquals(6, d.period)                                          // пять периодов подряд без ожидания
        assertEquals(26, d.day)
        assertEquals(5, d.periods.size)
        assertTrue(d.coins < 400)                                          // квартира оплачена по правилам
    }

    @Test
    fun newProgressSurvivesSaveAndLoad() {
        val s = started().copy(
            housing = 2, rentDueDay = 30, rentPaidAhead = true, rentDebt = 7,
            mood = 33, moodWhy = "почему-то", goalId = "bike", piggy = 42, piggyDeposited = 50, piggyFirstDay = 2,
            goalsDone = setOf("console"),
            plan = BudgetPlan(1, 100, 50, 20, 30, true),
            pNeeds = 11, pWants = 12, pSaved = 13, pEarned = 14, pHungry = 1,
            purchases = listOf("День 1: Хлеб −5 (обязательное)"),
            periods = listOf(PeriodResult(1, true, 1, 2, 3, 4, 5, 6, 7, 0, 0, true, false, true)),
            periodSeen = 1, stars = 9,
            taskResults = mapOf("t_split_week" to Rating.OK),
            pendingAd = "phone", lastAdDay = 3, adsShown = 2, adsBought = 1, adsDeclined = 1,
            joys = mapOf("toy" to 2), helpSeen = true, demo = true
        )
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s, back)
    }

    @Test
    fun anOldSaveWithoutRentGetsTheNextPaymentDay() {
        val old = StateCodec.encode(started().copy(day = 17))
            .lines().filterNot { it.startsWith("rentDueDay") }.joinToString("\n")
        val back = StateCodec.decode(old)!!
        assertEquals(30, back.rentDueDay)
    }

    @Test
    fun aHelperCanAffordTheModestFlat() {
        // Обычный игрок: работает помощником, ест, спит. За месяц квартира оплачивается без долгов.
        var s = started()
        repeat(30) {
            while (s.food < 60 && s.coins >= 10) s = GameEngine.buyFood(s, "lunch").state
            while (s.water < 60 && s.coins >= 3) s = GameEngine.buyFood(s, "water").state
            while (s.energy >= 55 && GameEngine.workBlocker(s) == null) {
                s = GameEngine.work(s, Job.HELPER, true).outcome.state
                while (s.food < 40 && s.coins >= 10) s = GameEngine.buyFood(s, "lunch").state
                while (s.water < 40 && s.coins >= 3) s = GameEngine.buyFood(s, "water").state
            }
            s = GameEngine.sleep(s, SleepPlace.CABIN).state
        }
        assertEquals(31, s.day)
        assertEquals(0, s.rentDebt)
        assertTrue("Денег в конце месяца: ${s.coins}", s.coins > 200)
    }
}
