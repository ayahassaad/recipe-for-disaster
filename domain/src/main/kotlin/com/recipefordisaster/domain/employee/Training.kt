package com.recipefordisaster.domain.employee

/**
 * A morning's training for someone already on the staff: they come out of it better at the job.
 * The better they already are, the more it costs to teach them anything new.
 */
object Training {

    /** How much better at the job a morning's training makes someone. */
    const val SKILL_BOOST = 8

    /** Nobody gets better than this, however much training they have. */
    const val MAX_SKILL = 95

    fun cost(employee: Employee): Long = 60L + employee.skill * 2L

    fun canTrain(employee: Employee): Boolean = employee.status == EmployeeStatus.ACTIVE && employee.skill < MAX_SKILL

    fun train(employee: Employee): Employee = employee.copy(skill = (employee.skill + SKILL_BOOST).coerceAtMost(MAX_SKILL))
}
