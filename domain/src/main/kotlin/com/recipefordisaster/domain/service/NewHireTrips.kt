package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource

/**
 * Someone new on the staff is clumsy for their first few shifts: sooner or later, carrying a tray, they
 * trip and drop it. Who trips, and when, is decided when the doors open, from the game's seed and the
 * day (not from the night's own dice, so nothing else about the night changes).
 */
object NewHireTrips {

    /** How many shifts someone counts as new for. */
    const val NEW_SHIFTS = 3

    /** Who trips: the ones who carry things out and about. Cooks and bartenders stay put in their corners. */
    private val carriers = setOf(Role.SERVER, Role.MANAGER, Role.DISHWASHER, Role.BUSSER)

    /** How likely a trip is on someone's [shift]th shift (0 is their first): near certain at first, rarer as they settle in. */
    fun chance(shift: Int): Float = when (shift) {
        0 -> 1f
        1 -> 0.7f
        2 -> 0.4f
        else -> 0f
    }

    /** The time in the night each new hire who'll trip tonight will do it, by their id. */
    fun planFor(state: GameState): Map<String, Float> = buildMap {
        state.employees
            .filter { it.status == EmployeeStatus.ACTIVE && it.role in carriers && it.experienceDays < NEW_SHIFTS }
            .forEach { employee ->
                val rng = SeededRandomSource(state.seed * 31 + state.day * 7 + employee.id.value.hashCode())
                val trips = rng.nextFloat() < chance(employee.experienceDays)
                val at = EARLIEST + rng.nextFloat() * (LATEST - EARLIEST)
                if (trips) put(employee.id.value, at)
            }
    }

    private const val EARLIEST = 25f
    private const val LATEST = 170f
}
