package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import kotlinx.serialization.Serializable

/**
 * How the kitchen is feeling tonight, from the cooks' morale and stress. A grumpy chef burns some
 * dishes and has to cook them again; a happy one turns some plates into a chef's special.
 */
@Serializable
enum class ChefMood {
    GRUMPY,
    NORMAL,
    HAPPY,
    ;

    companion object {
        /** Below this morale (or at this stress or above) the cooks are grumpy. */
        const val GRUMPY_MORALE = 45
        const val GRUMPY_STRESS = 75

        /** At this morale or above, and calm enough, they're happy. */
        const val HAPPY_MORALE = 75
        const val HAPPY_STRESS = 50

        /** Tonight's mood in the kitchen; with no cooks on shift it's just a normal night. */
        fun of(employees: List<Employee>): ChefMood {
            val cooks = employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.COOK }
            if (cooks.isEmpty()) return NORMAL
            val morale = cooks.map { it.morale }.average()
            val stress = cooks.map { it.stress }.average()
            return when {
                morale < GRUMPY_MORALE || stress >= GRUMPY_STRESS -> GRUMPY
                morale >= HAPPY_MORALE && stress < HAPPY_STRESS -> HAPPY
                else -> NORMAL
            }
        }
    }
}
