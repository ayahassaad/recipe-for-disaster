package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ChefMoodTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(day = 5, restaurant = it.restaurant.copy(tables = 6)) }

    private fun withCooks(morale: Int, stress: Int) =
        start.copy(employees = start.employees.map { if (it.role == Role.COOK) it.copy(morale = morale, stress = stress) else it })

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    @Test
    fun `the kitchen's mood follows the cooks' morale and stress`() {
        assertEquals(ChefMood.NORMAL, open(withCooks(morale = 70, stress = 20)).chefMood)
        assertEquals(ChefMood.GRUMPY, open(withCooks(morale = 30, stress = 20)).chefMood)
        assertEquals(ChefMood.GRUMPY, open(withCooks(morale = 80, stress = 80)).chefMood)
        assertEquals(ChefMood.HAPPY, open(withCooks(morale = 85, stress = 20)).chefMood)
        assertEquals(ChefMood.NORMAL, ChefMood.of(emptyList()))
    }
}
