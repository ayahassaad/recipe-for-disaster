package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.Customer
import com.recipefordisaster.domain.customer.CustomerId
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceSimulatorTest {

    private fun customer(patience: Int = 20) = Customer(
        id = CustomerId("c-${(0..1_000_000).random()}"),
        name = "Test Customer",
        patience = patience,
        budget = 5_000,
        preferences = emptySet(),
        dietaryRequirements = emptySet(),
        satisfaction = 70,
        likelihoodOfReturning = 50,
        complaintTendency = 50,
        reviewInfluence = 50,
    )

    private fun employee(morale: Int, stress: Int) = Employee(
        id = EmployeeId("e-${(0..1_000_000).random()}"),
        name = "Test Employee",
        role = Role.SERVER,
        skill = 60,
        speed = 60,
        reliability = 60,
        morale = morale,
        stress = stress,
        salaryPerDay = 1000,
        experienceDays = 10,
        personalityTraits = emptySet(),
        relationships = emptyMap(),
        status = EmployeeStatus.ACTIVE,
    )

    private val dish = Dish(
        id = DishId("burger"),
        name = "Burger",
        recipe = Recipe(ingredientRequirements = emptyMap(), preparationTimeMinutes = 5),
        sellingPrice = 1_000,
        popularity = 80,
        quality = 60,
        available = true,
        allergens = emptySet(),
    )

    /**
     * This is the test that matters most in this file: it directly checks
     * the product brief's headline example (section 5) — low morale +
     * understaffing + a busy evening should measurably hurt satisfaction
     * compared to a well-staffed, healthy-employee scenario. If this test
     * ever goes red, the emergent-chain design principle has broken.
     */
    @Test
    fun `understaffed and exhausted service produces lower satisfaction than well-staffed healthy service`() {
        val arrivals = (1..12).map { customer(patience = 20) }
        val menu = listOf(dish)

        val wellStaffed = ServiceSimulator.simulate(
            arrivals = arrivals,
            employees = listOf(employee(morale = 90, stress = 5), employee(morale = 90, stress = 5), employee(morale = 90, stress = 5)),
            menu = menu,
        )

        val understaffedAndExhausted = ServiceSimulator.simulate(
            arrivals = arrivals,
            employees = listOf(employee(morale = 15, stress = 90)),
            menu = menu,
        )

        val wellStaffedAvgSatisfaction = wellStaffed.outcomes.map { it.satisfaction }.average()
        val understaffedAvgSatisfaction = understaffedAndExhausted.outcomes.map { it.satisfaction }.average()

        assertTrue(understaffedAvgSatisfaction < wellStaffedAvgSatisfaction)
    }

    @Test
    fun `a customer walks away with zero satisfaction when nothing on the menu fits their budget`() {
        val brokeCustomer = customer().copy(budget = 1)
        val result = ServiceSimulator.simulate(
            arrivals = listOf(brokeCustomer),
            employees = listOf(employee(morale = 90, stress = 5)),
            menu = listOf(dish),
        )

        assertTrue(result.outcomes.single().satisfaction == 0)
        assertTrue(result.outcomes.single().dish == null)
    }

    @Test
    fun `reputation model rewards high satisfaction and punishes poor cleanliness`() {
        val goodDayGoodCleanliness = com.recipefordisaster.domain.restaurant.ReputationModel.dailyReputationDelta(
            averageSatisfaction = 90,
            cleanliness = 90,
        )
        val badDayBadCleanliness = com.recipefordisaster.domain.restaurant.ReputationModel.dailyReputationDelta(
            averageSatisfaction = 20,
            cleanliness = 10,
        )

        assertTrue(goodDayGoodCleanliness > badDayBadCleanliness)
    }
}
