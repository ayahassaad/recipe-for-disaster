package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.simulation.GameState

/**
 * A bank loan: borrow coins in the morning (up to a limit) and pay them back, but not forever. The whole loan is
 * due [TERM_DAYS] days after it's taken out. While you owe anything you pay interest every day, which comes out
 * of the takings like any other cost. Late, the interest jumps, your name suffers every day, you can't borrow
 * any more, and a week late the bank takes the restaurant. A safety net for a bad week, not free money.
 */
object Loans {

    /** Loans move in steps of this many coins. */
    const val STEP = 100L

    /** The most the bank will lend, in all. */
    const val LIMIT = 1_000L

    /** What the bank charges each day, as a percentage of what you owe. */
    const val DAILY_INTEREST_PERCENT = 2

    /** How many days a loan lasts: it has to be paid back by this many days after it was taken out. */
    const val TERM_DAYS = 14

    /** From this many days before it's due, the morning warns you. */
    const val WARN_DAYS = 3

    /** Once late, the bank charges this much a day instead, and the restaurant's name suffers by [OVERDUE_REPUTATION_HIT] points a day. */
    const val OVERDUE_INTEREST_PERCENT = 5
    const val OVERDUE_REPUTATION_HIT = 3

    /** This many days late, the bank takes the restaurant: the game is over. */
    const val FORECLOSE_AFTER_OVERDUE = 7

    /** After a loan is paid off, the bank won't lend again for this many days. */
    const val COOLDOWN_DAYS = 3

    /** A day's interest on [owed] coins at [percent], rounded up so a small loan still costs a little. */
    fun interest(owed: Long, percent: Int = DAILY_INTEREST_PERCENT): Long = if (owed <= 0) 0 else (owed * percent + 99) / 100

    /** How many days late [state]'s loan is (0 if it isn't, or there's no loan). */
    fun overdueDays(state: GameState): Int {
        val due = state.loanDueDay ?: return 0
        return if (state.loan > 0 && state.day > due) state.day - due else 0
    }

    /** Today's interest on what [state] owes: the usual rate, or the much higher one once the loan is late. */
    fun interestFor(state: GameState): Long =
        interest(state.loan, if (overdueDays(state) > 0) OVERDUE_INTEREST_PERCENT else DAILY_INTEREST_PERCENT)

    /** How many days until the loan is due (0 on the last day, below zero once late); null when nothing is owed. */
    fun daysUntilDue(state: GameState): Int? = if (state.loan > 0) state.loanDueDay?.let { it - state.day } else null

    /** How many days late the loan has to get before the bank takes the restaurant, counting down; null if it isn't late. */
    fun daysBeforeForeclosure(state: GameState): Int? = overdueDays(state).takeIf { it > 0 }?.let { FORECLOSE_AFTER_OVERDUE - it }

    /** Whether the bank has run out of patience and taken the restaurant. */
    fun foreclosed(state: GameState): Boolean = overdueDays(state) >= FORECLOSE_AFTER_OVERDUE

    /** Why the bank won't lend today, if it won't. */
    enum class Refusal { LATE, JUST_PAID_OFF }

    fun refusal(state: GameState): Refusal? = when {
        overdueDays(state) > 0 -> Refusal.LATE
        state.day < state.noLoanUntilDay -> Refusal.JUST_PAID_OFF
        else -> null
    }

    /** How much more [state] can borrow: nothing while the loan is late or just paid off, otherwise up to the limit. */
    fun canBorrow(state: GameState): Long = if (refusal(state) != null) 0 else (LIMIT - state.loan).coerceAtLeast(0)

    /** How much [state] could pay back now: all of the loan, or as much as there's cash for. */
    fun canRepay(state: GameState): Long = minOf(state.loan, state.restaurant.cash).coerceAtLeast(0)

    /**
     * What a request to borrow ([change] above zero) or repay (below zero) comes to, once kept within what the
     * bank will lend and what's owed or in the till.
     */
    fun clamp(state: GameState, change: Long): Long = when {
        change > 0 -> minOf(change, canBorrow(state))
        change < 0 -> -minOf(-change, canRepay(state))
        else -> 0
    }

    /**
     * [state] after borrowing or repaying [change] (already within limits): the debt and the cash both move. A new
     * loan starts the clock (borrowing more doesn't restart it), and paying everything off stops it, and makes the
     * bank wait a few days before lending again.
     */
    fun applied(state: GameState, change: Long): GameState {
        if (change == 0L) return state
        val owed = state.loan + change
        return state.copy(
            loan = owed,
            loanDueDay = when {
                owed <= 0 -> null
                state.loan <= 0 -> state.day + TERM_DAYS
                else -> state.loanDueDay
            },
            noLoanUntilDay = if (owed <= 0) state.day + COOLDOWN_DAYS else state.noLoanUntilDay,
            restaurant = state.restaurant.copy(cash = state.restaurant.cash + change),
        )
    }
}
