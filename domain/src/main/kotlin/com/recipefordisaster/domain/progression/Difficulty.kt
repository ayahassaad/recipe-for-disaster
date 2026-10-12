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
    private const val CHAOS_START = 0.15f
    private const val CHAOS_FULL = 0.45f

    /** How far along the run is, from 0 (the first night) to 1 (as hard as it gets). */
    fun pressure(state: GameState): Float = ((state.day - 1).toFloat() / (FULL_DAY - 1)).coerceIn(0f, 1f)

    fun dials(state: GameState): Dials {
        val p = pressure(state)
        val chaosAlong = ((state.day - CHAOS_FROM).toFloat() / (FULL_DAY - CHAOS_FROM)).coerceIn(0f, 1f)
        return Dials(
            patience = lerp(1.25f, 0.85f, p),
            arrivalGap = lerp(1.25f, 0.85f, p),
            chaosChance = CHAOS_START + (CHAOS_FULL - CHAOS_START) * chaosAlong,
            maxSpills = when {
                state.day < 10 -> 1
                state.day < 30 -> 2
                else -> 3
            },
            pickiness = (8 * p).toInt(),
        )
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
}
