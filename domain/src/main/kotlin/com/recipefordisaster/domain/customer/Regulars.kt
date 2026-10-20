package com.recipefordisaster.domain.customer

import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.CustomerServiceOutcome
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import kotlinx.serialization.Serializable

/** A guest who liked the place enough to keep coming back. */
@Serializable
data class Regular(
    val customer: Customer,
    /** How many evenings they've had here. */
    val visits: Int = 1,
    /** The last day they came. */
    val lastSeenDay: Int = 0,
)

/**
 * Regulars: a guest who has a wonderful evening may become one, comes back on later nights, is more
 * patient with you and tips better, and is gone for good the night you let them down.
 */
object Regulars {
    /** Nobody is a regular before this day. */
    const val FIRST_DAY = 5

    /** How many regulars a restaurant can have at once. */
    const val MAX = 8

    /** A regular turns up on a given night this often (percent). */
    const val RETURN_PERCENT = 75

    /** A guest this happy (or happier) may become a regular, one time in this many percent. */
    const val JOIN_SATISFACTION = 80
    const val JOIN_PERCENT = 40

    /** A regular who leaves less happy than this never comes back. */
    const val LOSE_BELOW = 40

    /** Extra coins a pleased regular leaves, and how much more patient they are (percent). */
    const val TIP_BONUS = 2L
    const val PATIENCE_PERCENT = 130

    /** What a regular's first name is, for the "Maria is back" lines. */
    fun firstName(customer: Customer): String = customer.name.substringBefore(' ')

    /** Tonight's guests with the regulars who are coming swapped in, and who they are (by customer id). */
    fun welcomeBack(state: GameState, arrivals: List<Customer>): Pair<List<Customer>, Set<String>> {
        if (state.day < FIRST_DAY || state.regulars.isEmpty() || arrivals.size < 4) return arrivals to emptySet()
        val rng = SeededRandomSource(state.seed * 41L + state.day * 7L)
        val coming = state.regulars.filter { rng.nextInt(100) < RETURN_PERCENT }.take(arrivals.size / 3)
        if (coming.isEmpty()) return arrivals to emptySet()
        val guests = arrivals.toMutableList()
        coming.forEachIndexed { k, regular ->
            // Spread through the evening, not all at once.
            val slot = ((k + 1) * arrivals.size / (coming.size + 1)).coerceIn(0, arrivals.lastIndex)
            guests[slot] = regular.customer.copy(patience = regular.customer.patience * PATIENCE_PERCENT / 100)
        }
        return guests to coming.map { it.customer.id.value }.toSet()
    }

    /** Who's a regular after a night: the new list, who just joined and who gave up on the place. */
    data class Update(val regulars: List<Regular>, val joined: List<Customer>, val lost: List<Customer>)

    fun afterNight(state: GameState, outcomes: List<CustomerServiceOutcome>): Update {
        val kept = mutableListOf<Regular>()
        val lost = mutableListOf<Customer>()
        for (regular in state.regulars) {
            val outcome = outcomes.firstOrNull { it.customer.id == regular.customer.id }
            when {
                // Didn't come tonight, or found nothing they could eat: still a regular.
                outcome == null || outcome.missedReason == MissedMealReason.NOTHING_SUITABLE -> kept += regular
                outcome.satisfaction < LOSE_BELOW -> lost += regular.customer
                else -> kept += regular.copy(visits = regular.visits + 1, lastSeenDay = state.day)
            }
        }
        val rng = SeededRandomSource(state.seed * 43L + state.day * 11L)
        val candidates = outcomes.filter { o ->
            o.dish != null && o.satisfaction >= JOIN_SATISFACTION && state.regulars.none { it.customer.id == o.customer.id }
        }
        val joined = if (state.day >= FIRST_DAY && kept.size < MAX) {
            // At most one new regular a night.
            candidates.firstOrNull { rng.nextInt(100) < JOIN_PERCENT }?.customer?.let { listOf(it) } ?: emptyList()
        } else {
            emptyList()
        }
        return Update(kept + joined.map { Regular(it, visits = 1, lastSeenDay = state.day) }, joined, lost)
    }
}
