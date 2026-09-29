package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Магазин продуктов и холодильник, работа в банке, приставка и большой стеллаж в магазине. */
class NewWorkTest {

    private fun started(seed: Long = 42L): GameState =
        GameEngine.choose(GameEngine.markCreated(GameEngine.newGame(seed)), Scenarios.INTRO, 0).outcome.state

    // ───────────────────────── Магазин продуктов ─────────────────────────

    @Test
    fun groceryCatalogMatchesTheDesignCards() {
        assertEquals(Grocery.all.size, Grocery.all.map { it.id }.toSet().size)
        assertEquals(8, Grocery.section(GrocerySection.HOME).size)
        assertEquals(8, Grocery.section(GrocerySection.FRUITS).size)
        assertEquals(12, Grocery.section(GrocerySection.VEG).size)
        assertEquals(12, Grocery.section(GrocerySection.DRINKS).size)
        assertEquals(9, Grocery.section(GrocerySection.READY).size)
        assertEquals(12, Grocery.section(GrocerySection.FOOD).size)
        // Цена в игре — в 10 раз меньше, чем на карточке макета.
        assertEquals(4, Grocery.item("water")!!.price)
        assertEquals(12, Grocery.item("bananas")!!.price)
        assertEquals(35, Grocery.item("beef")!!.price)
        assertTrue(Grocery.all.all { it.price > 0 && (it.food > 0 || it.water > 0) })
    }

    @Test
    fun cartIsPaidAtOnceAndGoesToTheFridge() {
        val s = started().copy(coins = 100)
        var cart = emptyMap<String, Int>()
        cart = Grocery.add(cart, "water")
        cart = Grocery.add(cart, "water")
        cart = Grocery.add(cart, "bread")
        cart = Grocery.add(cart, "smoothie")
        assertEquals(4, Grocery.count(cart))
        assertEquals(4 + 4 + 12 + 13, Grocery.total(cart))
        assertEquals((4 + 4 + 12) to 13, Grocery.split(cart))
        cart = Grocery.remove(cart, "smoothie")
        assertNull(cart["smoothie"])

        val bought = Grocery.checkout(s, cart)
        assertTrue(bought.ok)
        assertEquals(100 - 20, bought.state.coins)
        assertEquals(2, bought.state.fridge["water"])
        assertEquals(1, bought.state.fridge["bread"])
        assertEquals(s.pNeeds + 20, bought.state.pNeeds)

        // В минус покупать нельзя, пустую корзину — тоже.
        assertFalse(Grocery.checkout(s.copy(coins = 5), cart).ok)
        assertFalse(Grocery.checkout(s, emptyMap()).ok)
    }

    @Test
    fun eatingFromTheFridgeFeedsMontikAndUsesUpFood() {
        val s = started().copy(food = 40, water = 40, fridge = mapOf("bread" to 1, "water" to 2))
        val ate = Grocery.eat(s, "bread")
        assertTrue(ate.ok)
        assertEquals(65, ate.state.food)
        assertNull(ate.state.fridge["bread"])
        val drank = Grocery.eat(ate.state, "water")
        assertEquals(75, drank.state.water)
        assertEquals(1, drank.state.fridge["water"])
        // Нечего есть — нельзя; сытому Монтику хлеб не нужен.
        assertFalse(Grocery.eat(drank.state, "bread").ok)
        assertFalse(Grocery.eat(s.copy(food = 100), "bread").ok)
        assertEquals(1, Grocery.fridge(drank.state).size)
    }

    @Test
    fun fridgeSurvivesSaveAndLoad() {
        val s = started().copy(fridge = mapOf("milk" to 2, "eggs" to 1, "unknown" to 3), bankShifts = 4, consoleDay = 7, consolePlays = 2)
        val back = StateCodec.decode(StateCodec.encode(s))!!
        assertEquals(mapOf("milk" to 2, "eggs" to 1), back.fridge)
        assertEquals(4, back.bankShifts)
        assertEquals(7, back.consoleDay)
        assertEquals(2, back.consolePlays)
    }

    // ───────────────────────── Банк ─────────────────────────

    @Test
    fun bankShiftHasSomeDefectiveNotes() {
        val s = started().copy(shifts = 2)
        val notes = BankWork.notes(s)
        assertEquals(BankWork.NOTES_PER_SHIFT, notes.size)
        val bad = notes.count { it.defective }
        assertTrue("дефектных: $bad", bad in 3..5)
        assertEquals(notes, BankWork.notes(s))
        assertTrue(BankWork.notes(s.copy(day = s.day + 1)) != notes || BankWork.notes(s.copy(bankShifts = 1)) != notes)
    }

    @Test
    fun bankPaysForAttention() {
        val s = started().copy(shifts = 2, food = 100, water = 100, energy = 100)
        val all = BankWork.notes(s).map { NoteAnswer(it, it.defective) }
        val perfect = BankWork.performance(all, 10_000)
        assertEquals(100, perfect)
        val sloppy = BankWork.performance(all.map { NoteAnswer(it.defect, !it.defect.defective) }, 10_000)
        assertEquals(0, sloppy)

        val shift = BankWork.work(s, perfect)
        assertTrue(shift.outcome.ok)
        assertNotNull(shift.payslip)
        assertEquals(3, shift.payslip!!.stars)
        assertEquals(1, shift.outcome.state.bankShifts)
        assertTrue(shift.outcome.state.coins > s.coins)
        assertEquals(3, BankWork.bestStars(shift.outcome.state))
        // Сначала нужно поработать где-нибудь ещё.
        assertFalse(BankWork.work(s.copy(shifts = 0), 100).outcome.ok)
    }

    // ───────────────────────── Приставка ─────────────────────────

    @Test
    fun consoleNeedsTheGoalAndHasADailyLimit() {
        val s = started()
        assertNotNull(ConsoleGame.blocker(s))
        var owned = s.copy(goalsDone = setOf(ConsoleGame.GOAL_ID), mood = 50)
        assertNull(ConsoleGame.blocker(owned))
        repeat(ConsoleGame.PLAYS_PER_DAY) {
            val r = ConsoleGame.finishRace(owned, 120)
            assertTrue(r.ok)
            owned = r.state
        }
        assertEquals(50 + ConsoleGame.PLAYS_PER_DAY * ConsoleGame.MOOD_PER_RACE, owned.mood)
        assertEquals(0, ConsoleGame.playsLeft(owned))
        assertFalse(ConsoleGame.finishRace(owned, 10).ok)
        // На следующий день можно снова.
        assertEquals(ConsoleGame.PLAYS_PER_DAY, ConsoleGame.playsLeft(owned.copy(day = owned.day + 1)))
    }

    // ───────────────────────── Большой стеллаж ─────────────────────────

    @Test
    fun shopShiftsAlternateTheStands() {
        val s = started().copy(shifts = 5)
        assertEquals(StandLayout.CLASSIC, ShopWork.standLayout(s.copy(shopShifts = 4)))
        assertEquals(StandLayout.BIG, ShopWork.standLayout(s.copy(shopShifts = 5)))
        // Каждый товар большого стеллажа подходит ровно к одному пустому месту.
        for (p in ShopCatalog.shelved) {
            val fits = StandLayout.BIG.slots.count { p.shelf in it }
            assertTrue("${p.name}: $fits", fits <= 1)
        }
        for (slot in StandLayout.BIG.slots) assertTrue(ShopCatalog.shelved.any { it.shelf in slot })
        assertEquals(10, ShopCatalog.products.size)
    }
}
