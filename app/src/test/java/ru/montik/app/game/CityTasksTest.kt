package ru.montik.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityTasksTest {
    private fun inCity(city: String) = GameEngine.newGame(7L).copy(
        created = true, coins = 100, trip = TripProgress(city, 0, TripPhase.CITY)
    )

    @Test
    fun bestOptionWeighsIncomeAgainstCompetition() {
        assertEquals("red", CityTasks.all.getValue("moscow").best.id)
        assertEquals("ech", CityTasks.all.getValue("kazan").best.id)
        assertEquals("river", CityTasks.all.getValue("spb").best.id)
        assertEquals("beds", CityTasks.all.getValue("sochi").best.id)
    }

    @Test
    fun taskIsShownOnceOnArrivalAndRewardsTheBestChoiceMore() {
        val s = inCity("moscow")
        assertNotNull(CityTasks.due(s))
        assertNull(CityTasks.due(s.copy(trip = TripProgress("moscow", 0, TripPhase.FLY_OUT))))

        val best = CityTasks.solve(s, "red")
        assertTrue(best.ok)
        assertEquals(100 + CityTasks.BEST_REWARD, best.state.coins)
        assertNull(CityTasks.due(best.state))                   // второй раз не спрашиваем
        assertFalse(CityTasks.solve(best.state, "red").ok)

        val other = CityTasks.solve(s, "mall")
        assertEquals(100 + CityTasks.OTHER_REWARD, other.state.coins)

        val back = StateCodec.decode(StateCodec.encode(best.state))!!
        assertTrue("moscow" in back.cityTasks)
    }
}
