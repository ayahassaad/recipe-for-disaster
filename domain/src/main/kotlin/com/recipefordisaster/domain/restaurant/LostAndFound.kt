package com.recipefordisaster.domain.restaurant

import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.CustomerServiceOutcome
import kotlinx.serialization.Serializable

/** Something a guest left behind. */
@Serializable
data class LostItem(
    val id: Int,
    /** What it is: "umbrella", "scarf"... */
    val what: String,
    /** Whose it is. */
    val owner: String,
    /** The day it was found. */
    val day: Int,
)

/**
 * The lost and found: guests who enjoyed themselves now and then leave something behind. Give it back
 * the next morning and the owner is thrilled: a thank-you tip in the jar and a little good word about the place.
 * The box only holds so much, so the oldest things go when it's full.
 */
object LostAndFound {
    const val MAX_ITEMS = 6

    /** Each guest who had a decent evening leaves something this often (percent). */
    const val CHANCE_PERCENT = 8
    const val MIN_SATISFACTION = 60

    /** What an owner gives you for getting their thing back, and how much better they speak of you. */
    const val THANK_YOU_TIP = 5L
    const val REPUTATION_BOOST = 1

    val THINGS = listOf("umbrella", "scarf", "pair of glasses", "teddy bear", "phone", "woolly hat", "book", "glove", "set of keys")

    /** The box after tonight: what's new in it, and what had to go to make room. */
    data class Update(val items: List<LostItem>, val found: List<LostItem>, val thrownOut: List<LostItem>)

    fun afterNight(state: GameState, outcomes: List<CustomerServiceOutcome>): Update {
        val rng = SeededRandomSource(state.seed * 47L + state.day * 13L)
        val found = mutableListOf<LostItem>()
        outcomes.forEach { outcome ->
            // Always roll, so one guest's luck doesn't depend on the guests before them.
            val roll = rng.nextInt(100)
            val thing = rng.nextInt(THINGS.size)
            if (outcome.dish != null && outcome.satisfaction >= MIN_SATISFACTION && roll < CHANCE_PERCENT) {
                found += LostItem(id = state.day * 100 + found.size, what = THINGS[thing], owner = outcome.customer.name, day = state.day)
            }
        }
        val all = state.lostAndFound + found
        val kept = all.takeLast(MAX_ITEMS)
        return Update(kept, found, all.dropLast(kept.size))
    }

    /** [state] after the things in [ids] go back to their owners this morning. */
    fun giveBack(state: GameState, ids: Set<Int>): GameState {
        val returning = state.lostAndFound.filter { it.id in ids }
        if (returning.isEmpty()) return state
        return state.copy(
            lostAndFound = state.lostAndFound - returning.toSet(),
            tipJar = state.tipJar + THANK_YOU_TIP * returning.size,
            restaurant = state.restaurant.copy(reputation = (state.restaurant.reputation + REPUTATION_BOOST * returning.size).coerceAtMost(100)),
        )
    }
}
