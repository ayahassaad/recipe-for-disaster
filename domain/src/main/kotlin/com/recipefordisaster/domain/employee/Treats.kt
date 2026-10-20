package com.recipefordisaster.domain.employee

/**
 * Small rewards for the whole team, paid for from the tip jar. Tips fill the jar every night (they're
 * still part of the takings); a treat takes coins out of it and cheers everyone who's working up.
 */
enum class Treat(val cost: Long, val morale: Int, val stress: Int) {
    COFFEE_AND_PASTRIES(cost = 15, morale = 3, stress = 3),
    PIZZA_FOR_THE_TEAM(cost = 30, morale = 6, stress = 6),
    AN_EVENING_OUT(cost = 70, morale = 12, stress = 14),
    ;

    /** [employee] after the treat: happier and less frayed. */
    fun cheer(employee: Employee): Employee = employee.copy(
        morale = (employee.morale + morale).coerceIn(0, 100),
        stress = (employee.stress - stress).coerceIn(0, 100),
    )
}

object Treats {
    /** What a treat is, in words, for the morning log. */
    fun describe(treat: Treat): String = when (treat) {
        Treat.COFFEE_AND_PASTRIES -> "coffee and pastries"
        Treat.PIZZA_FOR_THE_TEAM -> "pizza"
        Treat.AN_EVENING_OUT -> "an evening out"
    }
}
