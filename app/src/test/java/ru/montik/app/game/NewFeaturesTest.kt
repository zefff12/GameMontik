package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Скины за монеты, кредиты с кредитной историей, рынок енота (бартер), честные потери. */
class NewFeaturesTest {

    private fun started(seed: Long = 7L): GameState =
        GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(seed)), Scenarios.INTRO, 0).outcome.state

    private fun night(s: GameState): GameState =
        GameEngine.sleep(s.copy(food = 100, water = 100, pendingEvent = null), SleepPlace.CABIN).state

    // ───────────────────────── Скины ─────────────────────────

    @Test
    fun freeSkinsAreOwnedPaidAreNot() {
        val s = started()
        assertTrue(Skins.owns(s, HeroPreset.WHITE))
        assertTrue(Skins.owns(s, HeroPreset.BLUE))
        assertFalse(Skins.owns(s, HeroPreset.RAINBOW))
        assertTrue(Skins.ownsColor(s, Hero.PALETTE[0]))
        assertFalse(Skins.ownsColor(s, Hero.PALETTE.last()))
    }

    @Test
    fun buyingSkinCostsCoinsAndCannotGoNegative() {
        val poor = started().copy(coins = 10)
        val fail = Skins.buy(poor, HeroPreset.PINK)
        assertFalse(fail.ok)
        assertEquals(10, fail.state.coins)
        val rich = started().copy(coins = 200)
        val ok = Skins.buy(rich, HeroPreset.PINK)
        assertTrue(ok.ok)
        assertEquals(200 - Skins.price(HeroPreset.PINK), ok.state.coins)
        assertTrue(Skins.owns(ok.state, HeroPreset.PINK))
        assertFalse("второй раз не покупается", Skins.buy(ok.state, HeroPreset.PINK).ok)
        val color = Skins.buyColor(ok.state, Hero.PALETTE.last())
        assertTrue(color.ok)
        assertTrue(Skins.ownsColor(color.state, Hero.PALETTE.last()))
        // Покупки переживают сохранение.
        val back = StateCodec.decode(StateCodec.encode(color.state))!!
        assertTrue(Skins.owns(back, HeroPreset.PINK))
        assertTrue(Skins.ownsColor(back, Hero.PALETTE.last()))
    }

    // ───────────────────────── Кредиты ─────────────────────────

    @Test
    fun creditGivesMoneyAndMustBeRepaidWithOverpay() {
        val s0 = started().copy(coins = 0)
        val took = Credits.take(s0, LoanProduct.SMALL.id)
        assertTrue(took.ok)
        val s1 = took.state
        assertEquals(LoanProduct.SMALL.amount, s1.coins)
        assertEquals(1, s1.credits.size)
        assertEquals(LoanProduct.SMALL.total, s1.credits[0].left)
        assertFalse("тот же кредит второй раз нельзя", Credits.take(s1, LoanProduct.SMALL.id).ok)
        val paid = Credits.pay(s1.copy(coins = 100), s1.credits[0].id, 1000)
        assertTrue(paid.ok)
        assertEquals(100 - LoanProduct.SMALL.total, paid.state.coins)
        assertTrue(paid.state.credits.isEmpty())
        assertEquals(1, paid.state.creditHistory.size)
        assertTrue(paid.state.creditHistory[0].onTime)
        assertTrue(Credits.score(paid.state) > 50)
    }

    @Test
    fun payingWithEmptyWalletFails() {
        val s = Credits.take(started(), LoanProduct.SMALL.id).state.copy(coins = 0)
        val r = Credits.pay(s, s.credits[0].id, 10)
        assertFalse(r.ok)
        assertEquals(0, r.state.coins)
    }

    @Test
    fun overdueCreditGetsPenaltyAndBadHistory() {
        var s = Credits.take(started(), LoanProduct.SMALL.id).state
        repeat(LoanProduct.SMALL.days + 3) { s = night(s) }
        val c = s.credits.single()
        assertTrue(Credits.isOverdue(s, c))
        assertTrue("штраф набежал", c.penalty > 0)
        assertNotNull("новый кредит не дают", Credits.blocker(s, LoanProduct.STUDY))
        val closed = Credits.pay(s.copy(coins = 500), c.id, 500).state
        assertFalse(closed.creditHistory.single().onTime)
        assertTrue(Credits.score(closed) < 50)
    }

    @Test
    fun mortgageNeedsGoodHistory() {
        val s = started()
        assertNotNull(Credits.blocker(s, LoanProduct.MORTGAGE))
        val one = Credits.take(s, LoanProduct.SMALL.id).state
        val repaid = Credits.pay(one.copy(coins = 100), one.credits[0].id, 100).state
        assertNull(Credits.blocker(repaid, LoanProduct.MORTGAGE))
        val back = StateCodec.decode(StateCodec.encode(repaid))!!
        assertEquals(repaid.creditHistory, back.creditHistory)
    }

    @Test
    fun creditsSurviveSave() {
        val s = Credits.take(started(), LoanProduct.PHONE.id).state
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s.credits, back.credits)
        assertEquals(s.creditSeq, back.creditSeq)
    }

    // ───────────────────────── Бартер ─────────────────────────

    @Test
    fun raccoonComesEveryTenDays() {
        var s = started()
        assertFalse(Barter.due(s))
        repeat(Barter.INTERVAL_DAYS) { s = night(s) }
        assertTrue(Barter.due(s))
        assertFalse(Barter.due(Barter.leave(s)))
    }

    @Test
    fun fairTradeOnly() {
        val s = started().copy(fridge = mapOf("carrots" to 3, "beef" to 2))
        val honey = "honey"
        assertEquals(Barter.Verdict.EMPTY, Barter.judge(honey, 0))
        assertEquals(Barter.Verdict.TOO_LITTLE, Barter.judge(honey, Barter.offerValue(s, mapOf("carrots" to 1))))
        assertEquals(Barter.Verdict.TOO_MUCH, Barter.judge(honey, Barter.offerValue(s, mapOf("beef" to 2))))
        assertFalse(Barter.trade(s, honey, mapOf("carrots" to 1)).ok)
        val offer = mapOf("carrots" to 1, Barter.SERVICE_ID to 1)
        assertEquals(Barter.Verdict.FAIR, Barter.judge(honey, Barter.offerValue(s, offer)))
        val r = Barter.trade(s, honey, offer)
        assertTrue(r.ok)
        assertEquals(2, r.state.fridge["carrots"])
        assertEquals(1, r.state.fridge[honey])
        assertEquals(1, r.state.barterSessions)
        assertEquals(s.day, r.state.barterDay)
        assertTrue(Lesson.BARTER.name in r.state.seenLessons)
        assertEquals("монеты не тратятся", s.coins, r.state.coins)
    }

    @Test
    fun serviceAloneBuysTeaEvenWithEmptyFridge() {
        val s = started().copy(fridge = emptyMap())
        val r = Barter.trade(s, "herbal_tea", mapOf(Barter.SERVICE_ID to 1))
        assertTrue(r.ok)
        assertEquals(1, r.state.fridge["herbal_tea"])
        val back = StateCodec.decode(StateCodec.encode(r.state))!!
        assertEquals(1, back.fridge["herbal_tea"])
    }

    @Test
    fun cannotOfferMoreThanOwned() {
        val s = started().copy(fridge = mapOf("carrots" to 1))
        assertFalse(Barter.trade(s, "honey", mapOf("carrots" to 5)).ok)
    }

    // ───────────────────────── Честные потери ─────────────────────────

    @Test
    fun lossWithEmptyWalletSaysNothingWasLost() {
        var found: Pair<Destination, Int>? = null
        for (d in Destinations.all) for ((i, id) in d.stops.withIndex()) {
            if (found == null && Scenarios.byId(id)?.choices?.any { it.loss > 0 } == true) found = d to i
        }
        val (dest, stopIndex) = found!!
        val scenario = Scenarios.byId(dest.stops[stopIndex])!!
        val index = scenario.choices.indexOfFirst { it.loss > 0 }
        val s = started().copy(coins = 0, trip = TripProgress(dest.id, stopIndex))
        val r = GameEngine.choose(s, scenario.id, index).outcome
        assertTrue(r.ok)
        assertEquals(0, r.state.coins)
        assertTrue(r.messages.any { "пусто" in it })
    }
}

