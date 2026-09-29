package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.simulation.GameState
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
 * unhappy customers -> bad reviews) are meant to be built from in Phase 6:
 * ordinary rules whose prerequisites happen to reference each other's
 * consequences, not a hardcoded script.
 */
data class EventOutcome(
    val ruleId: String,
    val description: String,
    val resultingState: GameState,
    val followUpRuleIds: Set<String> = emptySet(),
)

/**
 * A state-aware, weighted event definition. Prerequisites and weight are
 * both functions of the live [GameState] rather than fixed values, so
 * whether an event can fire — and how likely it is to — depends on what's
 * actually going on in the restaurant, per the "no purely scripted events"
 * design principle.
 *
 * NOTE: this is Phase 2 scaffolding — the type shape only. The actual rule
 * library (the 15-25 events agreed for MVP) and the selection algorithm
 * that reads [EventEngine] rules against [GameState] are Phase 6 work
 * (Events and emergent systems), deliberately not implemented yet.
 */
data class EventRule(
    val id: String,
    val severity: Severity,
    val cooldownDays: Int,
    val unique: Boolean,
    val prerequisite: (GameState) -> Boolean,
    val weight: (GameState) -> Float,
    val resolve: (GameState, RandomSource) -> EventOutcome,
)

/**
 * Evaluates the current rule set against a [GameState] and selects the next
 * event to fire, respecting cooldowns and uniqueness constraints.
 *
 * The selection *mechanism* below is real, working Phase 3 code. What's
 * still missing is the actual rule library — the 15-25 concrete events
 * described in the project brief — which is Phase 6 (Events and emergent
 * systems) content work, deliberately not authored here. An [EventEngine]
 * constructed with an empty rule list is a perfectly valid, fully
 * functional engine that simply never fires an event, which is exactly
 * what Phase 3 needs while that content doesn't exist yet.
 */
class EventEngine(
    private val rules: List<EventRule>,
) {
    fun availableRules(state: GameState): List<EventRule> =
        rules.filter { rule ->
            rule.prerequisite(state) &&
                (state.eventCooldowns[rule.id] ?: 0) <= 0 &&
                !(rule.unique && rule.id in state.firedUniqueEventIds)
        }

    /**
     * Weighted-random pick among eligible rules, then stamps the resulting
     * state with that rule's cooldown and (if applicable) uniqueness —
     * centralized here rather than duplicated in every rule's `resolve`,
     * so an individual event definition only has to describe its own
     * consequences, not the bookkeeping around firing at all.
     */
    fun selectNext(state: GameState, rng: RandomSource): EventOutcome? {
        val eligible = availableRules(state)
        if (eligible.isEmpty()) return null

        val weights = eligible.map { it.weight(state).coerceAtLeast(0f) }
        val totalWeight = weights.sum()
        if (totalWeight <= 0f) return null

        val roll = rng.nextFloat() * totalWeight
        var cumulative = 0f
        var chosen = eligible.last()
        for ((rule, weight) in eligible.zip(weights)) {
            cumulative += weight
            if (roll <= cumulative) {
                chosen = rule
                break
            }
        }

        val outcome = chosen.resolve(state, rng)
        return outcome.copy(resultingState = stampBookkeeping(outcome.resultingState, chosen))
    }

    private fun stampBookkeeping(state: GameState, rule: EventRule): GameState {
        val newCooldowns = state.eventCooldowns + (rule.id to rule.cooldownDays)
        val newUniques = if (rule.unique) state.firedUniqueEventIds + rule.id else state.firedUniqueEventIds
        return state.copy(eventCooldowns = newCooldowns, firedUniqueEventIds = newUniques)
    }

    companion object {
        /** Call once per day-tick so cooldowns on rules that didn't fire actually count down. */
        fun decayCooldowns(cooldowns: Map<String, Int>): Map<String, Int> =
            cooldowns.mapValues { (_, daysRemaining) -> (daysRemaining - 1).coerceAtLeast(0) }
                .filterValues { it > 0 }
    }
}
