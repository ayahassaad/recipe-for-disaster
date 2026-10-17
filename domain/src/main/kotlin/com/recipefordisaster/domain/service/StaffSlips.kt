package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.progression.Progression
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

    /**
     * A slip: at [at] seconds into the night, a worker either drops what they're carrying, or (a server, once
     * guests can ask for things) takes someone the wrong dish, which that table will want you to put right.
     */
    @kotlinx.serialization.Serializable
    data class Slip(val at: Float, val mixUp: Boolean = false)

    /** The slip each worker who'll slip tonight will make, by their id. */
    fun planFor(state: GameState): Map<String, Slip> = buildMap {
        state.employees
            .filter { it.status == EmployeeStatus.ACTIVE && it.role in carriers }
            .forEach { employee ->
                val rng = SeededRandomSource(state.seed * 31 + state.day * 7 + employee.id.value.hashCode())
                val slips = rng.nextFloat() < chance(employee)
                val at = EARLIEST + rng.nextFloat() * (LATEST - EARLIEST)
                val mixUpRoll = rng.nextFloat()
                val canMixUp = employee.role in servers && Progression.has(state, Feature.GUEST_REQUESTS)
                if (slips) put(employee.id.value, Slip(at, mixUp = canMixUp && mixUpRoll < MIX_UP_SHARE))
            }
    }

    /** Servers carry out the food, so they're the ones who can mix orders up. */
    private val servers = setOf(Role.SERVER, Role.MANAGER)

    /** Of a server's slips, the share that are a wrong dish rather than a dropped tray. */
    private const val MIX_UP_SHARE = 0.5f

    private const val EARLIEST = 25f
    private const val LATEST = 170f
}
