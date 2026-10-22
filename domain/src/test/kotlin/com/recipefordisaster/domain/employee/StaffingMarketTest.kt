package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffingMarketTest {

    private val luxury = setOf(Role.HOST, Role.BUSSER)

    @Test
    fun `hosts and bussers don't apply to a restaurant that can't afford them`() {
        (1L..30L).forEach { seed ->
            val pool = StaffingMarket.generateApplicants(SeededRandomSource(seed), day = 3, cash = StaffingMarket.LUXURY_STAFF_CASH - 1)
            assertTrue(pool.none { it.role in luxury })
        }
    }

    @Test
    fun `once there's money in the bank, a host or a busser is always in the pool`() {
        (1L..30L).forEach { seed ->
            val pool = StaffingMarket.generateApplicants(SeededRandomSource(seed), day = 3, cash = StaffingMarket.LUXURY_STAFF_CASH)
            assertEquals(1, pool.count { it.role in luxury })
        }
    }

    @Test
    fun `there's always a cook, a server and a dishwasher to hire`() {
        (1L..30L).forEach { seed ->
            val roles = StaffingMarket.generateApplicants(SeededRandomSource(seed), day = 3).map { it.role }.toSet()
            assertTrue(roles.containsAll(setOf(Role.COOK, Role.SERVER, Role.DISHWASHER)))
        }
    }

    @Test
    fun `a bartender applies once the bar is open, and not before`() {
        val engine = com.recipefordisaster.domain.simulation.DefaultDayTickEngine(com.recipefordisaster.domain.event.EventEngine(emptyList()))
        val start = com.recipefordisaster.domain.simulation.NewGameFactory.create(seed = 5L)
        fun poolAfter(day: Int) = engine.advanceDay(start.copy(day = day), com.recipefordisaster.domain.simulation.PlayerDecisions(), SeededRandomSource(day.toLong())).newState.applicants
        assertTrue(poolAfter(3).none { it.role == Role.BARTENDER })
        // The night before the bar opens closes; the next day is when it opens.
        val opens = com.recipefordisaster.domain.progression.Feature.DRINKS.day
        assertTrue(poolAfter(opens - 1).any { it.role == Role.BARTENDER })
        assertTrue(StaffingMarket.withEveryBasicRole(emptyList(), SeededRandomSource(1L), opens, bar = true).any { it.role == Role.BARTENDER })
    }
}
