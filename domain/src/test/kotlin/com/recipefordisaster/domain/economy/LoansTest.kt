package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoansTest {

    private val start = NewGameFactory.create(seed = 3L)
    private val cash = start.restaurant.cash

    @Test
    fun `interest is two percent a day, rounded up`() {
        assertEquals(0L, Loans.interest(0))
        assertEquals(2L, Loans.interest(100))
        assertEquals(10L, Loans.interest(500))
        assertEquals(1L, Loans.interest(1))
        assertEquals(20L, Loans.interest(1_000))
    }

    @Test
    fun `borrowing puts coins in the till and on the debt`() {
        val after = DecisionApplier.apply(start, PlayerDecisions(loanChange = 300)).state
        assertEquals(cash + 300, after.restaurant.cash)
        assertEquals(300L, after.loan)
    }

    @Test
    fun `the bank won't lend more than its limit, in all`() {
        val once = DecisionApplier.apply(start, PlayerDecisions(loanChange = 5_000)).state
        assertEquals(Loans.LIMIT, once.loan)
        assertEquals(0L, Loans.canBorrow(once))
        val again = DecisionApplier.apply(once, PlayerDecisions(loanChange = 500)).state
        assertEquals(Loans.LIMIT, again.loan)
        assertEquals(once.restaurant.cash, again.restaurant.cash)
    }

    @Test
    fun `paying back takes coins out of the till and off the debt, never more than owed or held`() {
        val owing = start.copy(loan = 400, restaurant = start.restaurant.copy(cash = 1_000))
        val paid = DecisionApplier.apply(owing, PlayerDecisions(loanChange = -200)).state
        assertEquals(200L, paid.loan)
        assertEquals(800L, paid.restaurant.cash)

        val all = DecisionApplier.apply(owing, PlayerDecisions(loanChange = -9_000)).state
        assertEquals(0L, all.loan)
        assertEquals(600L, all.restaurant.cash)

        val broke = DecisionApplier.apply(owing.copy(restaurant = owing.restaurant.copy(cash = 150)), PlayerDecisions(loanChange = -400)).state
        assertEquals(250L, broke.loan)
        assertEquals(0L, broke.restaurant.cash)
    }

    @Test
    fun `borrowed coins can be spent the same morning`() {
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 0))
        val flour = com.recipefordisaster.domain.menu.RecipeBook.FLOUR
        val without = DecisionApplier.apply(broke, PlayerDecisions(purchases = mapOf(flour to 20.0)))
        val with = DecisionApplier.apply(broke, PlayerDecisions(loanChange = 300, purchases = mapOf(flour to 20.0)))

        assertEquals(0L, without.spending.ingredients)
        assertTrue(with.spending.ingredients > 0)
        assertEquals(300L, with.state.loan)
    }

    @Test
    fun `an old save with no loan simply owes nothing`() {
        assertEquals(0L, start.loan)
        assertEquals(0L, Loans.interest(start.loan))
    }

    @Test
    fun `a night's books charge the day's interest on what's owed`() {
        val engine = DefaultDayTickEngine(EventEngine(emptyList()))
        fun nightsProfit(loan: Long): Pair<Long, Long> {
            val state = start.copy(loan = loan)
            val result = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(1L))
            val books = result.newState.ledger.history.last()
            return books.interest to books.profitOrLoss
        }
        val (noInterest, profitClear) = nightsProfit(0)
        val (interest, profitOwing) = nightsProfit(500)
        assertEquals(0L, noInterest)
        assertEquals(10L, interest)
        assertEquals(profitClear - 10L, profitOwing)
    }
}
