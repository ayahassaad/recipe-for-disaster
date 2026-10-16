package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoTapTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(): ServiceNight {
        val state = NewGameFactory.create(seed = 5L)
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
    }

    @Test
    fun `there is nothing to undo when you aren't going anywhere`() {
        val night = open()
        assertFalse(night.canUndo)
        assertSame(night, night.undoLastTap())
    }

    @Test
    fun `undo takes back the last stop you lined up, one at a time`() {
        val night = open().tapDishStation().tapMopBucket().tapChef()
        assertEquals(listOf(ServiceNight.Errand.VisitMopBucket, ServiceNight.Errand.HandIn), night.player.queue)

        val once = night.undoLastTap()
        assertEquals(listOf(ServiceNight.Errand.VisitMopBucket), once.player.queue)
        assertEquals(ServiceNight.Errand.VisitDishStation, once.player.errand)

        val twice = once.undoLastTap()
        assertTrue(twice.player.queue.isEmpty())
        assertEquals(ServiceNight.Errand.VisitDishStation, twice.player.errand)
    }

    @Test
    fun `with nothing lined up, undo stops the walk where you are`() {
        val walking = open().tapDishStation().advance(0.3f)
        assertTrue(walking.canUndo)

        val stopped = walking.undoLastTap()
        assertNull(stopped.player.errand)
        assertFalse(stopped.canUndo)
        // And they stay put rather than carrying on to the station.
        val later = stopped.advance(30f).player
        assertEquals(stopped.player.position(stopped.time).x, later.position(later.routeEnd).x, 0.001f)
    }
}
