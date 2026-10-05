package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentOperations
import kotlin.math.pow

/**
 * How much the kitchen can cook today and how good it'll taste (Phase 6).
 * This is what makes "who's cooking" a real decision: cooks set the
 * throughput ceiling, so a sick chef or a broken oven turns directly into
 * customers who sat down and never got fed — and from there into reputation.
 */
object KitchenModel {

    /** Fraction of a cook's output that anyone else manages when they have to jump on the line. */
    private const val NON_COOK_KITCHEN_SHARE = 0.25

    fun mealCapacity(employees: List<Employee>, equipment: List<Equipment>): Int {
        val working = employees.filter { it.status == EmployeeStatus.ACTIVE }
        val rawMeals = working.sumOf { employee ->
            val meals = 6.0 + EmployeePerformance.effectiveServiceSpeed(employee) * 0.25
            if (employee.role == Role.COOK) meals else meals * NON_COOK_KITCHEN_SHARE
        }
        // Every broken machine halves what the kitchen can get out.
        val brokenCount = equipment.count { EquipmentOperations.isBroken(it) }
        // Better kit gets more out of the same cooks.
        return (rawMeals * 0.5.pow(brokenCount) * com.recipefordisaster.domain.equipment.EquipmentCatalog.cookingSpeed(equipment)).toInt()
    }

    /**
     * Bonus (or penalty) added to every dish's quality today. Skilled cooks
     * lift it, a broken machine or a filthy kitchen drags it down, and a
     * kitchen with no cook at all is a server microwaving things.
     */
    fun qualityBonus(employees: List<Employee>, equipment: List<Equipment>, cleanliness: Int): Int {
        val cooks = employees.filter { it.status == EmployeeStatus.ACTIVE && it.role == Role.COOK }
        val skillEffect = if (cooks.isEmpty()) -15 else ((cooks.map { it.skill }.average() - 55) / 3).toInt()
        val equipmentEffect = if (equipment.any { EquipmentOperations.isBroken(it) }) -10 else 3 * equipment.sumOf { it.capacityEffect }
        val cleanlinessEffect = if (cleanliness < 40) -5 else 0
        return skillEffect + equipmentEffect + cleanlinessEffect
    }
}
