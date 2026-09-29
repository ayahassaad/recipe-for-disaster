package com.recipefordisaster.domain.employee

/**
 * Turns an employee's stats into the two numbers the rest of the simulation
 * actually cares about: how fast they work, and how likely they are to
 * make a mistake. Kept as pure functions of [Employee] so they're trivial
 * to unit test and so no two employees with different stats ever behave
 * identically, per the project's core design principle.
 *
 * The exact coefficients below are first-pass balance, not tuned numbers —
 * flagged here rather than presented as final, since real balance only
 * comes from playtesting once there's a playable loop.
 */
object EmployeePerformance {

    fun effectiveServiceSpeed(employee: Employee): Double {
        if (employee.status != EmployeeStatus.ACTIVE) return 0.0

        val base = (employee.skill + employee.speed) / 2.0
        val moralePenalty = (100 - employee.morale.coerceIn(0, 100)) / 100.0
        val stressPenalty = employee.stress.coerceIn(0, 100) / 100.0

        // Morale and stress each shave up to ~40% off an otherwise-capable
        // employee's effective speed — enough to matter, not enough for a
        // single bad day to zero someone out entirely.
        val penalty = (moralePenalty * 0.4) + (stressPenalty * 0.4)
        return (base * (1.0 - penalty)).coerceAtLeast(5.0)
    }

    fun errorProbability(employee: Employee): Double {
        if (employee.status != EmployeeStatus.ACTIVE) return 0.0

        val unreliability = (100 - employee.reliability.coerceIn(0, 100)) / 100.0
        val stressFactor = employee.stress.coerceIn(0, 100) / 100.0

        return ((unreliability * 0.5) + (stressFactor * 0.5)).coerceIn(0.0, 0.9)
    }

    /**
     * Stress creep at the end of a working day — heavier when the shift was
     * busy relative to how much staff was on hand. This is the "exhausted
     * employee" half of the chain-reaction example in the product brief;
     * the other half (slow service -> impatient customers) lives in
     * [com.recipefordisaster.domain.simulation.ServiceSimulator].
     */
    fun applyEndOfDayStress(employee: Employee, staffingRatio: Double): Employee {
        if (employee.status != EmployeeStatus.ACTIVE) return employee

        val stressGain = (staffingRatio * 5.0).roundToIntClamped(0, 30)
        val moraleDrift = if (staffingRatio > 2.0) -2 else 1 // overworked days erode morale; calmer ones slowly recover it

        return employee.copy(
            stress = (employee.stress + stressGain).coerceIn(0, 100),
            morale = (employee.morale + moraleDrift).coerceIn(0, 100),
        )
    }

    private fun Double.roundToIntClamped(min: Int, max: Int): Int =
        this.toInt().coerceIn(min, max)
}
