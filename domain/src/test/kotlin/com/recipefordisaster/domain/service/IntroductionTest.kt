package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroductionTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    private fun waitFor(night: ServiceNight, condition: (ServiceNight) -> Boolean): ServiceNight {
        var n = night
        var guard = 0
        while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f)
        return n
    }

    @Test
    fun `the first spill of the game is explained, once`() {
        var n = waitFor(open(start.copy(day = 3))) { it.messesOnFloor.isNotEmpty() }
        assertEquals(Feature.SPILLS, n.introductionDue())
        n = n.introduce(Feature.SPILLS)
        assertNull(n.introductionDue())
    }

    @Test
    fun `the first drinks order is explained`() {
        val n = waitFor(open(start.copy(day = 11))) { night -> night.parties.any { it.stage == ServiceNight.Stage.WANTS_DRINKS } }
        assertEquals(Feature.DRINKS, n.introductionDue())
    }

    @Test
    fun `the first rat is explained, but not again in a later game where it's been seen`() {
        val n = waitFor(open(start.copy(day = 15))) { it.activeChaos != null }
        assertEquals(Feature.RAT, n.introductionDue())
        val seen = waitFor(open(start.copy(day = 15, introductionsSeen = setOf(Feature.RAT)))) { it.activeChaos != null }
        assertTrue(seen.introductionDue() != Feature.RAT)
    }

    @Test
    fun `nothing needs explaining on a plain first night`() {
        var n = open(start)
        repeat(2_000) {
            assertNull(n.introductionDue())
            n = n.advance(0.05f)
        }
    }
}
