package com.recipefordisaster.domain.goal

import com.recipefordisaster.domain.simulation.GameState
import kotlinx.serialization.Serializable

/** What a week's goal asks for. */
@Serializable
enum class GoalKind {
    /** Take this many coins over the week. */
    TAKINGS,

    /** Feed this many guests over the week. */
    GUESTS_FED,

    /** Get the restaurant's reputation up to this by the end of the week. */
    STARS,
}

/**
 * Something to aim for this week, with a reward for getting there. [progress] is how far along it
 * is: the coins taken or guests fed so far, or the reputation now.
 */
@Serializable
data class WeeklyGoal(
    val kind: GoalKind,
    val target: Long,
    val reward: Long,
    val startDay: Int,
    val progress: Long = 0,
    /** Reached, and the reward paid. */
    val met: Boolean = false,
) {
    /** The last day that counts towards it. */
    val lastDay: Int get() = startDay + WeeklyGoals.WEEK - 1

    /** Whether it's still being worked towards today. */
    fun openOn(day: Int): Boolean = !met && day <= lastDay
}

object WeeklyGoals {
    const val WEEK = 7

    /** The first week is for learning the ropes: goals start in the second. */
    const val FIRST_GOAL_DAY = 8

    /** Whether [day] starts a new week with a new goal. */
    fun startsAWeek(day: Int): Boolean = day >= FIRST_GOAL_DAY && (day - 1) % WEEK == 0

    /** The goal for the week starting [state]'s day, aimed a little above how the restaurant's been doing. */
    fun newGoal(state: GameState): WeeklyGoal {
        val week = (state.day - 1) / WEEK
        val kind = GoalKind.entries[(week - 1).coerceAtLeast(0) % GoalKind.entries.size]
        val recent = state.ledger.history.takeLast(3)
        val target = when (kind) {
            GoalKind.TAKINGS -> {
                val daily = if (recent.isEmpty()) 150.0 else recent.map { it.revenue }.average()
                roundTo((daily * WEEK * 1.1).toLong(), 50).coerceAtLeast(300)
            }
            GoalKind.GUESTS_FED -> {
                val expected = com.recipefordisaster.domain.simulation.OutlookCalculator.forTonight(state).expectedCustomers
                roundTo((expected * WEEK * 0.85).toLong(), 5).coerceAtLeast(20)
            }
            // The next half star up (a star is 20 points of reputation).
            GoalKind.STARS -> ((state.restaurant.reputation / 10 + 1) * 10L).coerceAtMost(100)
        }
        return WeeklyGoal(kind, target, reward = (150L + 50L * week).coerceAtMost(800), startDay = state.day)
    }

    /**
     * The goal after a day of [takings] coins and [fed] guests, ending on [reputation]: and the reward
     * to pay, when that's the day it was reached.
     */
    fun afterDay(goal: WeeklyGoal, day: Int, takings: Long, fed: Int, reputation: Int): Pair<WeeklyGoal, Long> {
        if (!goal.openOn(day)) return goal to 0L
        val progress = when (goal.kind) {
            GoalKind.TAKINGS -> goal.progress + takings
            GoalKind.GUESTS_FED -> goal.progress + fed
            GoalKind.STARS -> reputation.toLong()
        }
        val met = progress >= goal.target
        return goal.copy(progress = progress, met = met) to (if (met) goal.reward else 0L)
    }

    private fun roundTo(value: Long, step: Long) = (value + step / 2) / step * step
}
