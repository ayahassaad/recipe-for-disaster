package com.recipefordisaster.domain.economy

import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.equipment.Equipment
import com.recipefordisaster.domain.equipment.EquipmentId
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import com.recipefordisaster.domain.restaurant.OperatingCosts
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyFinancialsCalculatorTest {

    private val burger = Dish(
        id = DishId("burger"),
        name = "Burger",
        recipe = Recipe(emptyMap(), 5),
        sellingPrice = 1_000,
        popularity = 80,
        quality = 60,
        available = true,
        allergens = emptySet(),
    )

    private fun employee(status: EmployeeStatus) = Employee(
        id = EmployeeId("e1"),
        name = "Test",
        role = Role.COOK,
        skill = 60, speed = 60, reliability = 60, morale = 60, stress = 20,
        salaryPerDay = 500,
        experienceDays = 10,
        personalityTraits = emptySet(),
        relationships = emptyMap(),
        status = status,
    )

    private val fryer = Equipment(
        id = EquipmentId("fryer"),
        name = "Fryer",
        purchaseCost = 2000,
        condition = 80,
        capacityEffect = 10,
        maintenanceCostPerDay = 15,
        failureProbabilityBase = 0.01,
        upgradeLevel = 0,
    )

    @Test
    fun `revenue is computed from dishes sold at their menu price`() {
        val result = DailyFinancialsCalculator.calculate(
            day = 1,
            dishesSold = mapOf(DishId("burger") to 10),
            menu = listOf(burger),
            employees = emptyList(),
            ingredientCosts = 0,
            operatingCosts = OperatingCosts(0, 0, 0),
            equipment = emptyList(),
        )

        assertEquals(10_000L, result.revenue)
    }

    @Test
    fun `only active employees are paid`() {
        val result = DailyFinancialsCalculator.calculate(
            day = 1,
            dishesSold = emptyMap(),
            menu = listOf(burger),
            employees = listOf(employee(EmployeeStatus.ACTIVE), employee(EmployeeStatus.FIRED)),
            ingredientCosts = 0,
            operatingCosts = OperatingCosts(0, 0, 0),
            equipment = emptyList(),
        )

        assertEquals(500L, result.wages)
    }

    @Test
    fun `expenses roll up rent, utilities, wages, ingredients, and maintenance`() {
        val result = DailyFinancialsCalculator.calculate(
            day = 1,
            dishesSold = emptyMap(),
            menu = listOf(burger),
            employees = listOf(employee(EmployeeStatus.ACTIVE)),
            ingredientCosts = 300,
            operatingCosts = OperatingCosts(rentPerDay = 200, utilitiesPerDay = 50, miscPerDay = 10),
            equipment = listOf(fryer),
        )

        // wages 500 + ingredients 300 + rent 200 + utilities 50 + maintenance 15 + misc 10
        assertEquals(1_075L, result.expenses)
    }

    @Test
    fun `profit or loss is revenue minus expenses`() {
        val result = DailyFinancialsCalculator.calculate(
            day = 1,
            dishesSold = mapOf(DishId("burger") to 1),
            menu = listOf(burger),
            employees = emptyList(),
            ingredientCosts = 0,
            operatingCosts = OperatingCosts(rentPerDay = 900, utilitiesPerDay = 0, miscPerDay = 0),
            equipment = emptyList(),
        )

        assertEquals(1_000L - 900L, result.profitOrLoss)
    }
}
