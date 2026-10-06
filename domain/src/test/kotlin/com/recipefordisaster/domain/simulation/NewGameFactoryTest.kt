package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.restaurant.RestaurantStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewGameFactoryTest {

    @Test
    fun `a new game starts on day one, open, with positive cash`() {
        val state = NewGameFactory.create(seed = 42L)

        assertEquals(1, state.day)
        assertEquals(RestaurantStatus.OPEN, state.restaurant.status)
        assertTrue(state.restaurant.cash > 0)
    }

    @Test
    fun `a new game starts with at least one employee, one dish, and stocked ingredients`() {
        val state = NewGameFactory.create(seed = 42L)

        assertTrue(state.employees.isNotEmpty())
        assertTrue(state.menu.isNotEmpty())
        assertTrue(state.inventory.ingredients.isNotEmpty())
        assertTrue(state.menu.all { dish -> dish.available })
    }

    @Test
    fun `a new game passes GameStateValidator`() {
        val state = NewGameFactory.create(seed = 42L)

        assertEquals(emptyList<String>(), GameStateValidator.validate(state))
    }

    @Test
    fun `every dish's ingredient requirements exist in starting inventory`() {
        val state = NewGameFactory.create(seed = 42L)

        for (dish in state.menu) {
            for (ingredientId in dish.recipe.ingredientRequirements.keys) {
                assertTrue(
                    "Dish ${dish.name} requires $ingredientId which isn't in starting inventory",
                    ingredientId in state.inventory.ingredients,
                )
            }
        }
    }

    @Test
    fun `creating a new game is deterministic for a given seed`() {
        val first = NewGameFactory.create(seed = 99L)
        val second = NewGameFactory.create(seed = 99L)

        assertEquals(first, second)
    }

    @Test
    fun `a new restaurant starts with just a cook, and servers are hired later`() {
        val state = NewGameFactory.create(seed = 7L)
        assertEquals(listOf(com.recipefordisaster.domain.employee.Role.COOK), state.employees.map { it.role })
    }

    @Test
    fun `the player counts as a worker, so a lone cook isn't run ragged by a modest night`() {
        val state = NewGameFactory.create(seed = 7L).let { it.copy(restaurant = it.restaurant.copy(tables = 6)) }
        val engine = DefaultDayTickEngine(com.recipefordisaster.domain.event.EventEngine(emptyList()))
        val setup = engine.openService(state, PlayerDecisions(), SeededRandomSource(1L))
        val night = com.recipefordisaster.domain.service.ServiceNight.open(setup, SeededRandomSource(2L))
        assertEquals(setup.arrivals.size / 2.0, night.staffingRatio, 0.001)
    }
}
