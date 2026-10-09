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

    @Test
    fun `a family leaves a mess on the floor near their table when they go`() {
        val night = (1L..20L).map { open(seed = it) }.first { n -> n.parties.any { it.family } }
        val family = night.parties.first { it.family }
        // Play until the family has eaten and is on its way out (serving everyone straight away).
        var n = night
        var guard = 0
        while (n.parties.first { it.id == family.id }.stage != ServiceNight.Stage.LEAVING_HAPPY && guard++ < 20_000) {
            val me = n.player
            if (!me.walking) {
                val target = me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { n.tapTable(it) } }
                    ?: if (me.tickets.isNotEmpty() || n.parties.any { it.stage == ServiceNight.Stage.READY_AT_PASS }) n.tapPass() else null
                    ?: n.parties.firstOrNull { it.id == family.id && it.stage == ServiceNight.Stage.READY_TO_ORDER }?.table?.let { n.tapTable(it) }
                n = target ?: n
            }
            n = n.advance(0.05f)
        }
        val left = n.parties.first { it.id == family.id }
        assertTrue("the family never finished", left.stage == ServiceNight.Stage.LEAVING_HAPPY)
        val stand = n.layout.stand(left.table!!)
        val mess = n.messesOnFloor.minByOrNull { it.at.distanceTo(stand) }
        assertTrue(mess != null && mess.appearsAt >= left.stageSince - 0.1f && mess.at.distanceTo(stand) < 30f)
    }
}
