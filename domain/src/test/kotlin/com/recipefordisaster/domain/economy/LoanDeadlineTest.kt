package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanDeadlineTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start = NewGameFactory.create(seed = 3L).let { it.copy(day = 10, restaurant = it.restaurant.copy(cash = 1_000)) }

    private fun borrow(state: GameState, amount: Long) = DecisionApplier.apply(state, PlayerDecisions(loanChange = amount)).state

    private fun owing(day: Int, due: Int, loan: Long = 400) = start.copy(day = day, loan = loan, loanDueDay = due)

    @Test
    fun `a new loan is due fourteen days later`() {
        val after = borrow(start, 300)
        assertEquals(10 + Loans.TERM_DAYS, after.loanDueDay)
        assertEquals(Loans.TERM_DAYS, Loans.daysUntilDue(after))
    }

    @Test
    fun `borrowing more doesn't give you longer`() {
        val first = borrow(start, 300)
        val later = borrow(first.copy(day = 17), 200)
        assertEquals(first.loanDueDay, later.loanDueDay)
        assertEquals(500L, later.loan)
    }

    @Test
    fun `paying it all off stops the clock, and the bank won't lend again for a few days`() {
        val cleared = DecisionApplier.apply(borrow(start, 300), PlayerDecisions(loanChange = -300)).state
        assertEquals(0L, cleared.loan)
        assertNull(cleared.loanDueDay)
        assertEquals(Loans.Refusal.JUST_PAID_OFF, Loans.refusal(cleared))
        assertEquals(0L, Loans.canBorrow(cleared))
        assertEquals(0L, borrow(cleared, 300).loan)

        val later = cleared.copy(day = cleared.day + Loans.COOLDOWN_DAYS)
        assertNull(Loans.refusal(later))
        assertEquals(300L, borrow(later, 300).loan)
    }

    @Test
    fun `it isn't late until the day after it's due`() {
        assertEquals(0, Loans.overdueDays(owing(day = 24, due = 24)))
        assertEquals(0L, Loans.interestFor(owing(day = 24, due = 24)) - Loans.interest(400))
        assertEquals(1, Loans.overdueDays(owing(day = 25, due = 24)))
        assertEquals(4, Loans.overdueDays(owing(day = 28, due = 24)))
    }

    @Test
    fun `a late loan costs far more in interest, and the bank won't lend any more`() {
        val late = owing(day = 26, due = 24)
        assertEquals(Loans.interest(400, Loans.OVERDUE_INTEREST_PERCENT), Loans.interestFor(late))
        assertTrue(Loans.interestFor(late) > Loans.interestFor(owing(day = 20, due = 24)) * 2)
        assertEquals(Loans.Refusal.LATE, Loans.refusal(late))
        assertEquals(0L, Loans.canBorrow(late))
        assertEquals(400L, borrow(late, 300).loan)
    }

    @Test
    fun `the countdown to the bank taking the place`() {
        assertEquals(Loans.FORECLOSE_AFTER_OVERDUE - 1, Loans.daysBeforeForeclosure(owing(day = 25, due = 24)))
        assertNull(Loans.daysBeforeForeclosure(owing(day = 24, due = 24)))
        assertTrue(Loans.foreclosed(owing(day = 24 + Loans.FORECLOSE_AFTER_OVERDUE, due = 24)))
        assertTrue(!Loans.foreclosed(owing(day = 24 + Loans.FORECLOSE_AFTER_OVERDUE - 1, due = 24)))
    }

    private fun closeDay(state: GameState) = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(1L))

    @Test
    fun `every late day knocks your reputation down, and says so in the log`() {
        val onTime = closeDay(owing(day = 20, due = 24).copy(loan = 0, loanDueDay = null)).newState.restaurant.reputation
        val late = closeDay(owing(day = 26, due = 24))
        assertEquals(onTime - Loans.OVERDUE_REPUTATION_HIT, late.newState.restaurant.reputation)
        assertTrue(late.newState.log.any { it.message.contains("late") })
    }

    @Test
    fun `the books charge the late rate`() {
        val result = closeDay(owing(day = 26, due = 24, loan = 400))
        assertEquals(Loans.interest(400, Loans.OVERDUE_INTEREST_PERCENT), result.newState.ledger.history.last().interest)
    }

    @Test
    fun `a week late the bank takes the restaurant, however much is in the till`() {
        val sixDaysLate = owing(day = 24 + Loans.FORECLOSE_AFTER_OVERDUE - 1, due = 24)
        val fine = closeDay(sixDaysLate.copy(restaurant = sixDaysLate.restaurant.copy(cash = 5_000))).newState
        assertEquals(RestaurantStatus.BANKRUPT, fine.restaurant.status)
        assertTrue(fine.log.any { it.message.contains("taken the restaurant") })

        val fiveDaysLate = owing(day = 24 + Loans.FORECLOSE_AFTER_OVERDUE - 2, due = 24)
        assertEquals(RestaurantStatus.OPEN, closeDay(fiveDaysLate.copy(restaurant = fiveDaysLate.restaurant.copy(cash = 5_000))).newState.restaurant.status)
    }

    @Test
    fun `an old save with no due day is simply never late`() {
        val old = start.copy(loan = 400, loanDueDay = null)
        assertEquals(0, Loans.overdueDays(old))
        assertNull(Loans.daysUntilDue(old))
    }
}
