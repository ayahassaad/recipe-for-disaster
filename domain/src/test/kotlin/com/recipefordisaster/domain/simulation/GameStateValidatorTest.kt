package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStateValidatorTest {

    private fun validRestaurant() = Restaurant(
        cash = 5_000, reputation = 50, cleanliness = 80, capacity = 20, level = 1,
        operatingCosts = OperatingCosts(100, 20, 10), currentDay = 1, status = RestaurantStatus.OPEN,
    )

    private fun baseState(restaurant: Restaurant = validRestaurant(), employees: List<Employee> = emptyList()) = GameState(
        seed = 1, day = 1, restaurant = restaurant, employees = employees, customersPresent = emptyList(),
        inventory = InventoryState(emptyMap(), 100.0, emptyList()), menu = emptyList(), equipment = emptyList(),
        ledger = Ledger(emptyList()), log = emptyList(),
    )

    private fun employee(morale: Int = 50, stress: Int = 50, skill: Int = 50) = Employee(
        id = EmployeeId("e1"), name = "Test", role = Role.COOK, skill = skill, speed = 50, reliability = 50,
        morale = morale, stress = stress, salaryPerDay = 500, experienceDays = 10, personalityTraits = emptySet(),
        relationships = emptyMap(), status = EmployeeStatus.ACTIVE,
    )

    @Test
    fun `a freshly constructed valid state has no problems`() {
        assertTrue(GameStateValidator.isValid(baseState()))
    }

    @Test
    fun `negative day is flagged`() {
        val state = baseState().copy(day = -1)
        assertTrue(GameStateValidator.validate(state).any { it.contains("day") })
    }

    @Test
    fun `out-of-range reputation is flagged`() {
        val state = baseState(restaurant = validRestaurant().copy(reputation = 150))
        assertTrue(GameStateValidator.validate(state).any { it.contains("reputation") })
    }

    @Test
    fun `out-of-range employee morale is flagged`() {
        val state = baseState(employees = listOf(employee(morale = -10)))
        assertTrue(GameStateValidator.validate(state).any { it.contains("morale") })
    }

    @Test
    fun `multiple problems are all reported, not just the first`() {
        val state = baseState(
            restaurant = validRestaurant().copy(reputation = -5, cleanliness = 200),
            employees = listOf(employee(morale = 500)),
        )
        assertTrue(GameStateValidator.validate(state).size >= 3)
    }
}
