package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.EmployeeId
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

class StaffTroubleNightTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val serverId = "t-server"

    private fun open(day: Int = 25): ServiceNight {
        val base = NewGameFactory.create(seed = 5L)
        val server = base.applicants.first().copy(id = EmployeeId(serverId), name = "Sam Server", role = Role.SERVER, status = EmployeeStatus.ACTIVE, experienceDays = 20, stress = 10, morale = 80)
        val state = base.copy(day = day, restaurant = base.restaurant.copy(tables = 6), employees = base.employees + server)
        return ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))
            .copy(troubles = emptyMap(), slips = emptyMap())
    }

    @Test
    fun `workers are known by name, so a hint can say who has gone`() {
        assertEquals("Sam Server", open().staffNames[serverId])
    }

    @Test
    fun `someone going home sick leaves at their time, dropping what they carry`() {
        var night = open()
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }
        night = night.copy(
            parties = night.parties.map { if (it.id == party.id) it.copy(stage = Stage.CARRIED, heldBy = serverId) else it },
            waiters = night.waiters.map { if (it.id == serverId) it.copy(hands = listOf(HandItem.Plate(party.id))) else it },
            troubles = mapOf(serverId to StaffTrouble(TroubleKind.SICK, at = night.time)),
        )

        val after = night.advance(0.1f)

        val server = after.waiters.first { it.id == serverId }
        assertTrue(server.away)
        assertTrue(server.hands.isEmpty())
        assertEquals(Stage.IN_KITCHEN, after.parties.first { it.id == party.id }.stage)
        assertTrue(serverId in after.troubled)
        assertEquals(serverId, after.lastTroubleWho)
    }

    @Test
    fun `an away worker does no work, and stays away`() {
        var night = open()
        night = night.copy(troubles = mapOf(serverId to StaffTrouble(TroubleKind.ARGUMENT, at = 0f)))
        repeat(3000) { night = night.advance(0.1f) }
        assertTrue(night.waiters.first { it.id == serverId }.away)
        assertTrue(night.waiters.first { it.id == serverId }.errand == null)
    }

    @Test
    fun `a long break ends and they go back to work`() {
        var night = open()
        night = night.copy(troubles = mapOf(serverId to StaffTrouble(TroubleKind.BREAK, at = 1f, backAt = 5f)))
        repeat(30) { night = night.advance(0.1f) }
        assertTrue(night.waiters.first { it.id == serverId }.away)
        repeat(30) { night = night.advance(0.1f) }
        assertFalse(night.waiters.first { it.id == serverId }.away)
    }

    @Test
    fun `the night reports who had a bad night, for the morning`() {
        var night = open()
        night = night.copy(troubles = mapOf(serverId to StaffTrouble(TroubleKind.SICK, at = 0f)))
        repeat(10) { night = night.advance(0.1f) }
        assertEquals(listOf(StaffTroubleOutcome(serverId, TroubleKind.SICK)), night.result().staffTrouble)
    }
}
