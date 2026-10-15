package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.HandItem
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WalkedOutTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(): ServiceNight {
        val state = NewGameFactory.create(seed = 5L)
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
    }

    /** A night where a party is sitting at a table, so there's someone to walk out. */
    private fun withSeatedParty(): Pair<ServiceNight, ServiceNight.Party> {
        var night = open()
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }
        return night to party
    }

    private fun walkOut(night: ServiceNight, party: ServiceNight.Party, hands: List<HandItem>): ServiceNight {
        val me = night.player
        val impatient = night.copy(
            parties = night.parties.map {
                if (it.id == party.id) it.copy(stage = Stage.CARRIED, stageSince = night.time - 100_000f, orderedAt = night.time - 100_000f, heldBy = me.id) else it
            },
            waiters = night.waiters.map { if (it.isPlayer) it.copy(hands = hands) else it },
        )
        return impatient.advance(0.1f)
    }

    @Test
    fun `food in your hands for a table that walks out becomes dirty dishes to wash`() {
        val (night, party) = withSeatedParty()
        val after = walkOut(night, party, listOf(HandItem.Plate(party.id)))

        assertEquals(listOf(HandItem.DirtyDishes(party.table!!)), after.player.hands)
        assertEquals(listOf(party.table), after.player.dirtyDishes)
    }

    @Test
    fun `food and drinks for the same table end up as one stack`() {
        val (night, party) = withSeatedParty()
        val after = walkOut(night, party, listOf(HandItem.Plate(party.id), HandItem.Drinks(party.id)))

        assertEquals(1, after.player.hands.size)
        assertNotNull(after.player.dirtyDishes.singleOrNull())
    }
}
