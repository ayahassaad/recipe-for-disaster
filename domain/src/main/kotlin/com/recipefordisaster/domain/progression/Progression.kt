package com.recipefordisaster.domain.progression

import com.recipefordisaster.domain.simulation.GameState
import kotlinx.serialization.Serializable

/**
 * Something the restaurant meets as it goes along, and the day it first turns up. New things arrive
 * one at a time, every few days, so there's only ever one new thing to learn.
 */
@Serializable
enum class Feature(val day: Int, internal val legacyDay: Int?) {
    SPILLS(day = 3, legacyDay = 3),
    DRINKS(day = 6, legacyDay = 1),
    CAT(day = 8, legacyDay = 3),
    FAMILIES(day = 10, legacyDay = 2),
    CELEBRITY(day = 12, legacyDay = 3),
    CRITIC(day = 14, legacyDay = 3),
    RAT(day = 15, legacyDay = 4),
    FRIDGE_BREAKS(day = 17, legacyDay = 4),
    DOG(day = 18, legacyDay = 4),
    CHEF_MOODS(day = 20, legacyDay = 1),
    PAN_FIRE(day = 21, legacyDay = 4),
    INSPECTOR(day = 23, legacyDay = 3),
    POWER_CUT(day = 24, legacyDay = 4),

    /** Some nights, a second thing goes wrong after the first is sorted. */
    DOUBLE_TROUBLE(day = 26, legacyDay = null),
    ;

    // legacyDay: when it used to turn up, before things arrived one at a time. A game started back then
    // has already met everything that came before its current day, and keeps it.
}

/**
 * Which features a restaurant has met so far, and how settled in each one is. Each new thing starts
 * rare on its first day and becomes normal over [RAMP_DAYS] days.
 */
object Progression {
    /** Days a new feature takes to go from rare to normal. */
    const val RAMP_DAYS = 5

    /** Whether [feature] can happen today. */
    fun has(state: GameState, feature: Feature): Boolean = feature in metEarly(state) || state.day >= feature.day

    /**
     * How much of [feature] there is today: 0 before it arrives, a little on its first day, 1 once it's
     * settled in. Anything a game met before the schedule existed is there in full.
     */
    fun strength(state: GameState, feature: Feature): Float = when {
        feature in metEarly(state) -> 1f
        state.day < feature.day -> 0f
        else -> ((state.day - feature.day + 1).toFloat() / RAMP_DAYS).coerceIn(0f, 1f)
    }

    /** The features that arrive on exactly [day] (for a new game; ones already met don't count). */
    fun arrivingOn(state: GameState, day: Int): List<Feature> = Feature.entries.filter { it.day == day && it !in metEarly(state) }

    /**
     * Gives a game saved before features arrived one at a time the set it had already met, so nothing it
     * knew disappears. New games start with none, and anything already set is left alone.
     */
    fun ensure(state: GameState): GameState =
        if (state.featuresMetEarly != null) state else state.copy(featuresMetEarly = legacy(state.day))

    private fun metEarly(state: GameState): Set<Feature> = state.featuresMetEarly ?: legacy(state.day)

    private fun legacy(day: Int): Set<Feature> = Feature.entries.filter { f -> f.legacyDay != null && day >= f.legacyDay }.toSet()
}
