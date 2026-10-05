package com.recipefordisaster.domain.equipment

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.service.ServiceNight
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentCatalogTest {

    private val start: GameState = NewGameFactory.create(seed = 42L)
    private val oven = start.equipment.first()

    @Test
    fun `the ancient oven upgrades step by step to the best model, then no further`() {
        val sturdy = EquipmentCatalog.upgrade(oven)
        assertEquals("Sturdy Oven", sturdy.name)
        assertEquals(oven.id, sturdy.id)
        assertEquals(100, sturdy.condition)
        val best = EquipmentCatalog.upgrade(sturdy)
        assertEquals("Convection Oven", best.name)
        assertNull(EquipmentCatalog.nextModel(best))
        assertEquals(best, EquipmentCatalog.upgrade(best))
    }

    @Test
    fun `a better oven breaks less and wears more slowly`() {
        val sturdy = EquipmentCatalog.upgrade(oven).copy(condition = 60)
        val old = oven.copy(condition = 60)
        assertTrue(EquipmentOperations.failureProbability(sturdy) < EquipmentOperations.failureProbability(old))
        assertTrue(EquipmentOperations.applyDailyWear(sturdy, 1.0).condition > EquipmentOperations.applyDailyWear(old, 1.0).condition)
    }

    @Test
    fun `upgrading in the morning charges the new oven's price`() {
        val applied = DecisionApplier.apply(start, PlayerDecisions(upgrades = setOf(oven.id), repairs = setOf(oven.id)))
        assertEquals("Sturdy Oven", applied.state.equipment.first().name)
        assertEquals(EquipmentCatalog.upgradeCost(oven), applied.spending.upgrades)
        assertEquals(0L, applied.spending.repairs) // no point repairing a brand-new oven
    }

    @Test
    fun `an upgrade you can't afford doesn't happen`() {
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 100))
        val applied = DecisionApplier.apply(broke, PlayerDecisions(upgrades = setOf(oven.id)))
        assertEquals(oven, applied.state.equipment.first())
        assertEquals(0L, applied.spending.total)
    }

    @Test
    fun `a better oven cooks faster during service`() {
        val engine = DefaultDayTickEngine(EventEngine(emptyList()))
        fun night(state: GameState) = ServiceNight.open(engine.openService(state, PlayerDecisions(), SeededRandomSource(1)), SeededRandomSource(2))
        val upgraded = start.copy(equipment = start.equipment.map { EquipmentCatalog.upgrade(it) })
        assertTrue(night(upgraded).secondsPerDish < night(start).secondsPerDish)
    }
}
