package com.recipefordisaster.domain.service

import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.progression.Feature
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import com.recipefordisaster.domain.simulation.ServiceSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeawayTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 31L).let { it.copy(day = 14, restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    /** A night where only the takeaway guest turns up. */
    private fun justTheTakeaway(): Pair<ServiceNight, Int> {
        val night = (1L..40L).map { open(seed = it) }.first { n -> n.parties.any { it.takeaway } }
        val guest = night.parties.first { it.takeaway }
        return night.copy(parties = night.parties.map { if (it.id == guest.id) it.copy(arriveAt = 0.5f) else it.copy(arriveAt = 10_000f) }) to guest.id
    }

    private fun ServiceNight.run(until: (ServiceNight) -> Boolean): ServiceNight {
        var n = this
        var steps = 0
        while (!until(n) && steps++ < 6000) n = n.advance(0.05f)
        assertTrue("never got there (time ${n.time})", until(n))
        return n
    }

    @Test
    fun `from day nine some lone guests want a takeaway, and before that none do`() {
        val before = (1L..30L).count { open(start.copy(day = 8), it).parties.any { p -> p.takeaway } }
        assertEquals(0, before)
        val after = (1L..30L).count { open(start.copy(day = 14), it).parties.any { p -> p.takeaway } }
        assertTrue("$after nights", after in 10..30)
        (1L..30L).flatMap { open(start.copy(day = 14), it).parties }.filter { it.takeaway }.forEach {
            assertEquals(1, it.guests.size)
            assertFalse(it.wantsDrinks)
            assertEquals(null, it.special)
        }
    }

    @Test
    fun `on its first day a takeaway is sure to turn up`() {
        val first = start.copy(day = Feature.TAKEAWAY.day)
        (1L..30L).forEach { seed ->
            val night = open(first, seed)
            if (night.parties.count { it.guests.size == 1 && it.special == null } >= 2) assertTrue("seed $seed", night.parties.any { it.takeaway })
        }
    }

    @Test
    fun `you take the order at the door, bring the food back and they pay and leave`() {
        val (night, id) = justTheTakeaway()
        val party = { n: ServiceNight -> n.parties.first { it.id == id } }
        var n = night.run { party(it).stage == Stage.READY_TO_ORDER }
        assertEquals(null, party(n).table)
        // Take the order.
        n = n.tapTakeaway(id).run { party(it).stage == Stage.ORDER_TAKEN }
        assertEquals(1, n.player.tickets.size)
        // Hand it to the chef, and wait for it to be cooked.
        n = n.tapChef().run { party(it).stage == Stage.READY_AT_PASS }
        // Fetch it and take it to the door.
        n = n.tapPlate(id).run { id in it.player.plates }
        n = n.tapTakeaway(id).run { party(it).stage == Stage.LEAVING_HAPPY }
        val dish = n.results.getValue(party(n).guests.first())
        assertNotNull(dish.dish)
        assertTrue(dish.satisfaction > 0)
        // No table was used, so nothing to clear.
        assertTrue(n.dirtyTables.isEmpty())
        assertTrue(n.takings >= dish.dish!!.sellingPrice)
    }

    @Test
    fun `they never order a drink`() {
        val (night, id) = justTheTakeaway()
        var n = night
        var steps = 0
        while (steps++ < 2000 && n.parties.first { it.id == id }.stage != Stage.LEAVING_ANGRY) {
            n = n.advance(0.05f)
            assertFalse(n.parties.first { it.id == id }.stage in listOf(Stage.WANTS_DRINKS, Stage.DRINKS_ORDERED, Stage.DRINKING))
        }
        assertEquals(0L, n.drinkTakings)
    }

    @Test
    fun `a takeaway guest nobody serves gives up and goes`() {
        val (night, id) = justTheTakeaway()
        val n = night.run { it.parties.first { p -> p.id == id }.stage == Stage.LEAVING_ANGRY }
        assertTrue(n.results.values.any { it.missedReason == ServiceSimulator.MissedMealReason.TIRED_OF_WAITING })
        assertEquals(1, n.gaveUpCounts()[ServiceNight.WaitedFor.ORDER])
        assertTrue(n.dirtyTables.isEmpty())
    }

    @Test
    fun `hired servers leave the takeaway food for you to hand over`() {
        val server = start.employees.first().copy(role = com.recipefordisaster.domain.employee.Role.SERVER)
        val (night, id) = justTheTakeaway()
        val withServer = night.copy(
            waiters = night.waiters + ServiceNight.Waiter(
                server.id.value, isPlayer = false, tables = night.waiters.first().tables, speed = 20f, restSpot = ServiceFloor.pass,
                route = listOf(ServiceFloor.pass), routeStart = 0f, routeEnd = 0f, errand = null,
            ),
        )
        var n = withServer.run { it.parties.first { p -> p.id == id }.stage == Stage.READY_TO_ORDER }
        n = n.tapTakeaway(id).run { it.parties.first { p -> p.id == id }.stage == Stage.ORDER_TAKEN }
        n = n.tapChef().run { it.parties.first { p -> p.id == id }.stage == Stage.READY_AT_PASS }
        // Leave the plate on the pass for a good while: the server still doesn't touch it.
        repeat(400) { n = n.advance(0.05f) }
        val party = n.parties.first { it.id == id }
        assertTrue(party.stage == Stage.READY_AT_PASS || party.stage == Stage.LEAVING_ANGRY)
        assertTrue(n.waiters.filter { !it.isPlayer }.all { id !in it.plates })
    }
}
