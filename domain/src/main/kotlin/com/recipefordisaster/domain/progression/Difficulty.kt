package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.GameState

/**
 * How hard tonight is. A handful of dials turn up a tiny bit every day, so each night is only a
 * little harder than the last and there's never a sudden wall.
 */
data class Dials(
    /** How long guests will wait, as a share of their usual patience: more than 1 is more forgiving. */
    val patience: Float,
    /** The gap between parties arriving, as a share of the usual: less than 1 is busier. */
    val arrivalGap: Float,
    /** The chance that something goes wrong tonight (once disasters have started). */
    val chaosChance: Float,
    /** The most spills there can be tonight. */
    val maxSpills: Int,
    /** Points knocked off every guest's enjoyment: guests expect more as the run goes on. */
    val pickiness: Int,
)

object Difficulty {
    /** By this day every dial is all the way up; after it, things stay as hard as they are. */
    const val FULL_DAY = 40

    /** The day disasters start, and how likely they are then and once they're fully settled in. */
    private const val CHAOS_FROM = 15
    private const val CHAOS_START = 0.3f
    private const val CHAOS_FULL = 0.6f

    /** Every this many days there's a quieter night, so it gets harder in waves rather than a straight climb. */
    const val BREATHER_EVERY = 7
    private const val BREATHER_EASE = 0.1f

    /** Whether tonight is one of the quieter nights. */
    fun breather(state: GameState): Boolean = state.day % BREATHER_EVERY == 0

    /**
     * How far along the run is, from 0 (the first night) to 1 (as hard as it gets): eased off on a
     * quieter night, and quietly following how the restaurant's doing (see [adapting]).
     */
    fun pressure(state: GameState): Float {
        val along = ((state.day - 1).toFloat() / (FULL_DAY - 1)).coerceIn(0f, 1f)
        return (along - (if (breather(state)) BREATHER_EASE else 0f) - adapting(state)).coerceIn(0f, 1f)
    }

    /**
     * After a run of bad nights (losing money, unhappy guests) things ease off a little until the
     * restaurant finds its feet; when it's cruising, they move on a little sooner. Positive is easier.
     */
    fun adapting(state: GameState): Float {
        val recent = state.ledger.history.takeLast(3)
        val losses = recent.count { it.profitOrLoss < 0 }
        return when {
            losses >= 3 -> 0.15f
            losses >= 2 || state.recentSatisfaction < 45 -> 0.08f
            recent.size == 3 && losses == 0 && state.recentSatisfaction >= 75 -> -0.05f
            else -> 0f
        }
    }

    fun dials(state: GameState): Dials {
        val p = pressure(state)
        val chaosAlong = ((state.day - CHAOS_FROM).toFloat() / (FULL_DAY - CHAOS_FROM)).coerceIn(0f, 1f)
        return Dials(
            patience = lerp(1.25f, 0.85f, p),
            arrivalGap = lerp(1.25f, 0.85f, p),
            // Something going wrong is half as likely on a quieter night.
            chaosChance = (CHAOS_START + (CHAOS_FULL - CHAOS_START) * chaosAlong) * (if (breather(state)) 0.5f else 1f),
            maxSpills = when {
                state.day < 10 -> 1
                state.day < 30 -> 2
                else -> 3
            },
            pickiness = (8 * p).toInt() + starPickiness(state.restaurant.reputation),
        )
    }

    /** When moderate and serious overnight events start, each easing in over [Progression.RAMP_DAYS] days. */
    private const val MODERATE_EVENTS_FROM = 5
    private const val SERIOUS_EVENTS_FROM = 9

    /**
     * How much of its usual chance an overnight event of this [severity] has today: small things can
     * happen from the start, moderate ones ease in from day five and serious ones from day nine.
     */
    fun overnightShare(state: GameState, severity: com.recipefordisaster.domain.event.Severity): Float {
        val from = when (severity) {
            com.recipefordisaster.domain.event.Severity.TRIVIAL, com.recipefordisaster.domain.event.Severity.MINOR -> return 1f
            com.recipefordisaster.domain.event.Severity.MODERATE -> MODERATE_EVENTS_FROM
            else -> SERIOUS_EVENTS_FROM
        }
        // A game that already knew about disasters before they came in gradually keeps having them.
        if (Progression.has(state, Feature.RAT) && state.day < Feature.RAT.day) return 1f
        return ((state.day - from + 1).toFloat() / Progression.RAMP_DAYS).coerceIn(0f, 1f)
    }

    /** A well-known restaurant draws guests who expect a lot: a little pickier at four stars, more at four and a half. */
    private fun starPickiness(reputation: Int) = when {
        reputation >= 90 -> 4
        reputation >= 80 -> 2
        else -> 0
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
}
