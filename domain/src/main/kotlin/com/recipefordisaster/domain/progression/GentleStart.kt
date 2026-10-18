package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.GameState

/**
 * The first nights are for learning the ropes, so they're slower: guests arrive further apart and are more
 * patient, giving a new player time to find out what to tap before the next party is at the door. It eases
 * off each night and is gone after the first week.
 */
object GentleStart {

    /** The nights it lasts: it's all the way gone by the night after this one (the sixth). */
    const val LAST_DAY = 5

    /** On the very first night parties arrive this many times further apart than usual, and wait this much longer. */
    const val FIRST_NIGHT_GAP = 2f
    const val FIRST_NIGHT_PATIENCE = 1.4f

    /** How much of the gentleness is left tonight: 1 on the first night, down to 0 after the first week (or at once with everything from the start). */
    fun left(state: GameState): Float {
        if (state.everythingFromTheStart) return 0f
        return (1f - (state.day - 1).toFloat() / LAST_DAY).coerceIn(0f, 1f)
    }

    /** How many times further apart than usual guests arrive tonight (1 is as usual). */
    fun arrivalGapFactor(state: GameState): Float = 1f + (FIRST_NIGHT_GAP - 1f) * left(state)

    /** How many times as patient as usual guests are tonight (1 is as usual). */
    fun patienceFactor(state: GameState): Float = 1f + (FIRST_NIGHT_PATIENCE - 1f) * left(state)
}
