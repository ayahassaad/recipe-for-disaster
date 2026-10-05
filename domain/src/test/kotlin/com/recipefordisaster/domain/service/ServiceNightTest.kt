package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.GameStateValidator
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceNightTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L)

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    /** Lets the clock run in small steps, calling [player] each step so a test can play. */
    private fun play(night: ServiceNight, player: (ServiceNight) -> ServiceNight = { it }): ServiceNight {
        var n = night
        var steps = 0
        while (!n.finished && steps < 20_000) {
            n = player(n).advance(0.05f)
            steps++
        }
        return n
    }

    /** A decent player: serves plates first, hands in tickets, then takes the longest-waiting order. */
    private fun busyPlayer(n: ServiceNight): ServiceNight {
        val me = n.player
        if (me.walking) return n
        me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        if (me.tickets.isNotEmpty()) return n.tapPass()
        if (n.parties.any { it.stage == Stage.READY_AT_PASS && it.table in me.tables }) return n.tapPass()
        n.parties.filter { it.stage == Stage.READY_TO_ORDER && it.table in me.tables }.minByOrNull { it.stageSince }?.table?.let { return n.tapTable(it) }
        return n
    }

    @Test
    fun `every guest gets exactly one result by the end of the night`() {
        val night = play(open(), ::busyPlayer)
        val result = night.result()
        assertEquals(night.arrivalOrder.size, result.outcomes.size)
        assertTrue(night.finished)
    }

    @Test
    fun `a player who does nothing feeds nobody at their own tables`() {
        val idle = play(open())
        val busy = play(open(), ::busyPlayer)
        assertTrue(busy.result().outcomes.count { it.dish != null } > idle.result().outcomes.count { it.dish != null })
    }

    @Test
    fun `ignored guests give up waiting rather than staying forever`() {
        val idle = play(open())
        assertTrue(idle.result().outcomes.any { it.missedReason == MissedMealReason.TIRED_OF_WAITING })
    }

    @Test
    fun `the player can only carry two plates`() {
        var n = open()
        var maxCarried = 0
        n = play(n) { current ->
            maxCarried = maxOf(maxCarried, current.player.plates.size)
            busyPlayer(current)
        }
        assertTrue(maxCarried <= 2)
    }

    @Test
    fun `taking an order needs the player to actually walk to the table`() {
        var n = open()
        while (n.parties.none { it.stage == Stage.READY_TO_ORDER && it.table in n.player.tables }) n = n.advance(0.05f)
        val table = n.parties.first { it.stage == Stage.READY_TO_ORDER && it.table in n.player.tables }.table!!
        n = n.tapTable(table)
        assertTrue(n.player.walking)
        assertTrue(n.player.tickets.isEmpty())
        while (n.player.walking) n = n.advance(0.05f)
        assertEquals(1, n.player.tickets.size)
    }

    @Test
    fun `the player looks after every table`() {
        assertEquals((0 until ServiceFloor.TABLE_COUNT).toSet(), open().player.tables)
    }

    @Test
    fun `hired servers run plates out when the player leaves them on the pass`() {
        val withRunner = start.copy(employees = start.employees + start.applicants.filter { it.role == com.recipefordisaster.domain.employee.Role.SERVER })
        // A player who takes orders and hands them in, but never collects food.
        val orderTaker: (ServiceNight) -> ServiceNight = { n ->
            val me = n.player
            when {
                me.walking -> n
                me.tickets.isNotEmpty() -> n.tapPass()
                else -> n.parties.filter { it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { n.tapTable(it) } ?: n
            }
        }
        val alone = play(open(start.copy(employees = start.employees.filter { it.role != com.recipefordisaster.domain.employee.Role.SERVER })), orderTaker)
        val helped = play(open(withRunner), orderTaker)
        assertEquals(0, alone.result().outcomes.count { it.dish != null })
        assertTrue(helped.result().outcomes.count { it.dish != null } > 0)
    }

    @Test
    fun `a played night closes the day into a valid state with books that match`() {
        val setup = engine.openService(start, PlayerDecisions(), SeededRandomSource(3))
        val night = play(ServiceNight.open(setup, SeededRandomSource(4)), ::busyPlayer)
        val day = engine.closeService(setup, night.result(), SeededRandomSource(5))

        assertEquals(emptyList<String>(), GameStateValidator.validate(day.newState))
        assertEquals(night.takings, day.newState.ledger.history.last().revenue)
        assertEquals(start.day + 1, day.newState.day)
    }

    @Test
    fun `a quick player makes more money than a slow one`() {
        val quick = play(open(seed = 7), ::busyPlayer)
        var slowSteps = 0
        val slow = play(open(seed = 7)) { n -> if (slowSteps++ % 120 == 0) busyPlayer(n) else n }
        assertTrue("quick ${quick.takings} vs slow ${slow.takings}", quick.takings >= slow.takings)
    }
}
