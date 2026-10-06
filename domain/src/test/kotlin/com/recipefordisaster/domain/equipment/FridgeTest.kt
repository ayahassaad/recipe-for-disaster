package com.recipefordisaster.domain.equipment

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.event.KitchenEvents
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.service.ServiceNight.Stage
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FridgeTest {

    private val engine = DefaultDayTickEngine(EventEngine(emptyList()))
    private val start: GameState = NewGameFactory.create(seed = 11L).let { it.copy(day = 6, restaurant = it.restaurant.copy(tables = 6)) }

    private fun withFridge(state: GameState, condition: Int) =
        state.copy(equipment = state.equipment.map { if (Fridge.isFridge(it)) it.copy(condition = condition) else it })

    private fun open(state: GameState, seed: Long = 1L) =
        ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(seed)), SeededRandomSource(seed + 1))

    private fun waitFor(night: ServiceNight, condition: (ServiceNight) -> Boolean): ServiceNight {
        var n = night
        var guard = 0
        while (!condition(n) && guard++ < 20_000) n = n.advance(0.05f)
        return n
    }

    /** A worn fridge, on a night it does give up. */
    private fun breakingNight(): ServiceNight =
        (1L..40L).map { open(withFridge(start, 10), it) }.first { it.fridgeBreaksAt != null }

    @Test
    fun `a new restaurant has a fridge, and old saves get one`() {
        assertNotNull(Fridge.of(NewGameFactory.create(seed = 3L)))
        val old = start.copy(equipment = start.equipment.filterNot { Fridge.isFridge(it) })
        assertNotNull(Fridge.of(Fridge.ensure(old)))
    }

    @Test
    fun `a fridge in good condition never breaks during service`() {
        (1L..30L).forEach { assertNull(open(withFridge(start, 90), it).fridgeBreaksAt) }
    }

    @Test
    fun `a worn fridge sometimes breaks during service, with a warning first`() {
        val night = breakingNight()
        val breaksAt = night.fridgeBreaksAt!!
        val warned = waitFor(night) { it.time >= breaksAt - ServiceNight.FRIDGE_WARNING + 0.1f }
        assertTrue(warned.fridgeStruggling)
        assertFalse(warned.fridgeBroken)
        val broken = waitFor(warned) { it.fridgeBroken }
        assertTrue(broken.fridgeBrokeTonight)
    }

    @Test
    fun `while the fridge is broken nobody can order cold food, and the cold food spoils`() {
        var n = waitFor(breakingNight()) { it.fridgeBroken }
        val coldBefore = n.inventory.ingredients.values.filter { Fridge.isCold(it) }.sumOf { it.quantityOnHand }
        // Every dish on the starting menu needs something cold, so a waiting party can't order.
        n = waitFor(n) { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
        val party = n.parties.first { it.stage == Stage.READY_TO_ORDER }
        n = waitFor(n.tapTable(party.table!!)) { !it.player.walking }
        assertEquals(Stage.READY_TO_ORDER, n.parties.first { it.id == party.id }.stage)
        n = waitFor(n) { it.time > n.time + 12f }
        assertTrue(n.inventory.ingredients.values.filter { Fridge.isCold(it) }.sumOf { it.quantityOnHand } < coldBefore)
    }

    @Test
    fun `tapping the fridge fixes it, and orders can be taken again`() {
        var n = waitFor(breakingNight()) { it.fridgeBroken }
        n = n.tapFridge()
        n = waitFor(n) { !it.player.walking }
        assertFalse(n.fridgeBroken)
        n = waitFor(n) { night -> night.parties.any { it.stage == Stage.READY_TO_ORDER } }
        val party = n.parties.first { it.stage == Stage.READY_TO_ORDER }
        n = waitFor(n.tapTable(party.table!!)) { !it.player.walking }
        assertEquals(listOf(party.id), n.player.tickets)
    }

    @Test
    fun `tapping a working fridge does nothing`() {
        val n = open(start)
        assertEquals(n, n.tapFridge())
    }

    @Test
    fun `a hired dishwasher fixes a broken fridge`() {
        val washer = start.applicants.first().copy(role = com.recipefordisaster.domain.employee.Role.DISHWASHER)
        val state = withFridge(start.copy(employees = start.employees + washer), 10)
        val night = (1L..40L).map { open(state, it) }.first { it.fridgeBreaksAt != null }
        var n = waitFor(night) { it.fridgeBroken }
        n = waitFor(n) { !it.fridgeBroken }
        assertFalse(n.fridgeBroken)
    }

    @Test
    fun `a fridge left broken at closing is broken the next morning`() {
        val state = withFridge(start, 10)
        val seed = (1L..40L).first { open(state, it).fridgeBreaksAt != null }
        val setup = engine.openService(state, PlayerDecisions(), SeededRandomSource(seed))
        var n = ServiceNight.open(setup, SeededRandomSource(seed + 1))
        while (!n.finished) n = n.advance(0.05f)
        val next = engine.closeService(setup, n.result(), SeededRandomSource(seed + 2)).newState
        assertTrue(EquipmentOperations.isBroken(Fridge.of(next)!!))
    }

    @Test
    fun `a broken fridge doesn't slow the cooking down`() {
        val fine = open(start)
        val brokenFridge = open(withFridge(start, 0))
        assertTrue(brokenFridge.fridgeBroken)
        assertEquals(fine.secondsPerDish, brokenFridge.secondsPerDish, 0.0001f)
    }

    @Test
    fun `the overnight fridge failure only happens to a worn fridge, and breaks it`() {
        val rule = KitchenEvents.fridgeFailure
        assertFalse(rule.prerequisite(withFridge(start, 90)))
        assertTrue(rule.prerequisite(withFridge(start, 30)))
        val after = rule.resolve(withFridge(start, 30), SeededRandomSource(1L)).resultingState
        assertTrue(EquipmentOperations.isBroken(Fridge.of(after)!!))
    }

    @Test
    fun `the fridge can be repaired and upgraded in the morning`() {
        val worn = withFridge(start, 30)
        val fridge = Fridge.of(worn)!!
        val repaired = DecisionApplier.apply(worn, PlayerDecisions(repairs = setOf(fridge.id))).state
        assertEquals(100, Fridge.of(repaired)!!.condition)
        val upgraded = DecisionApplier.apply(worn, PlayerDecisions(upgrades = setOf(fridge.id))).state
        assertEquals("Sturdy Fridge", Fridge.of(upgraded)!!.name)
        assertEquals(0, Fridge.of(upgraded)!!.capacityEffect)
    }
}
