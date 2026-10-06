package com.recipefordisaster.domain.decision

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionApplierTest {

    private val start: GameState = NewGameFactory.create(seed = 42L)

    @Test
    fun `no decisions changes nothing and costs nothing`() {
        val applied = DecisionApplier.apply(start, PlayerDecisions())

        assertEquals(start, applied.state)
        assertEquals(0L, applied.spending.total)
    }

    @Test
    fun `buying an ingredient adds stock and charges its price`() {
        val flour = RecipeBook.FLOUR
        val before = start.inventory.ingredients.getValue(flour)

        val applied = DecisionApplier.apply(start, PlayerDecisions(purchases = mapOf(flour to 5.0)))

        assertEquals(before.quantityOnHand + 5.0, applied.state.inventory.ingredients.getValue(flour).quantityOnHand, 0.0001)
        assertEquals(before.purchasePricePerUnit * 5, applied.spending.ingredients)
    }

    @Test
    fun `purchases are capped by what the cash on hand can pay for`() {
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 10))
        val meat = RecipeBook.MYSTERY_MEAT // 6 per kg

        val applied = DecisionApplier.apply(broke, PlayerDecisions(purchases = mapOf(meat to 50.0)))

        assertTrue(applied.spending.total <= 10)
        assertTrue(applied.log.any { "Only managed" in it.message })
    }

    @Test
    fun `hiring moves an applicant onto the payroll and charges the hiring fee`() {
        val applicant = start.applicants.first()

        val applied = DecisionApplier.apply(start, PlayerDecisions(hires = setOf(applicant.id)))

        assertTrue(applied.state.employees.any { it.id == applicant.id })
        assertFalse(applied.state.applicants.any { it.id == applicant.id })
        assertEquals(StaffingMarket.hiringFee(applicant), applied.spending.staffing)
    }

    @Test
    fun `firing removes the employee, pays severance, and dents everyone else's morale`() {
        // Two people on the payroll, so there's a colleague to see it happen.
        val team = start.copy(employees = start.employees + start.applicants.first().copy(status = com.recipefordisaster.domain.employee.EmployeeStatus.ACTIVE))
        val fired = team.employees.first()
        val colleague = team.employees.last()

        val applied = DecisionApplier.apply(team, PlayerDecisions(fires = setOf(fired.id)))

        assertFalse(applied.state.employees.any { it.id == fired.id })
        assertEquals(StaffingMarket.severance(fired), applied.spending.staffing)
        assertTrue(applied.state.employees.single { it.id == colleague.id }.morale < colleague.morale)
    }

    @Test
    fun `a day off takes the employee off the floor`() {
        val resting = start.employees.first()

        val applied = DecisionApplier.apply(start, PlayerDecisions(restDays = setOf(resting.id)))

        assertEquals(EmployeeStatus.ON_BREAK, applied.state.employees.single { it.id == resting.id }.status)
    }

    @Test
    fun `price changes are clamped to the fair-play range`() {
        val dish = start.menu.first()

        val applied = DecisionApplier.apply(start, PlayerDecisions(priceChanges = mapOf(dish.id to 10_000L)))

        assertEquals(PriceRules.maxPrice(dish), applied.state.menu.single { it.id == dish.id }.sellingPrice)
    }

    @Test
    fun `adding a recipe-book dish puts it on the menu for the reprint cost`() {
        val newDish = RecipeBook.dishes.first()

        val applied = DecisionApplier.apply(start, PlayerDecisions(dishesToAdd = setOf(newDish.id)))

        assertTrue(applied.state.menu.any { it.id == newDish.id })
        assertEquals(RecipeBook.ADD_DISH_COST, applied.spending.menu)
    }

    @Test
    fun `unknown dishes and applicants are ignored rather than crashing`() {
        val applied = DecisionApplier.apply(
            start,
            PlayerDecisions(dishesToAdd = setOf(DishId("not_a_real_dish")), hires = setOf(start.employees.first().id)),
        )

        assertEquals(start.menu, applied.state.menu)
        assertEquals(0L, applied.spending.total)
    }

    @Test
    fun `repairing restores condition and charges for the wear`() {
        val worn = start.copy(equipment = start.equipment.map { it.copy(condition = 30) })
        val oven = worn.equipment.first()

        val applied = DecisionApplier.apply(worn, PlayerDecisions(repairs = setOf(oven.id)))

        assertEquals(100, applied.state.equipment.first().condition)
        assertEquals(EquipmentOperations.repairCost(oven), applied.spending.repairs)
    }

    @Test
    fun `nothing is bought once the money runs out, and the player is told`() {
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 0))

        val applied = DecisionApplier.apply(broke, PlayerDecisions(deepClean = true, hires = setOf(start.applicants.first().id)))

        assertEquals(0L, applied.spending.total)
        assertEquals(start.restaurant.cleanliness, applied.state.restaurant.cleanliness)
        assertEquals(2, applied.log.count { "Couldn't afford" in it.message })
    }
}
