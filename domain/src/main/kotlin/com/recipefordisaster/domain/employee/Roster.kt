package com.recipefordisaster.domain.employee

/**
 * A weekly roster: for each worker, the weekdays they have off, week after week, without anyone having to
 * remember. Day 1 starts a week, like the weekly goals. A rostered day off works like any other day off
 * (they don't work, they aren't paid, and they come back much less stressed).
 */
object Roster {

    const val DAYS_IN_WEEK = 7

    /** Nobody can have more than this many days off a week, or they'd hardly be working. */
    const val MAX_DAYS_OFF = 4

    /** Which day of the week [day] is, from 0 (the first) to 6. */
    fun weekday(day: Int): Int = (day - 1).mod(DAYS_IN_WEEK)

    /** Whether [employee] has today off, by the roster. */
    fun isOff(employee: Employee, day: Int): Boolean = weekday(day) in employee.offDays

    /** Puts [weekday] on or takes it off [employee]'s days off (up to [MAX_DAYS_OFF]). */
    fun toggle(employee: Employee, weekday: Int): Employee {
        if (weekday !in 0 until DAYS_IN_WEEK) return employee
        return when {
            weekday in employee.offDays -> employee.copy(offDays = employee.offDays - weekday)
            employee.offDays.size >= MAX_DAYS_OFF -> employee
            else -> employee.copy(offDays = employee.offDays + weekday)
        }
    }

    /** How many days a week they work. */
    fun daysWorked(employee: Employee): Int = DAYS_IN_WEEK - employee.offDays.size

    /** What a week costs in wages (days off are unpaid). */
    fun weeklyWages(employee: Employee): Long = employee.salaryPerDay * daysWorked(employee)
}

/** Giving someone a raise: a bit more money a day, for a better mood, now and then. */
object Raises {

    /** How much more a day a raise pays, in percent (and the least it can be). */
    const val RAISE_PERCENT = 10
    const val MIN_RAISE = 2L

    /** How much better their mood is for it. */
    const val MORALE_BOOST = 15

    /** Days before the same person can be given another. */
    const val COOLDOWN_DAYS = 10

    fun amount(employee: Employee): Long = maxOf(MIN_RAISE, employee.salaryPerDay * RAISE_PERCENT / 100)

    fun canRaise(employee: Employee, day: Int): Boolean =
        employee.status == EmployeeStatus.ACTIVE && day - employee.lastRaiseDay >= COOLDOWN_DAYS

    /** Days until [employee] can be given another raise (0 if they can now). */
    fun daysToWait(employee: Employee, day: Int): Int = (COOLDOWN_DAYS - (day - employee.lastRaiseDay)).coerceAtLeast(0)

    fun raise(employee: Employee, day: Int): Employee = employee.copy(
        salaryPerDay = employee.salaryPerDay + amount(employee),
        morale = (employee.morale + MORALE_BOOST).coerceAtMost(100),
        lastRaiseDay = day,
    )
}
