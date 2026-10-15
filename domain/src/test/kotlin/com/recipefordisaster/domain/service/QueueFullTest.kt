package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueFullTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(): ServiceNight {
        val state = NewGameFactory.create(seed = 5L)
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
    }

    @Test
    fun `a sixth stop is ignored, and the night says its list is full`() {
        var night = open().tapDishStation()
        assertFalse(night.queueFull)

        night = night.tapMopBucket().tapPass().tapChef().tapBar().tapPlate(1)
        assertTrue(night.queueFull)

        assertSame(night, night.tapPlate(2))
    }
}