class PictureSkinsTest {
    @Test
    fun twentyPictureSkinsBuyAndSave() {
        assertEquals(20, Skins.pictures.size)
        val s0 = GameEngine.markCreated(GameEngine.newGame(3L)).copy(coins = 500)
        val gamer = Skins.picture("s09")!!
        assertFalse(Skins.ownsPicture(s0, gamer))
        val r = Skins.buyPicture(s0, gamer)
        assertTrue(r.ok)
        assertEquals(500 - gamer.price, r.state.coins)
        val s1 = r.state.copy(heroSkin = gamer.id)
        val back = StateCodec.decode(StateCodec.encode(s1))!!
        assertEquals("s09", back.heroSkin)
        assertEquals(gamer.tint, Skins.tint(back))
        // Не купленный скин из сохранения не подхватывается.
        val cheat = StateCodec.decode(StateCodec.encode(s0.copy(heroSkin = "s10")))!!
        assertNull(cheat.heroSkin)
    }
}

/** Проходит путешествие целиком: перелёт → остановки → конференция → домой. */
object TripTestHelper {
    fun finishTrip(start: GameState, preferGreat: Boolean = false): GameState {
        var s = start
        var guard = 0
        while (s.trip != null && guard++ < 20) {
            val trip = s.trip!!
            s = when (trip.phase) {
                TripPhase.FLY_OUT -> Travel.land(s).state
                TripPhase.CITY -> {
                    val stop = GameEngine.currentStop(s)!!
                    val great = stop.choices.indexOfFirst { it.rating == Rating.GREAT && GameEngine.canChoose(s, stop, it) }
                    val idx = if (preferGreat && great >= 0) great else stop.choices.indexOfFirst { GameEngine.canChoose(s, stop, it) }
                    GameEngine.choose(s, stop.id, idx).outcome.state
                }
                TripPhase.CONFERENCE -> Travel.attendConference(s).state
                TripPhase.FLY_HOME -> Travel.flyHome(s).state
            }
        }
        return s
    }
}

