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
}
