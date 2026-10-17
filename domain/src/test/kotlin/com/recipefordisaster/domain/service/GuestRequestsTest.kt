package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.HandItem
import com.recipefordisaster.domain.service.ServiceNight.RequestKind
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestRequestsTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    private fun open(day: Int): ServiceNight {
        val base = NewGameFactory.create(seed = 5L)
        val state: GameState = base.copy(day = day, restaurant = base.restaurant.copy(tables = 6))
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
    }

    /** A night where the player is walking a plate (which sat on the pass for [onPassFor] seconds) to a seated party. */
    private fun carryingPlate(day: Int, onPassFor: Float): Pair<ServiceNight, ServiceNight.Party> {
        var night = open(day)
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }
        night = night.copy(
            parties = night.parties.map {
                if (it.id == party.id) it.copy(stage = Stage.CARRIED, heldBy = ServiceNight.PLAYER_ID, readyAt = night.time - onPassFor, orders = it.preferences.map { p -> p.first() }) else it
            },
            waiters = night.waiters.map { if (it.isPlayer) it.copy(hands = listOf(HandItem.Plate(party.id))) else it },
        )
        return night.tapTable(party.table!!) to party
    }

    private fun walkUntil(night: ServiceNight, partyId: Int, condition: (ServiceNight.Party) -> Boolean): ServiceNight {
        var n = night
        var steps = 0
        while (!condition(n.parties.first { it.id == partyId }) && steps++ < 1500) n = n.advance(0.05f)
        return n
    }

    @Test
    fun `food that sat on the pass too long arrives cold, once guests can ask for things`() {
        val (night, party) = carryingPlate(day = 20, onPassFor = 30f)
        val n = walkUntil(night, party.id) { it.stage == Stage.EATING }

        val request = n.parties.first { it.id == party.id }.request
        assertNotNull(request)
        assertEquals(RequestKind.COLD, request!!.kind)
    }

    @Test
    fun `fresh food doesn't arrive cold`() {
        val (night, party) = carryingPlate(day = 20, onPassFor = 2f)
        val n = walkUntil(night, party.id) { it.stage == Stage.EATING }
        assertTrue(n.parties.first { it.id == party.id }.request?.kind != RequestKind.COLD)
    }

    @Test
    fun `nobody asks for anything before the day guests start to`() {
        val (night, party) = carryingPlate(day = 5, onPassFor = 30f)
        val n = walkUntil(night, party.id) { it.stage == Stage.EATING }
        assertNull(n.parties.first { it.id == party.id }.request)
    }

    @Test
    fun `going to their table sorts the request out, and it's remembered`() {
        val (night, party) = carryingPlate(day = 20, onPassFor = 30f)
        var n = walkUntil(night, party.id) { it.stage == Stage.EATING }
        n = walkUntil(n, party.id) { true }
        n = n.tapTable(party.table!!)
        n = walkUntil(n, party.id) { it.request == null }

        val after = n.parties.first { it.id == party.id }
        assertNull(after.request)
        assertTrue(after.requestFixed)
        assertEquals(1, n.requestsFixed)
    }

    @Test
    fun `an ignored request costs the tip and their good opinion`() {
        val (night, party) = carryingPlate(day = 20, onPassFor = 30f)
        var ignored = walkUntil(night, party.id) { it.stage == Stage.EATING }
        ignored = walkUntil(ignored, party.id) { it.stage == Stage.LEAVING_HAPPY }
        val seen = ignored.parties.first { it.id == party.id }
        assertEquals(0L, seen.tip)
        assertEquals(1, ignored.requestsMissed)

        // The same table, with the request seen to, is happier and tips.
        var fixed = walkUntil(night, party.id) { it.stage == Stage.EATING }
        fixed = fixed.tapTable(party.table!!)
        fixed = walkUntil(fixed, party.id) { it.stage == Stage.LEAVING_HAPPY }
        fun happiness(n: ServiceNight) = n.result().outcomes.filter { it.dish != null }.map { it.satisfaction }.average()
        assertTrue(happiness(fixed) > happiness(ignored))
    }

    @Test
    fun `a worker due a mix-up takes their table the wrong dish, once`() {
        val (night, party) = carryingPlate(day = 20, onPassFor = 2f)
        val slipping = night.copy(slips = mapOf(ServiceNight.PLAYER_ID to StaffSlips.Slip(at = 0f, mixUp = true)))
        val n = walkUntil(slipping, party.id) { it.stage == Stage.EATING }

        assertEquals(RequestKind.WRONG_DISH, n.parties.first { it.id == party.id }.request?.kind)
        assertTrue(ServiceNight.PLAYER_ID in n.tripped)
    }
}
