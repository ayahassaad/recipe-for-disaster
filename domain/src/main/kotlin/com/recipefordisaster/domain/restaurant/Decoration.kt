package com.recipefordisaster.domain.restaurant

import kotlinx.serialization.Serializable

/**
 * Something to make the dining room nicer, bought once and kept: every guest enjoys their evening a
 * little more for it. The dearer ones make more difference.
 */
@Serializable
enum class Decoration(val cost: Long, val joy: Int) {
    PAINTINGS(cost = 300, joy = 2),
    FAIRY_LIGHTS(cost = 600, joy = 3),
    PIANO(cost = 1_200, joy = 4),
    ;

    companion object {
        /** How much more every guest enjoys their evening for the decorations in the room. */
        fun joyOf(decorations: Set<Decoration>): Int = decorations.sumOf { it.joy }
    }
}
