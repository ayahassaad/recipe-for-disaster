package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.RandomSource

enum class Severity {
    TRIVIAL,
    MINOR,
    MODERATE,
    SEVERE,
    CATASTROPHIC,
}

/**
 * A single outcome produced by resolving an [EventRule] against the current
 * [GameState]. `followUpRuleIds` lets one event schedule another — this is
 * the mechanism chain reactions (e.g. exhausted employee -> slow service ->
 * unhappy customers -> bad reviews) are built from: ordinary rules whose
 * prerequisites happen to reference each other's consequences, not a
 * hardcoded script. A scheduled follow-up only fires if its own
 * prerequisite still holds by then.
 *
 * [tone] is decided per outcome rather than per rule, since the same event
 * can go either way (a food critic can rave or savage you).
 */
data class EventOutcome(
    val ruleId: String,
    val description: String,
    val resultingState: GameState,
    val followUpRuleIds: Set<String> = emptySet(),
    val tone: LogTone = LogTone.NEUTRAL,
)

/**
 * A state-aware, weighted event definition. Prerequisites and weight are
 * both functions of the live [GameState] rather than fixed values, so
 * whether an event can fire — and how likely it is to — depends on what's
 * actually going on in the restaurant, per the "no purely scripted events"
 * design principle.
 *
 * A rule whose weight is always zero never fires by chance, only when an
 * earlier event schedules it as a follow-up — that's how a "consequence"
 * event (the inspection after a fire) is expressed.
 *
 * The rule library itself lives in [EventLibrary].
 */
data class EventRule(
    val id: String,
    val severity: Severity,
    val cooldownDays: Int,
    val unique: Boolean,
    val prerequisite: (GameState) -> Boolean,
    val weight: (GameState) -> Float,
    val resolve: (GameState, RandomSource) -> EventOutcome,
    /** Short headline for the UI ("Food critic!"); the outcome's description carries the detail. */
    val title: String = id,
)

/**
 * Evaluates the current rule set against a [GameState] and selects the next
 * event to fire, respecting cooldowns and uniqueness constraints.
 *
 * Selection order each day:
 * 1. Any follow-up an earlier event scheduled ([GameState.scheduledFollowUps])
 *    whose prerequisite still holds fires first, ignoring its cooldown —
 *    consequences shouldn't be skipped just because the same thing happened
 *    recently. Scheduled follow-ups whose prerequisite no longer holds are
 *    dropped.
 * 2. Otherwise a weighted-random pick among eligible rules, where
 *    [quietDayWeight] is the weight of "nothing happens today." Because rule
 *    weights rise as things go wrong, a struggling restaurant sees more
 *    events than a calm one — the chaos is emergent, not scheduled.
 *
 * An engine with an empty rule list is still valid and simply never fires.
 */
class EventEngine(
    private val rules: List<EventRule>,
    private val quietDayWeight: Float = 0f,
) {
    fun availableRules(state: GameState): List<EventRule> =
        rules.filter { rule ->
            rule.prerequisite(state) &&
                (state.eventCooldowns[rule.id] ?: 0) <= 0 &&
                !(rule.unique && rule.id in state.firedUniqueEventIds)
        }

    /**
     * Picks and resolves the next event, then centrally stamps cooldown,
     * uniqueness and follow-up bookkeeping onto the resulting state — so an
     * individual event definition only has to describe its own
     * consequences, not the bookkeeping around firing at all.
     *
     * When nothing fires but scheduled follow-ups were dropped, that cleanup
     * still has to reach the state, so the caller should use [prepare]
     * first; [selectNext] assumes it's been given the prepared state.
     */
    fun selectNext(state: GameState, rng: RandomSource): EventOutcome? {
        val scheduled = scheduledFollowUp(state)
        if (scheduled != null) {
            val withoutIt = state.copy(scheduledFollowUps = state.scheduledFollowUps - scheduled.id)
            return fire(scheduled, withoutIt, rng)
        }

        val eligible = availableRules(state)
        if (eligible.isEmpty()) return null

        val weights = eligible.map { it.weight(state).coerceAtLeast(0f) }
        val totalWeight = weights.sum()
        if (totalWeight <= 0f) return null

        val roll = rng.nextFloat() * (totalWeight + quietDayWeight.coerceAtLeast(0f))
        if (roll > totalWeight) return null // a quiet day

        var cumulative = 0f
        // Fallback for float rounding at the very top of the range — never a zero-weight (follow-up-only) rule.
        var chosen = eligible.zip(weights).last { (_, weight) -> weight > 0f }.first
        for ((rule, weight) in eligible.zip(weights)) {
            cumulative += weight
            if (weight > 0f && roll <= cumulative) {
                chosen = rule
                break
            }
        }
        return fire(chosen, state, rng)
    }

    /** Drops scheduled follow-ups that can no longer happen (unknown ID, prerequisite gone, unique already fired). */
    fun prepare(state: GameState): GameState {
        if (state.scheduledFollowUps.isEmpty()) return state
        val stillValid = state.scheduledFollowUps.filter { id ->
            val rule = rules.firstOrNull { it.id == id } ?: return@filter false
            rule.prerequisite(state) && !(rule.unique && rule.id in state.firedUniqueEventIds)
        }.toSet()
        return if (stillValid == state.scheduledFollowUps) state else state.copy(scheduledFollowUps = stillValid)
    }

    fun ruleById(id: String): EventRule? = rules.firstOrNull { it.id == id }

    private fun scheduledFollowUp(state: GameState): EventRule? =
        rules.firstOrNull { rule ->
            rule.id in state.scheduledFollowUps &&
                rule.prerequisite(state) &&
                !(rule.unique && rule.id in state.firedUniqueEventIds)
        }

    private fun fire(rule: EventRule, state: GameState, rng: RandomSource): EventOutcome {
        val outcome = rule.resolve(state, rng)
        return outcome.copy(resultingState = stampBookkeeping(outcome.resultingState, rule, outcome.followUpRuleIds))
    }

    private fun stampBookkeeping(state: GameState, rule: EventRule, followUps: Set<String>): GameState {
        val newCooldowns = state.eventCooldowns + (rule.id to rule.cooldownDays)
        val newUniques = if (rule.unique) state.firedUniqueEventIds + rule.id else state.firedUniqueEventIds
        val knownFollowUps = followUps.filter { id -> rules.any { it.id == id } }
        return state.copy(
            eventCooldowns = newCooldowns,
            firedUniqueEventIds = newUniques,
            scheduledFollowUps = state.scheduledFollowUps + knownFollowUps,
        )
    }

    companion object {
        /** Call once per day-tick so cooldowns on rules that didn't fire actually count down. */
        fun decayCooldowns(cooldowns: Map<String, Int>): Map<String, Int> =
            cooldowns.mapValues { (_, daysRemaining) -> (daysRemaining - 1).coerceAtLeast(0) }
                .filterValues { it > 0 }
    }
}
