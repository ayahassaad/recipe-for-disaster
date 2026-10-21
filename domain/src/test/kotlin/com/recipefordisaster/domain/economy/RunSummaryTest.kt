package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSummaryTest {

    private fun night(day: Int, revenue: Long, wages: Long = 0, groceries: Long = 0, rent: Long = 0, tips: Long = 0) = DailyFinancials(
        day = day, revenue = revenue, wages = wages, ingredientCosts = groceries, rent = rent, utilities = 0, maintenance = 0,
        upgrades = 0, miscellaneous = 0, tips = tips,
    )

    private val game = NewGameFactory.create(seed = 12L)

    private fun withNights(vararg nights: DailyFinancials) = game.copy(day = nights.size + 1, ledger = Ledger(nights.toList()))

    @Test
    fun `the totals and the best night come from the books`() {
        val summary = RunSummaries.of(withNights(night(1, 100, wages = 40, tips = 5), night(2, 300, wages = 40, tips = 12), night(3, 120, wages = 40)))
        assertEquals(3, summary.daysSurvived)
        assertEquals(520L, summary.revenue)
        assertEquals(400L, summary.profit)
        assertEquals(NightResult(2, 260), summary.bestNight)
        assertEquals(17L, summary.tips)
    }

    @Test
    fun `the biggest mistake is the night that lost the most, and what it went on`() {
        val summary = RunSummaries.of(withNights(night(1, 200, wages = 50), night(2, 20, wages = 60, groceries = 150), night(3, 10, wages = 30, groceries = 40)))
        assertEquals(Mistake.CostlyNight(day = 2, loss = 190, mostlyOn = CostCategory.GROCERIES), summary.mistake)
    }

    @Test
    fun `a run that never lost money has no mistake to own up to`() {
        assertNull(RunSummaries.of(withNights(night(1, 200, wages = 50), night(2, 250, wages = 50))).mistake)
        assertNull(RunSummaries.of(game.copy(day = 1)).mistake)
        assertNull(RunSummaries.of(game.copy(day = 1)).bestNight)
    }

    @Test
    fun `the favourite dish is the one that sold most`() {
        val dish = game.menu.first()
        val other = game.menu.last()
        val summary = RunSummaries.of(game.copy(dishSalesTotals = mapOf(dish.id.value to 4, other.id.value to 9)))
        assertEquals(other.name to 9, summary.favouriteDish)
        assertNull(RunSummaries.of(game.copy(dishSalesTotals = emptyMap())).favouriteDish)
    }

    @Test
    fun `stars go from one to five with the reputation`() {
        assertEquals(1, RunSummaries.starsFor(0))
        assertEquals(3, RunSummaries.starsFor(60))
        assertEquals(5, RunSummaries.starsFor(100))
    }

    @Test
    fun `the bank taking the place is the mistake when it did`() {
        val late = game.copy(
            day = 30, loan = 400, loanDueDay = 10, ledger = Ledger(listOf(night(1, 100))),
            restaurant = game.restaurant.copy(status = RestaurantStatus.BANKRUPT),
        )
        assertTrue(Loans.foreclosed(late))
        assertEquals(Mistake.BankTookOver, RunSummaries.of(late).mistake)
    }
}
