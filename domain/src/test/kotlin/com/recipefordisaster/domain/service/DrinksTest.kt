package com.recipefordisaster.domain.service

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

class DrinksTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(restaurant = it.restaurant.copy(tables = 6)) }

    private fun open(state: GameState = start, seed: Long = 1L): ServiceNight =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    private fun waitFor(night: ServiceNight, condition: (ServiceNight) -> Boolean): ServiceNight {
        var n = night
        var guard = 0
        while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f)
        return n
    }

    private fun walk(night: ServiceNight) = waitFor(night) { !it.player.walking }

    @Test
    fun `once seated, guests want a drink before they order any food`() {
        val n = waitFor(open()) { night -> night.parties.any { it.stage == Stage.WANTS_DRINKS } }
        assertTrue(n.parties.none { it.stage == Stage.READY_TO_ORDER })
        // Left alone, they never get as far as ordering food.
        val later = waitFor(n) { it.time > n.time + 20f }
        assertTrue(later.parties.none { it.stage == Stage.READY_TO_ORDER })
    }

    @Test
    fun `take the drinks order, pour it at the bar, carry it over, and they order food after a sip`() {
        var n = waitFor(open()) { night -> night.parties.any { it.stage == Stage.WANTS_DRINKS } }
        val party = n.parties.first { it.stage == Stage.WANTS_DRINKS }
        val table = party.table!!

        n = walk(n.tapTable(table))
        assertEquals(Stage.DRINKS_ORDERED, n.parties.first { it.id == party.id }.stage)
        assertEquals(listOf(party.id), n.player.drinkOrders)

        // Pouring takes a moment at the bar; then the drinks are in your hand.
        n = n.tapBar()
        n = waitFor(n) { it.player.errand is ServiceNight.Errand.Pour }
        assertTrue(n.player.drinks.isEmpty())
        n = walk(n)
        assertEquals(listOf(party.id), n.player.drinks)
        assertTrue(n.player.drinkOrders.isEmpty())

        n = walk(n.tapTable(table))
        assertEquals(Stage.DRINKING, n.parties.first { it.id == party.id }.stage)
        assertTrue(n.player.drinks.isEmpty())

        // They sip and read the menu for a few seconds before they're ready to order food.
        val servedAt = n.time
        n = waitFor(n) { night -> night.parties.first { it.id == party.id }.stage != Stage.DRINKING }
        assertEquals(Stage.READY_TO_ORDER, n.parties.first { it.id == party.id }.stage)
        assertTrue(n.time - servedAt >= 3.9f)
    }

    @Test
    fun `tapping the bar with no drinks to pour does nothing there`() {
        var n = walk(open().tapBar())
        assertTrue(n.player.drinks.isEmpty())
        n = n.advance(1f)
        assertTrue(n.player.errand == null)
    }

    @Test
    fun `two hands carry two tables' drinks at once`() {
        var n = waitFor(open()) { night -> night.parties.count { it.stage == Stage.WANTS_DRINKS } >= 2 }
        val tables = n.parties.filter { it.stage == Stage.WANTS_DRINKS }.take(2).map { it.table!! }
        n = n.tapTable(tables[0]).tapTable(tables[1]).tapBar()
        n = waitFor(n) { it.player.drinks.size == 2 || it.time > 200f }
        assertEquals(2, n.player.drinks.size)
    }

    @Test
    fun `guests who never get their drinks give up and go`() {
        val n = waitFor(open()) { night -> night.gaveUpAt.values.any { it == ServiceNight.WaitedFor.DRINKS } }
        assertTrue(n.gaveUpAt.values.any { it == ServiceNight.WaitedFor.DRINKS })
    }

    @Test
    fun `time spent sipping a drink doesn't make the food seem slow`() {
        // Serve drinks and then food as quickly as possible: guests should be happy and tip.
        var n = open()
        var guard = 0
        while (!n.finished && guard++ < 20_000) {
            n = DrinksPlayer.play(n).advance(0.05f)
        }
        val fed = n.result().outcomes.filter { it.dish != null }
        assertTrue(fed.isNotEmpty())
        assertTrue(fed.map { it.satisfaction }.average() >= 55)
        assertTrue(n.tips > 0)
    }
}

/** A quick player who looks after the drinks as well as the food. */
internal object DrinksPlayer {
    fun play(n: ServiceNight): ServiceNight {
        val me = n.player
        if (me.walking) return n
        me.plates.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        me.drinks.firstOrNull()?.let { id -> n.parties.first { it.id == id }.table?.let { return n.tapTable(it) } }
        if (me.drinkOrders.isNotEmpty() && me.freeHands > 0) return n.tapBar()
        if (me.tickets.isNotEmpty()) return n.tapPass()
        if (n.parties.any { it.stage == Stage.READY_AT_PASS }) return n.tapPass()
        n.parties.filter { it.stage == Stage.WANTS_DRINKS || it.stage == Stage.READY_TO_ORDER }.minByOrNull { it.stageSince }?.table?.let { return n.tapTable(it) }
        if (me.dirtyDishes.isNotEmpty() && (me.freeHands == 0 || n.dirtyTables.isEmpty())) return n.tapDishStation()
        if (me.freeHands > 0) n.dirtyTables.firstOrNull()?.let { return n.tapTable(it) }
        return n
    }
}
