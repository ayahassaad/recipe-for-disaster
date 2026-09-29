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
 * TODO(Phase 6): implement selection (filter by prerequisite + cooldown,
 * weighted pick via [RandomSource], respect `unique`), and build out the
 * initial rule library described in the project brief.
 */
class EventEngine(
    private val rules: List<EventRule>,
) {
    fun availableRules(state: GameState): List<EventRule> =
        rules.filter { it.prerequisite(state) }

    fun selectNext(state: GameState, rng: RandomSource): EventOutcome? {
        TODO("Phase 6: weighted selection over availableRules(state), respecting cooldown/unique constraints")
    }
}
