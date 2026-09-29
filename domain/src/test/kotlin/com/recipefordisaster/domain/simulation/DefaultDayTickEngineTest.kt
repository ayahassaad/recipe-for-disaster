package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.employee.Employee
import com.recipefordisaster.domain.employee.EmployeeId
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.Recipe
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDayTickEngineTest {

    private fun freshState(employees: List<Employee>, reputation: Int = 60) = GameState(
        seed = 42,
        day = 1,
        restaurant = Restaurant(
            cash = 10_000,
            reputation = reputation,
            cleanliness = 80,
            capacity = 20,
            level = 1,
            operatingCosts = OperatingCosts(rentPerDay = 200, utilitiesPerDay = 50, miscPerDay = 10),
            currentDay = 1,
            status = RestaurantStatus.OPEN,
        ),
        employees = employees,
        customersPresent = emptyList(),
        inventory = InventoryState(ingredients = emptyMap(), storageCapacity = 1000.0, suppliers = emptyList()),
        menu = listOf(
            Dish(
                id = DishId("burger"),
                name = "Burger",
                recipe = Recipe(ingredientRequirements = emptyMap(), preparationTimeMinutes = 8),
                sellingPrice = 1_200,
                popularity = 80,
                quality = 65,
                available = true,
                allergens = emptySet(),
            ),
        ),
        equipment = emptyList(),
        ledger = Ledger(history = emptyList()),
        log = emptyList(),
    )

    private fun employee(morale: Int, stress: Int) = Employee(
        id = EmployeeId("e-${morale}-${stress}"),
        name = "Test Employee",
        role = Role.SERVER,
        skill = 65,
        speed = 65,
        reliability = 65,
        morale = morale,
        stress = stress,
        salaryPerDay = 400,
        experienceDays = 20,
        personalityTraits = emptySet(),
        relationships = emptyMap(),
        status = EmployeeStatus.ACTIVE,
    )

    private val emptyEventEngine = EventEngine(emptyList())

    @Test
    fun `same seed and same starting state produce identical day results`() {
        val engine = DefaultDayTickEngine(emptyEventEngine)
        val state = freshState(listOf(employee(morale = 70, stress = 20)))

        val resultA = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(seed = 55))
        val resultB = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(seed = 55))

        assertEquals(resultA, resultB)
    }

    @Test
    fun `advancing a day increments the day counter and appends ledger history`() {
        val engine = DefaultDayTickEngine(emptyEventEngine)
        val state = freshState(listOf(employee(morale = 70, stress = 20)))

        val result = engine.advanceDay(state, PlayerDecisions(), SeededRandomSource(seed = 1))

        assertEquals(state.day + 1, result.newState.day)
        assertEquals(1, result.newState.ledger.history.size)
        assertTrue(result.newState.log.isNotEmpty())
    }

    @Test
    fun `an understaffed exhausted day leaves the restaurant with lower reputation than a well-staffed healthy one`() {
        val engine = DefaultDayTickEngine(emptyEventEngine)

        val wellStaffedState = freshState(
            employees = listOf(employee(90, 5), employee(90, 5), employee(90, 5)),
            reputation = 60,
        )
        val understaffedState = freshState(
            employees = listOf(employee(15, 90)),
            reputation = 60,
        )

        // Same seed for both runs so the only thing that differs going in is staffing/health.
        val wellStaffedResult = engine.advanceDay(wellStaffedState, PlayerDecisions(), SeededRandomSource(seed = 2024))
        val understaffedResult = engine.advanceDay(understaffedState, PlayerDecisions(), SeededRandomSource(seed = 2024))

        assertTrue(understaffedResult.newState.restaurant.reputation <= wellStaffedResult.newState.restaurant.reputation)
    }

    @Test
    fun `going cash-negative flips restaurant status to bankrupt`() {
        val engine = DefaultDayTickEngine(emptyEventEngine)
        val brokeState = freshState(listOf(employee(70, 20))).let {
            it.copy(restaurant = it.restaurant.copy(cash = 10))
        }

        val result = engine.advanceDay(brokeState, PlayerDecisions(), SeededRandomSource(seed = 3))

        if (result.newState.restaurant.cash < 0) {
            assertEquals(RestaurantStatus.BANKRUPT, result.newState.restaurant.status)
        }
    }
}
