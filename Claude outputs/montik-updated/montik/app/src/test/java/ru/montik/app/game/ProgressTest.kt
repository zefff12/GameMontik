package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * То, что делает игру живее: опыт и уровень, звёзды смены в магазине, добрые события дня,
 * реплики Монтика, «Совет от Лобачевского», похвала от взрослого и коллекция.
 */
class ProgressTest {

    private fun started(seed: Long = 42L): GameState =
        GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(seed)), Scenarios.INTRO, 0).outcome.state

    private fun fed(s: GameState) = s.copy(food = 100, water = 100, energy = 100)

    // ───────────────────────── Опыт и уровень ─────────────────────────

    @Test
    fun levelCurveGrowsSteadily() {
        assertEquals(1, Progress.levelOf(0))
        assertEquals(1, Progress.levelOf(59))
        assertEquals(2, Progress.levelOf(60))
        assertEquals(2, Progress.levelOf(149))
        assertEquals(3, Progress.levelOf(150))
        assertEquals(150, Progress.levelStart(3))
        assertEquals(0 to 120, Progress.inLevel(150))
        assertTrue(kotlin.math.abs(Progress.fraction(105) - 0.5f) < 0.001f)
    }

    @Test
    fun levelUpIsCongratulatedOnce() {
        val s = started().copy(xp = 59)
        assertNull(Progress.levelUpPending(s))
        val up = s.copy(xp = 61)
        assertEquals(2, Progress.levelUpPending(up))
        assertNull(Progress.levelUpPending(Progress.markLevelSeen(up)))
    }

    @Test
    fun workTasksSavingAndAdsGiveExperience() {
        val s = fed(started().copy(coins = 200))
        val shift = GameEngine.work(s, Job.HELPER, true).outcome.state
        assertEquals(s.xp + Progress.XP_SHIFT, shift.xp)

        val task = Tasks.unlocked(s).first()
        val first = Tasks.complete(s, task.id, TaskOutcome(Rating.GREAT, "", "")).state
        assertEquals(s.xp + Progress.XP_TASK_GREAT, first.xp)
        val again = Tasks.complete(first, task.id, TaskOutcome(Rating.GREAT, "", "")).state
        assertEquals("за повтор опыта нет", first.xp, again.xp)

        // Опыт за накопления — раз в день, сколько бы раз ни откладывать.
        val once = Goals.deposit(s, 10).state
        val twice = Goals.deposit(once, 10).state
        assertEquals(s.xp + Progress.XP_SAVE_DAY, twice.xp)
        val cushion = GameEngine.depositCushion(twice, 10).state
        assertEquals(twice.xp, cushion.xp)

        val withAd = s.copy(pendingAd = Ads.all.first().id)
        assertEquals(s.xp + Progress.XP_AD_DECLINED, Ads.decline(withAd).state.xp)
    }

    // ───────────────────────── Звёзды смены в магазине ─────────────────────────

    @Test
    fun shopShiftGivesStarsAndRemembersTheRecord() {
        val s = fed(started().copy(shifts = 3))
        assertEquals(3, ShopWork.stars(95))
        assertEquals(2, ShopWork.stars(75))
        assertEquals(1, ShopWork.stars(10))

        val first = GameEngine.workShop(s, ShopGame.CASHIER, 75)
        assertEquals(2, first.payslip!!.stars)
        assertFalse("первая смена — ещё не рекорд", first.payslip!!.record)
        val s1 = first.outcome.state
        assertEquals(75, s1.shopBest[ShopGame.CASHIER.name])
        assertEquals(s.xp + Progress.XP_SHIFT + 2 * Progress.XP_SHOP_STAR, s1.xp)
        assertEquals(2, ShopWork.bestStars(s1, ShopGame.CASHIER))
        assertEquals(0, ShopWork.bestStars(s1, ShopGame.SHELVES))

        val better = GameEngine.workShop(fed(s1), ShopGame.CASHIER, 95)
        assertEquals(3, better.payslip!!.stars)
        assertTrue(better.payslip!!.record)
        val worse = GameEngine.workShop(fed(better.outcome.state), ShopGame.CASHIER, 50)
        assertFalse(worse.payslip!!.record)
        assertEquals(95, worse.outcome.state.shopBest[ShopGame.CASHIER.name])

        // Обычная работа — без звёзд.
        assertNull(GameEngine.work(fed(s), Job.HELPER, true).payslip!!.stars)
    }

    // ───────────────────────── Добрые события ─────────────────────────

    @Test
    fun kindEventsPayOutAndCanRepeat() {
        val s = fed(started())
        val coins = s.coins
        val help = GameEngine.choose(s.copy(pendingEvent = Scenarios.NEIGHBOR), Scenarios.NEIGHBOR, 0).outcome
        assertTrue(help.ok)
        assertEquals(coins + 15, help.state.coins)
        assertEquals(90, help.state.energy)
        assertNull(help.state.pendingEvent)
        assertTrue(help.state.xp > s.xp)
        // Событие можно встретить снова.
        assertTrue(GameEngine.choose(help.state.copy(pendingEvent = Scenarios.NEIGHBOR), Scenarios.NEIGHBOR, 1).outcome.ok)

        // Находка: в копилку, если есть цель, иначе — в подушку.
        val noGoal = GameEngine.choose(s, Scenarios.FOUND, 0).outcome.state
        assertEquals(s.cushion + 10, noGoal.cushion)
        val withGoal = Goals.choose(s, Goals.all.first().id).state
        val toPiggy = GameEngine.choose(withGoal, Scenarios.FOUND, 0).outcome.state
        assertEquals(withGoal.piggy + 10, toPiggy.piggy)
        assertEquals(withGoal.cushion, toPiggy.cushion)

        // Скидка: пройти мимо — деньги на месте; купить — желаемая трата.
        val pass = GameEngine.choose(s, Scenarios.SALE, 0).outcome.state
        assertEquals(coins, pass.coins)
        val buy = GameEngine.choose(s, Scenarios.SALE, 1).outcome.state
        assertEquals(coins - 20, buy.coins)
        assertEquals(s.pWants + 20, buy.pWants)
    }

    @Test
    fun differentEventsHappenOverAMonth() {
        var s = started().copy(coins = 5000)
        val seen = mutableSetOf<String>()
        repeat(40) {
            s.pendingEvent?.let { id ->
                seen += id
                s = GameEngine.choose(s, id, 0).outcome.state
            }
            s = GameEngine.sleep(fed(s), SleepPlace.CABIN).state
        }
        assertTrue("событий мало: $seen", seen.size >= 3)
        assertTrue(seen.all { it in Scenarios.EVENTS })
    }

    // ───────────────────────── Реплики и советы ─────────────────────────

    @Test
    fun montikSaysWhatMattersFirst() {
        val s = started()
        assertTrue(Talk.phrases(s.copy(food = 10)).first().contains("проголодался"))
        assertTrue(Talk.phrases(s.copy(energy = 10, food = 90, water = 90)).first().contains("устал"))
        val rent = Talk.phrases(s.copy(food = 90, water = 90, energy = 90, day = 12))
        assertTrue(rent.any { it.contains("До оплаты квартиры 3 дня") })
        assertTrue(Talk.phrases(s).isNotEmpty())
        assertEquals("1 день", Talk.daysWord(1))
        assertEquals("5 дней", Talk.daysWord(5))
        assertEquals("22 дня", Talk.daysWord(22))
    }

    @Test
    fun lobachevskyAdvisesOncePerDayAndToThePoint() {
        val s = started().copy(helpSeen = true)
        assertTrue(Advice.due(s))
        assertEquals("rent", Advice.forToday(s.copy(rentDebt = 50)).id)
        val tip = Advice.forToday(s)
        val seen = Advice.markSeen(s, tip)
        assertFalse(Advice.due(seen))
        assertTrue(Advice.due(seen.copy(day = seen.day + 1)))
        // Тот же совет два дня подряд не повторяется.
        val debt = Advice.markSeen(s.copy(rentDebt = 50), Advice.byId("rent")!!).copy(day = s.day + 1)
        assertTrue(Advice.forToday(debt).id != "rent")
    }

    // ───────────────────────── Похвала и коллекция ─────────────────────────

    @Test
    fun parentPraiseReachesTheChild() {
        val s = started().copy(mood = 50)
        assertEquals(s, Praise.send(s, "   "))
        val sent = Praise.send(s, "Горжусь тобой!")
        assertTrue(sent.parentNoteNew)
        assertEquals(1, sent.praises)
        val read = Praise.read(sent).state
        assertFalse(read.parentNoteNew)
        assertEquals("Горжусь тобой!", read.parentNote)
        assertEquals(60, read.mood)
    }

    @Test
    fun collectionShowsMagnetsAndThings() {
        val s = started()
        assertTrue(Keepsakes.magnets(s).none { it.collected })
        val travelled = s.copy(completedTrips = setOf("moscow"), goalsDone = setOf("bike"))
        assertEquals(listOf("moscow"), Keepsakes.magnets(travelled).filter { it.collected }.map { it.destinationId })
        assertEquals(listOf("bike"), Keepsakes.things(travelled).map { it.id })
    }

    @Test
    fun newProgressFieldsSurviveSaveAndLoad() {
        val s = started().copy(
            xp = 321, levelSeen = 3, xpSaveDay = 4, tipDay = 5, lastTipId = "ads",
            parentNote = "Молодец; так держать = супер!", parentNoteNew = true, praises = 2,
            shopBest = mapOf("CASHIER" to 88), soundOn = false
        )
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(s.xp, back.xp)
        assertEquals(s.levelSeen, back.levelSeen)
        assertEquals(s.xpSaveDay, back.xpSaveDay)
        assertEquals(s.tipDay, back.tipDay)
        assertEquals(s.lastTipId, back.lastTipId)
        assertEquals(s.parentNote, back.parentNote)
        assertTrue(back.parentNoteNew)
        assertEquals(2, back.praises)
        assertEquals(s.shopBest, back.shopBest)
        assertFalse(back.soundOn)
        assertNotNull(back)
    }
}
