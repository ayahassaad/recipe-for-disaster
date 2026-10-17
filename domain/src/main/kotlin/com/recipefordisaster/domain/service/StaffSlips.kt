package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.SeededRandomSource

/**
 * Staff aren't perfect: sooner or later, carrying a tray, someone trips and drops it. How likely depends on
 * who they are: new hires are clumsy for their first few shifts, and anyone who's tired, or not very
 * reliable, drops things too. Who slips, and when, is decided when the doors open, from the game's seed
 * and the day (not from the night's own dice, so nothing else about the night changes).
 */
object StaffSlips {

    /** How many shifts someone counts as new for. */
    const val NEW_SHIFTS = 3

    /** Who slips: the ones who carry things out and about. Cooks and bartenders stay put in their corners. */
    private val carriers = setOf(Role.SERVER, Role.MANAGER, Role.DISHWASHER, Role.BUSSER)

    /** How likely a trip is on someone's [shift]th shift (0 is their first) just for being new: near certain at first, rarer as they settle in. */
    fun newHireChance(shift: Int): Float = when (shift) {
        0 -> 1f
        1 -> 0.7f
        2 -> 0.4f
        else -> 0f
    }

    /** How likely [employee] is to drop something tonight: being new, tired or unreliable all add to it. */
    fun chance(employee: Employee): Float {
        val clumsy = EmployeePerformance.errorProbability(employee).toFloat()
        val newHire = newHireChance(employee.experienceDays)
        return (1f - (1f - newHire) * (1f - clumsy)).coerceIn(0f, 1f)
    }

    /** A word for how often someone slips, for the staff sheet. */
    enum class Sloppiness { RARELY, SOMETIMES, OFTEN }

    fun sloppiness(employee: Employee): Sloppiness = when {
        chance(employee) < 0.2f -> Sloppiness.RARELY
        chance(employee) < 0.5f -> Sloppiness.SOMETIMES
        else -> Sloppiness.OFTEN
    }

    /** The time in the night each worker who'll slip tonight will do it, by their id. */
    fun planFor(state: GameState): Map<String, Float> = buildMap {
        state.employees
            .filter { it.status == EmployeeStatus.ACTIVE && it.role in carriers }
            .forEach { employee ->
                val rng = SeededRandomSource(state.seed * 31 + state.day * 7 + employee.id.value.hashCode())
                val slips = rng.nextFloat() < chance(employee)
                val at = EARLIEST + rng.nextFloat() * (LATEST - EARLIEST)
                if (slips) put(employee.id.value, at)
            }
    }

    private const val EARLIEST = 25f
    private const val LATEST = 170f
}
