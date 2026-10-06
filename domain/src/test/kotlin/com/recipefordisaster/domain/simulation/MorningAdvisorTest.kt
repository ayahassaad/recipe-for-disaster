package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.menu.RecipeBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningAdvisorTest {

    private val start = NewGameFactory.create(seed = 3L)

    @Test
    fun `daily costs add up wages and premises`() {
        val costs = DailyCosts.of(start)

        assertEquals(start.employees.sumOf { it.salaryPerDay }, costs.wages)
        assertTrue(costs.premises >= start.restaurant.operatingCosts.rentPerDay)
    }

    @Test
    fun `hiring raises the daily costs by the new person's salary`() {
        val applicant = start.applicants.first()
        val hired = DecisionApplier.apply(start, PlayerDecisions(hires = setOf(applicant.id))).state

        assertEquals(DailyCosts.of(start).total + applicant.salaryPerDay, DailyCosts.of(hired).total)
    }

    @Test
    fun `an empty pantry produces urgent restock advice, and buying clears it`() {
        val emptyLettuce = start.copy(
            inventory = start.inventory.copy(
                ingredients = start.inventory.ingredients.mapValues { (id, ingredient) ->
                    if (id == RecipeBook.LETTUCE) ingredient.copy(quantityOnHand = 0.0) else ingredient
                },
            ),
        )

        val advice = MorningAdvisor.adviceFor(emptyLettuce).filterIsInstance<Advice.Restock>().single { it.ingredient.id == RecipeBook.LETTUCE }
        assertTrue(advice.urgent)

        val restocked = DecisionApplier.apply(emptyLettuce, PlayerDecisions(purchases = mapOf(RecipeBook.LETTUCE to advice.suggestedQuantity))).state
        assertTrue(MorningAdvisor.adviceFor(restocked).filterIsInstance<Advice.Restock>().none { it.ingredient.id == RecipeBook.LETTUCE })
    }

    @Test
    fun `overstaffing for the crowd shows up as a losing day`() {
        val overstaffed = start.copy(employees = start.employees + start.applicants + start.applicants.map { it.copy(id = com.recipefordisaster.domain.employee.EmployeeId(it.id.value + "-twin")) })

        assertTrue(MorningAdvisor.forecast(overstaffed).expectedProfit < 0)
        assertTrue(MorningAdvisor.adviceFor(overstaffed).any { it is Advice.LosingMoney })
    }

    @Test
    fun `a broken oven is flagged with its repair cost`() {
        val broken = start.copy(equipment = start.equipment.map { it.copy(condition = 0) })

        assertTrue(MorningAdvisor.adviceFor(broken).any { it is Advice.EquipmentBroken && it.repairCost > 0 })
    }

    @Test
    fun `urgent advice comes first`() {
        val messy = start.copy(
            restaurant = start.restaurant.copy(cleanliness = 50),
            equipment = start.equipment.map { it.copy(condition = 0) },
        )

        val advice = MorningAdvisor.adviceFor(messy)
        assertEquals(advice.sortedByDescending { it.urgent }, advice)
    }

    @Test
    fun `each day comes with a summary that matches the books`() {
        val result = DefaultDayTickEngine(EventEngine(emptyList())).advanceDay(start, PlayerDecisions(), SeededRandomSource(1))
        val summary = result.summary!!

        assertEquals(start.day, summary.day)
        assertTrue(summary.customersFed <= summary.customersArrived)
        assertEquals(summary.customersArrived, summary.customersFed + summary.unfedKitchenFull + summary.unfedOutOfStock + summary.walkedOut + summary.gaveUpWaiting)
        assertEquals(result.newState.restaurant.cash, summary.cashAfter)
        assertEquals(start.restaurant.cash + result.newState.ledger.history.last().profitOrLoss, summary.cashAfter)
        assertEquals(summary.customersArrived, summary.guests.size)
        assertEquals(summary.customersFed, summary.guests.count { it.outcome == GuestOutcome.FED })
        assertEquals(result.newState.ledger.history.last().revenue, summary.guests.sumOf { it.paid })
    }

    @Test
    fun `buying the whole restock list clears every restock item`() {
        val bare = start.copy(inventory = start.inventory.copy(ingredients = start.inventory.ingredients.mapValues { it.value.copy(quantityOnHand = 0.0) }))
        val list = MorningAdvisor.restockList(bare)
        assertTrue(list.isNotEmpty())

        val stocked = DecisionApplier.apply(bare, PlayerDecisions(purchases = list)).state

        assertTrue(MorningAdvisor.adviceFor(stocked).none { it is Advice.Restock })
    }

    @Test
    fun `nights of stock is null for ingredients tonight's menu doesn't use`() {
        val eggs = start.inventory.ingredients.getValue(RecipeBook.EGGS)
        val flour = start.inventory.ingredients.getValue(RecipeBook.FLOUR)

        assertEquals(null, MorningAdvisor.nightsOfStock(start, eggs))
        assertTrue(MorningAdvisor.nightsOfStock(start, flour)!! > 0.0)
    }

    @Test
    fun `the hiring sign asks for a cook when the kitchen can't keep up, and nothing when all is well`() {
        // A full house with the only oven broken: one cook can't keep up.
        val busy = start.copy(restaurant = start.restaurant.copy(reputation = 100), equipment = start.equipment.map { it.copy(condition = 0) })
        assertEquals(MorningAdvisor.Need.COOK, MorningAdvisor.staffNeed(busy))

        // A full house with a working kitchen is fine: one cook keeps up during a played night.
        val full = start.copy(restaurant = start.restaurant.copy(reputation = 100))
        assertTrue(MorningAdvisor.staffNeed(full) != MorningAdvisor.Need.COOK)

        val quiet = start.copy(restaurant = start.restaurant.copy(reputation = 30))
        assertEquals(null, MorningAdvisor.staffNeed(quiet))
    }

    @Test
    fun `a dirty restaurant with no dishwasher asks for one`() {
        val dirty = start.copy(restaurant = start.restaurant.copy(reputation = 30, cleanliness = 40))
        assertEquals(MorningAdvisor.Need.DISHWASHER, MorningAdvisor.staffNeed(dirty))
    }

    @Test
    fun `a worker is flagged as needing a day off while it can still help`() {
        val cook = start.employees.first()
        val tired = start.copy(employees = start.employees.map { if (it.id == cook.id) it.copy(stress = MorningAdvisor.TIRED_STRESS) else it.copy(stress = 10) })
        assertEquals(listOf(cook.id), MorningAdvisor.adviceFor(tired).filterIsInstance<Advice.StaffExhausted>().map { it.employee.id })
    }
}
