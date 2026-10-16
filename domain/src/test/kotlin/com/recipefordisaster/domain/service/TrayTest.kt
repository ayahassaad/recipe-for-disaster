package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.HandItem
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrayTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(state: GameState = NewGameFactory.create(seed = 5L)): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))

    /** A night where a party is seated, with their drinks poured and waiting on the bar. */
    private fun withTrayWaiting(state: GameState = NewGameFactory.create(seed = 5L)): Pair<ServiceNight, ServiceNight.Party> {
        var night = open(state)
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }
        night = night.copy(parties = night.parties.map {
            if (it.id == party.id) it.copy(stage = Stage.DRINKS_ORDERED, drinksOrderedAt = night.time, stageSince = night.time, trayAt = night.time, heldBy = null) else it
        })
        return night to night.parties.first { it.id == party.id }
    }

    @Test
    fun `a poured tray shows up on the bar, with room for three`() {
        val (night, party) = withTrayWaiting()
        assertEquals(listOf(party.id), night.traysOnBar.map { it.id })
        assertEquals(3, ServiceFloor.TRAYS_ON_BAR)
    }

    @Test
    fun `you can pick a tray up and carry it to the table`() {
        val (night, party) = withTrayWaiting()

        var n = night.tapTray(party.id)
        assertTrue(n.player.errand is ServiceNight.Errand.PickUpDrinks)
        var steps = 0
        while (party.id !in n.player.drinks && steps++ < 600) n = n.advance(0.1f)

        assertTrue(party.id in n.player.drinks)
        assertTrue(n.traysOnBar.isEmpty())
        assertNull(n.parties.first { it.id == party.id }.trayAt)

        n = n.tapTable(party.table!!)
        steps = 0
        while (n.parties.first { it.id == party.id }.stage != Stage.DRINKING && steps++ < 900) n = n.advance(0.1f)
        assertEquals(Stage.DRINKING, n.parties.first { it.id == party.id }.stage)
        assertTrue(n.player.hands.none { it is HandItem.Drinks })
    }

    @Test
    fun `tapping a tray that isn't there does nothing`() {
        val night = open()
        assertSame(night, night.tapTray(99))
    }

    @Test
    fun `you can't pick up a tray with both hands full`() {
        val (night, party) = withTrayWaiting()
        val full = night.copy(waiters = night.waiters.map { if (it.isPlayer) it.copy(hands = listOf(HandItem.DirtyDishes(1), HandItem.DirtyDishes(2))) else it })
        var n = full.tapTray(party.id)
        // Long enough to walk there, not so long the guests give up.
        repeat(80) { n = n.advance(0.1f) }
        assertFalse(party.id in n.player.drinks)
        assertEquals(listOf(party.id), n.traysOnBar.map { it.id })
    }
}
