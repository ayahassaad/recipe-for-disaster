package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.simulation.GameState

/**
 * A bank loan: borrow coins in the morning (up to a limit) and pay them back whenever you like. While you owe
 * anything you pay interest every day, which comes out of the takings like any other cost. It's a safety net
 * for a bad week, and a way to buy something big early, not free money.
 */
object Loans {

    /** Loans move in steps of this many coins. */
    const val STEP = 100L

    /** The most the bank will lend, in all. */
    const val LIMIT = 1_000L

    /** What the bank charges each day, as a percentage of what you owe. */
    const val DAILY_INTEREST_PERCENT = 2

    /** Today's interest on [owed] coins, rounded up so a small loan still costs a little. */
    fun interest(owed: Long): Long = if (owed <= 0) 0 else (owed * DAILY_INTEREST_PERCENT + 99) / 100

    /** How much more [state] can borrow. */
    fun canBorrow(state: GameState): Long = (LIMIT - state.loan).coerceAtLeast(0)

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

    /** [state] after borrowing or repaying [change] (already within limits): the debt and the cash both move. */
    fun applied(state: GameState, change: Long): GameState =
        if (change == 0L) state else state.copy(loan = state.loan + change, restaurant = state.restaurant.copy(cash = state.restaurant.cash + change))
}
