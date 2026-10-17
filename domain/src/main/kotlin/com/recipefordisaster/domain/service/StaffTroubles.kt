package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.progression.Progression
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource
import kotlinx.serialization.Serializable

/** What goes wrong for a worker on a bad night. */
@Serializable
enum class TroubleKind {
    /** Goes home ill, for the rest of the night (and a couple of days). */
    SICK,

    /** Storms out after a row, for good tonight; morale drops, and someone fed up enough doesn't come back. */
    ARGUMENT,

    /** Takes a long break, then comes back. */
    BREAK,
}

/** A bad night for one worker: at [at] seconds in, they're gone (until [backAt], for a break). */
@Serializable
data class StaffTrouble(val kind: TroubleKind, val at: Float, val backAt: Float? = null)

/** A bad night for [employeeId], as it ended: for the next morning to deal with. */
@Serializable
data class StaffTroubleOutcome(val employeeId: String, val kind: TroubleKind)

/**
 * Some nights, one of the staff has a bad one. Who, and when, is decided when the doors open, from the game's
 * seed and the day (not the night's own dice). It's likelier for someone stressed or in a poor mood, and
 * it's never more than one person a night, so you always have the others to lean on.
 */
object StaffTroubles {

    /** Who can have a bad night: the ones working the floor and the bar (the kitchen's fixed once the doors open). */
    private val onTheFloor = setOf(Role.SERVER, Role.MANAGER, Role.DISHWASHER, Role.BUSSER, Role.BARTENDER)

    /** How likely [employee] is to have a bad night when they come in, before the feature has settled in. */
    fun risk(employee: Employee): Float {
        val stressed = (employee.stress - 40).coerceAtLeast(0) / 300f
        val unhappy = (50 - employee.morale).coerceAtLeast(0) / 250f
        return (0.04f + stressed + unhappy).coerceAtMost(0.4f)
    }

    /** Who has a bad night tonight, and how: at most one worker, by their id. */
    fun planFor(state: GameState): Map<String, StaffTrouble> {
        val strength = Progression.strength(state, Feature.STAFF_TROUBLE)
        if (strength <= 0f) return emptyMap()
        val candidates = state.employees
            .filter { it.status == EmployeeStatus.ACTIVE && it.role in onTheFloor }
            .sortedByDescending { risk(it) }
        if (candidates.isEmpty()) return emptyMap()

        val rng = SeededRandomSource(state.seed * 53 + state.day * 11)
        // The first night it can happen, as promised, it does.
        val sure = Progression.debutsToday(state, Feature.STAFF_TROUBLE)
        val chosen = candidates.firstOrNull { rng.nextFloat() < risk(it) * strength } ?: candidates.first().takeIf { sure } ?: return emptyMap()

        val at = EARLIEST + rng.nextFloat() * (LATEST - EARLIEST)
        val sickWeight = chosen.stress / 100f
        val rowWeight = (100 - chosen.morale) / 100f * 0.8f
        val breakWeight = 0.35f
        val pick = rng.nextFloat() * (sickWeight + rowWeight + breakWeight)
        val kind = when {
            pick < sickWeight -> TroubleKind.SICK
            pick < sickWeight + rowWeight -> TroubleKind.ARGUMENT
            else -> TroubleKind.BREAK
        }
        return mapOf(chosen.id.value to StaffTrouble(kind, at, backAt = if (kind == TroubleKind.BREAK) at + BREAK_LENGTH else null))
    }

    /** How long a long break lasts. */
    const val BREAK_LENGTH = 40f

    private const val EARLIEST = 40f
    private const val LATEST = 140f
}
