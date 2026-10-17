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
     * Customers per working employee that a shift can absorb without
     * anyone's stress moving. Busier than this and stress climbs; quieter
     * and it eases off. Phase 6 balance: before this existed stress could
     * only ever go up, so every employee burned out on a fixed schedule no
     * matter what the player did.
     */
    const val COMFORTABLE_STAFFING_RATIO = 8.0

    /** How much someone's daily wage goes up each time they get better at the job (every ten days worked). */
    const val RAISE_PER_STEP = 2L

    /** How much more stressed everyone is after any shift, before counting how busy it was. */
    const val DAILY_FATIGUE = 4

    /**
     * Stress change at the end of a working day — heavier when the shift was
     * busy relative to how much staff was on hand. This is the "exhausted
     * employee" half of the chain-reaction example in the product brief;
     * the other half (slow service -> impatient customers) lives in
     * [com.recipefordisaster.domain.simulation.ServiceSimulator].
     *
     * Also where working days turn into experience: every 10 days worked
     * adds a point of skill, so a long-serving employee is worth keeping.
     */
    fun applyEndOfDayStress(employee: Employee, staffingRatio: Double): Employee {
        if (employee.status != EmployeeStatus.ACTIVE) return employee

        // Every shift tires people out a little, however many helpers there are; a short-staffed one far
        // more. Only a day off really clears it.
        val stressChange = DAILY_FATIGUE + ((staffingRatio - COMFORTABLE_STAFFING_RATIO) * 3.0).toInt().coerceIn(-3, 20)
        // Overworked days, or simply running on fumes, erode morale; calmer ones slowly recover it.
        val moraleDrift = when {
            employee.stress > 90 -> -5
            employee.stress > 75 -> -3
            staffingRatio > COMFORTABLE_STAFFING_RATIO * 1.5 -> -2
            else -> 1
        }
        val experience = employee.experienceDays + 1
        val skillGain = if (experience % 10 == 0) 1 else 0

        return employee.copy(
            stress = (employee.stress + stressChange).coerceIn(0, 100),
            morale = (employee.morale + moraleDrift).coerceIn(0, 100),
            experienceDays = experience,
            skill = (employee.skill + skillGain).coerceIn(0, 95),
            // Getting better at the job, they ask for a little more money too.
            salaryPerDay = employee.salaryPerDay + skillGain * RAISE_PER_STEP,
        )
    }

    /** How much someone's mood drops when they storm out after a row, and the mood below which they don't come back. */
    const val ROW_MORALE_HIT = 20
    const val QUIT_BELOW_MORALE = 15

    /** How many days someone who went home ill is off for. */
    const val SICK_DAYS_AFTER_GOING_HOME = 2

    /**
     * What a bad night leaves behind (on top of the usual end-of-day changes): someone who went home ill is off
     * for a couple of days, someone who stormed out is in a worse mood and, if it was bad enough, quits. A long
     * break leaves nothing behind.
     */
    fun afterBadNight(employee: Employee, kind: com.recipefordisaster.domain.service.TroubleKind): Employee = when (kind) {
        com.recipefordisaster.domain.service.TroubleKind.SICK ->
            employee.copy(status = EmployeeStatus.SICK, sickDaysRemaining = SICK_DAYS_AFTER_GOING_HOME)
        com.recipefordisaster.domain.service.TroubleKind.ARGUMENT -> {
            val morale = (employee.morale - ROW_MORALE_HIT).coerceIn(0, 100)
            employee.copy(morale = morale, status = if (morale < QUIT_BELOW_MORALE) EmployeeStatus.QUIT else employee.status)
        }
        com.recipefordisaster.domain.service.TroubleKind.BREAK -> employee
    }

    /** A line for the morning's log about how [before]'s bad night ended. */
    fun badNightNote(before: Employee, after: Employee, kind: com.recipefordisaster.domain.service.TroubleKind): String = when (kind) {
        com.recipefordisaster.domain.service.TroubleKind.SICK -> "${before.name} went home ill last night and is off for $SICK_DAYS_AFTER_GOING_HOME days."
        com.recipefordisaster.domain.service.TroubleKind.ARGUMENT ->
            if (after.status == EmployeeStatus.QUIT) "${before.name} stormed out after a row last night and quit for good." else "${before.name} stormed out after a row last night and is in a bad mood."
        com.recipefordisaster.domain.service.TroubleKind.BREAK -> "${before.name} took a very long break last night."
    }

    /** End of a day off: back on the rota tomorrow, a lot less frazzled. */
    fun returnFromDayOff(employee: Employee): Employee {
        if (employee.status != EmployeeStatus.ON_BREAK) return employee
        return employee.copy(
            status = EmployeeStatus.ACTIVE,
            stress = (employee.stress - 35).coerceIn(0, 100),
            morale = (employee.morale + 5).coerceIn(0, 100),
        )
    }

    /** Counts a sick employee one day closer to recovery, returning them to work when it reaches zero. */
    fun advanceSickness(employee: Employee): Employee {
        if (employee.status != EmployeeStatus.SICK) return employee
        val remaining = (employee.sickDaysRemaining - 1).coerceAtLeast(0)
        return if (remaining == 0) {
            employee.copy(status = EmployeeStatus.ACTIVE, sickDaysRemaining = 0, stress = (employee.stress - 15).coerceIn(0, 100))
        } else {
            employee.copy(sickDaysRemaining = remaining)
        }
    }
}
