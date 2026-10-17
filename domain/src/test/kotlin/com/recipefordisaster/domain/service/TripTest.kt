package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.HandItem
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))

    /** A night with a brand new server on the staff. */
    private fun open(): Pair<ServiceNight, String> {
        val base = NewGameFactory.create(seed = 5L)
        val server = base.applicants.first { it.role == Role.SERVER }.copy(status = EmployeeStatus.ACTIVE, experienceDays = 0)
        val state = base.copy(employees = base.employees + server)
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L)) to server.id.value
    }

    @Test
    fun `a new server is planned to trip tonight`() {
        val (night, id) = open()
        assertTrue(id in night.slips)
    }

    @Test
    fun `a new server carrying food trips, so the food is made again and there's a spill to mop`() {
        val (opened, id) = open()
        var night = opened
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }

        // Hand them a plate for that table, mid-walk, with their trip due.
        night = night.copy(
            slips = mapOf(id to StaffSlips.Slip(night.time)),
            parties = night.parties.map { if (it.id == party.id) it.copy(stage = Stage.CARRIED, heldBy = id) else it },
            waiters = night.waiters.map { if (it.id == id) it.copy(hands = listOf(HandItem.Plate(party.id))) else it },
        )
        night = night.copy(waiters = night.waiters.map { if (it.id == id) it.copy(errand = ServiceNight.Errand.Serve(party.table!!), route = listOf(ServiceFloor.pass, ServiceFloor.layout(night.tableCount).stand(party.table!!)), routeStart = night.time, routeEnd = night.time + 50f) else it })
        val spillsBefore = night.messesOnFloor.size

        val after = night.advance(0.1f)

        assertTrue(id in after.tripped)
        assertTrue(after.waiters.first { it.id == id }.hands.isEmpty())
        assertEquals(Stage.IN_KITCHEN, after.parties.first { it.id == party.id }.stage)
        // The spill is there once the plate has landed, not before.
        assertEquals(spillsBefore, after.messesOnFloor.size)
        var later = after
        repeat(10) { later = later.advance(0.1f) }
        assertEquals(spillsBefore + 1, later.messesOnFloor.size)
        assertTrue(after.time - after.lastTripAt < 1f)

        // The scene gets what it needs to play it out: who, what they dropped, and where it landed.
        val scene = after.trips.single()
        assertEquals(id, scene.waiterId)
        assertEquals(ServiceNight.TripItem.PLATE, scene.item)
        assertEquals(party.id, scene.partyId)
        assertTrue(scene.landAt in ServiceFloor.spillSpots)
        assertEquals(after.lastTripAt, scene.since, 0.001f)
    }

    @Test
    fun `nobody trips with empty hands`() {
        val (opened, id) = open()
        val due = opened.copy(slips = mapOf(id to StaffSlips.Slip(0f)))
        // Nothing in their hands, so there's nothing to drop.
        assertFalse(id in due.advance(0.1f).tripped)
    }
}
