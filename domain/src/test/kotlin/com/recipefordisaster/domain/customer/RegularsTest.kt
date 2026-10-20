package com.recipefordisaster.domain.customer

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.CustomerServiceOutcome
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegularsTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val base: GameState = NewGameFactory.create(seed = 21L).let { it.copy(day = 12, restaurant = it.restaurant.copy(tables = 6)) }
    private val maria = CustomerFlow.generateCustomer(SeededRandomSource(1L), 900)
    private val dish = base.menu.first()

    private fun served(customer: Customer, satisfaction: Int) = CustomerServiceOutcome(customer, dish, satisfaction, 1.0)
    private fun missed(customer: Customer) = CustomerServiceOutcome(customer, null, 10, 0.0, MissedMealReason.TIRED_OF_WAITING)

    @Test
    fun `a regular comes back and takes the place of a stranger`() {
        val state = base.copy(regulars = listOf(Regular(maria, visits = 3, lastSeenDay = 11)))
        val nights = (1L..30L).map { seed -> engine.openService(state.copy(seed = seed), PlayerDecisions(), SeededRandomSource(seed)) }
        val comes = nights.filter { maria.id.value in it.regulars }
        assertTrue("never came", comes.isNotEmpty())
        assertTrue("always came", comes.size < nights.size)
        comes.forEach { setup ->
            assertEquals(1, setup.arrivals.count { it.id == maria.id })
            // More patient than a stranger would be.
            assertTrue(setup.arrivals.first { it.id == maria.id }.patience > maria.patience)
        }
    }

    @Test
    fun `nobody is a regular on the first days or on a quiet night`() {
        val regular = listOf(Regular(maria))
        val early = base.copy(day = 3, regulars = regular)
        assertTrue((1L..20L).none { engine.openService(early, PlayerDecisions(), SeededRandomSource(it)).regulars.isNotEmpty() })
        assertEquals(emptySet<String>(), Regulars.welcomeBack(base.copy(regulars = regular), listOf(maria)).second)
    }

    @Test
    fun `the same night always brings the same regulars`() {
        val state = base.copy(regulars = listOf(Regular(maria)))
        val a = engine.openService(state, PlayerDecisions(), SeededRandomSource(5L))
        val b = engine.openService(state, PlayerDecisions(), SeededRandomSource(5L))
        assertEquals(a.arrivals, b.arrivals)
        assertEquals(a.regulars, b.regulars)
    }

    @Test
    fun `a regular who left happy has one more visit, and one who left unhappy is gone`() {
        val joe = CustomerFlow.generateCustomer(SeededRandomSource(2L), 901)
        val state = base.copy(regulars = listOf(Regular(maria, visits = 2), Regular(joe, visits = 4)))
        val update = Regulars.afterNight(state, listOf(served(maria, 90), served(joe, 20)))
        assertEquals(listOf(3), update.regulars.filter { it.customer.id == maria.id }.map { it.visits })
        assertEquals(listOf(joe), update.lost)
        assertFalse(update.regulars.any { it.customer.id == joe.id })
        // One who gave up waiting never forgives it either.
        assertEquals(listOf(maria), Regulars.afterNight(state, listOf(missed(maria))).lost)
    }

    @Test
    fun `a regular who stayed away tonight is still a regular`() {
        val state = base.copy(regulars = listOf(Regular(maria)))
        assertEquals(listOf(maria), Regulars.afterNight(state, emptyList()).regulars.map { it.customer })
    }

    @Test
    fun `a wonderful evening can make a stranger a regular, but not on the first days`() {
        val stranger = CustomerFlow.generateCustomer(SeededRandomSource(3L), 902)
        val joins = (1L..40L).count { seed ->
            Regulars.afterNight(base.copy(seed = seed), listOf(served(stranger, 95))).joined.isNotEmpty()
        }
        assertTrue(joins in 1..39)
        assertTrue(Regulars.afterNight(base.copy(day = 2), listOf(served(stranger, 95))).joined.isEmpty())
        assertTrue(Regulars.afterNight(base, listOf(served(stranger, 60))).joined.isEmpty())
    }

    @Test
    fun `the restaurant never has more regulars than it has room for`() {
        val full = (0 until Regulars.MAX).map { Regular(CustomerFlow.generateCustomer(SeededRandomSource(it + 10L), 950 + it)) }
        val stranger = CustomerFlow.generateCustomer(SeededRandomSource(3L), 902)
        (1L..40L).forEach { seed ->
            assertEquals(Regulars.MAX, Regulars.afterNight(base.copy(seed = seed, regulars = full), listOf(served(stranger, 95))).regulars.size)
        }
    }

    @Test
    fun `a regular tips more`() {
        val state = base.copy(regulars = listOf(Regular(maria)))
        val setup = (1L..30L).map { engine.openService(state.copy(seed = it), PlayerDecisions(), SeededRandomSource(it)) }.first { maria.id.value in it.regulars }
        val night = ServiceNight.open(setup, SeededRandomSource(9L))
        assertTrue(maria.id.value in night.regulars)
    }
}
