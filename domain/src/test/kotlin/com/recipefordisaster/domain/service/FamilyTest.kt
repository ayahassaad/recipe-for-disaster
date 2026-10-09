package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(day = 5, restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    @Test
    fun `some parties of two are a grown-up with a child, but not on the first night`() {
        val families = (1L..20L).flatMap { open(seed = it).parties }.filter { it.family }
        assertTrue(families.isNotEmpty())
        assertTrue(families.all { it.guests.size == 2 && it.special == null })
        assertTrue((1L..20L).none { seed -> open(start.copy(day = 1), seed).parties.any { it.family } })
    }
}