class TravelChainTest {
    private fun started(): GameState = GameEngine.markCreated(GameEngine.newGame(5L))

    @Test
    fun fullChainGivesCityExperienceAndHigherPay() {
        var s = started().copy(coins = 1000)
        s = GameEngine.startTrip(s, "moscow").state
        assertEquals(TripPhase.FLY_OUT, s.trip!!.phase)
        assertNull(GameEngine.currentStop(s))                 // в самолёте заданий нет

        val landed = Travel.land(s)
        assertTrue(landed.messages.any { "Открыт новый город" in it })
        s = landed.state
        assertTrue("moscow" in s.visitedCities)
        assertTrue(Keepsakes.magnets(s).first { it.destinationId == "moscow" }.collected)

        // Конференция закрыта, пока не пройдены остановки.
        assertFalse(Travel.attendConference(s).ok)
        while (s.trip!!.phase == TripPhase.CITY) {
            val stop = GameEngine.currentStop(s)!!
            s = GameEngine.choose(s, stop.id, stop.choices.indexOfFirst { GameEngine.canChoose(s, stop, it) }).outcome.state
        }
        assertEquals(TripPhase.CONFERENCE, s.trip!!.phase)

        val xpBefore = s.xp
        val shopBase = ShopWork.rank(s).base
        s = Travel.attendConference(s).state
        assertEquals(1, s.conferences)
        assertEquals(xpBefore + Travel.CONFERENCE_XP, s.xp)
        assertEquals(2, Travel.careerLevel(s))
        assertEquals(Travel.CAREER_STEP_PERCENT, Travel.careerPercent(s))
        assertTrue(Travel.shiftExample(shopBase, 1) > Travel.shiftExample(shopBase, 0))
        assertEquals(TripPhase.FLY_HOME, s.trip!!.phase)

        s = Travel.flyHome(s).state
        assertNull(s.trip)
        assertTrue("moscow" in s.completedTrips)
    }

    @Test
    fun newCityAchievementOnlyOnFirstVisitAndCityCanBeRevisited() {
        var s = TripTestHelper.finishTrip(GameEngine.startTrip(started().copy(coins = 1000), "moscow").state)
        assertNull(GameEngine.destinationStatus(s, Destinations.byId("moscow")!!))   // можно снова
        s = GameEngine.startTrip(s, "moscow").state
        val again = Travel.land(s)
        assertTrue(again.ok)
        assertFalse(again.messages.any { "Открыт новый город" in it })
        s = TripTestHelper.finishTrip(again.state)
        assertEquals(2, s.conferences)
        assertEquals(3, Travel.careerLevel(s))
    }

    @Test
    fun conferenceExperienceRaisesShiftPay() {
        val base = started().copy(energy = 100, food = 100, water = 100)
        val plain = GameEngine.work(base, Job.HELPER, null).payslip!!
        val pro = GameEngine.work(base.copy(conferences = 2), Job.HELPER, null).payslip!!
        assertEquals(0, plain.careerPercent)
        assertEquals(2 * Travel.CAREER_STEP_PERCENT, pro.careerPercent)
        assertTrue(pro.gross > plain.gross)
    }

    @Test
    fun cafeIsAWantAndOnlyOncePerTrip() {
        var s = GameEngine.startTrip(started().copy(coins = 500), "moscow").state
        assertFalse(Travel.eatAtCafe(s).ok)                    // в самолёте кафе нет
        s = Travel.land(s).state
        val coins = s.coins
        s = Travel.eatAtCafe(s).state
        assertEquals(coins - Travel.CAFE_PRICE, s.coins)
        assertTrue(s.trip!!.cafe)
        assertFalse(Travel.eatAtCafe(s).ok)
    }

    @Test
    fun tripPhasesSurviveSaveAndLoad() {
        var s = GameEngine.startTrip(started().copy(coins = 1000), "kazan".let { "moscow" }).state
        s = Travel.land(s).state
        s = Travel.eatAtCafe(s).state.copy(conferences = 3)
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s.trip, back.trip)
        assertEquals(s.visitedCities, back.visitedCities)
        assertEquals(3, back.conferences)
        // Старое сохранение без этапа: считаем, что Монтик гуляет по городу.
        val legacy = StateCodec.encode(s).replace("trip=moscow\\:0\\:CITY\\:true", "trip=moscow\\:1")
        val old = StateCodec.decode(legacy)!!
        assertEquals(TripPhase.CITY, old.trip!!.phase)
    }

    @Test
    fun nizhnyNovgorodIsInTheChainBeforeSochi() {
        val nn = Destinations.byId("nn")!!
        assertEquals("spb", nn.requires)
        assertEquals(3, nn.stops.size)
        assertTrue(nn.stops.all { Scenarios.byId(it) != null })
        assertEquals("До Сочи", Travel.flightTitle(Destinations.byId("sochi")!!, false))
        assertEquals("2 ч 15 мин", Travel.duration(135))
        assertEquals("До дома", Travel.flightTitle(nn, true))
    }
}
