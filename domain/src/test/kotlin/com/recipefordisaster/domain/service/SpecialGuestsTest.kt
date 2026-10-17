package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialGuestsTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val base: GameState = NewGameFactory.create(seed = 5L).let { it.copy(day = 12, restaurant = it.restaurant.copy(tables = 6)) }

    private fun staffed(): GameState {
        fun hire(role: Role) = base.applicants.first().copy(id = com.recipefordisaster.domain.employee.EmployeeId("t-$role"), role = role, status = EmployeeStatus.ACTIVE, experienceDays = 20)
        return base.copy(employees = base.employees + listOf(hire(Role.SERVER), hire(Role.BARTENDER)))
    }

    private fun open(state: GameState): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1L)), SeededRandomSource(2L))

    /** A night where the first party seated is special, with its food ready on the pass. */
    private fun withSpecialPlateOnPass(): Pair<ServiceNight, ServiceNight.Party> {
        var night = open(staffed())
        var steps = 0
        while (night.parties.none { it.table != null && it.stage >= Stage.DECIDING } && steps++ < 2000) night = night.advance(0.1f)
        val party = night.parties.first { it.table != null && it.stage >= Stage.DECIDING }
        night = night.copy(parties = night.parties.map {
            if (it.id == party.id) it.copy(special = SpecialGuest.CRITIC, stage = Stage.READY_AT_PASS, stageSince = night.time - 30f, orders = it.preferences.map { p -> p.first() }) else it
        })
        return night to night.parties.first { it.id == party.id }
    }

    @Test
    fun `a server leaves a special guest's food on the pass for you`() {
        val (night, party) = withSpecialPlateOnPass()
        var n = night
        repeat(600) { n = n.advance(0.1f) }
        // Nobody but you may carry it: it's still waiting (or the critic has given up on it).
        val after = n.parties.first { it.id == party.id }
        assertTrue(after.stage == Stage.READY_AT_PASS || after.stage == Stage.LEAVING_ANGRY || after.stage == Stage.DONE)
        assertTrue(n.waiters.filter { !it.isPlayer }.all { w -> party.id !in w.plates })
    }

    @Test
    fun `and you can still serve them yourself`() {
        val (night, party) = withSpecialPlateOnPass()
        var n = night.tapPlate(party.id)
        var steps = 0
        while (party.id !in n.player.plates && steps++ < 300) n = n.advance(0.1f)
        assertTrue(party.id in n.player.plates)
    }

    @Test
    fun `an ordinary party's food is carried by the server`() {
        val (night, party) = withSpecialPlateOnPass()
        val ordinary = night.copy(parties = night.parties.map { if (it.id == party.id) it.copy(special = null) else it })
        var n = ordinary
        var steps = 0
        while (n.parties.first { it.id == party.id }.stage == Stage.READY_AT_PASS && steps++ < 600) n = n.advance(0.1f)
        assertEquals(true, n.parties.first { it.id == party.id }.stage != Stage.READY_AT_PASS)
    }
}
