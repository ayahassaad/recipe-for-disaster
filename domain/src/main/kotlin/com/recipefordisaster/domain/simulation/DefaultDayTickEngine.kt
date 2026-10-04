package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.CustomerFlow
import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.economy.DailyFinancialsCalculator
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.event.Severity
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.restaurant.CleanlinessModel
import com.recipefordisaster.domain.restaurant.ReputationModel
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.ServiceSimulator.MissedMealReason

/**
 * The real implementation of the core simulation loop. One day, in order:
 *
 * 1. The morning: the player's [PlayerDecisions] are carried out by
 *    [DecisionApplier] (hiring, prices, repairs, groceries...).
 * 2. Service: customers arrive in proportion to reputation (plus any
 *    event-driven demand swing), and [ServiceSimulator] feeds them — limited
 *    by what the kitchen can cook ([KitchenModel]) and what's in stock.
 * 3. Closing: stock spoils, staff stress/rest/sickness moves on, equipment
 *    wears and may break, the floor gets dirtier, the day's books are
 *    closed, and average satisfaction moves reputation — which feeds back
 *    into tomorrow's demand, closing the product brief's feedback loop.
 * 4. Overnight: [EventEngine] gets a chance to fire one event (or a
 *    follow-up an earlier event scheduled).
 *
 * NOTE: `GameState.day` and `Restaurant.currentDay` currently track the
 * same thing redundantly — a wrinkle inherited from the Phase 2 scaffold,
 * kept in sync here rather than silently resolved, since collapsing it to
 * one field is a design change outside this phase's approved scope.
 */
class DefaultDayTickEngine(
    private val eventEngine: EventEngine,
) : DayTickEngine {

    override fun advanceDay(state: GameState, decisions: PlayerDecisions, rng: RandomSource): DayResult {
        // 1. Morning
        val morning = DecisionApplier.apply(state, decisions)
        val start = morning.state
        val dayLog = morning.log.toMutableList()

        // 2. Service
        val arrivals = CustomerFlow.generateArrivals(start.restaurant, rng, start.pendingDemandModifierPercent)
        val serviceResult = ServiceSimulator.simulate(
            arrivals = arrivals,
            employees = start.employees,
            menu = start.menu,
            inventory = start.inventory,
            kitchenCapacity = KitchenModel.mealCapacity(start.employees, start.equipment),
            kitchenQualityBonus = KitchenModel.qualityBonus(start.employees, start.equipment, start.restaurant.cleanliness),
            rng = rng,
        )
        val customersFed = serviceResult.outcomes.count { it.dish != null }

        // 3. Closing
        val inventoryAfterSpoilage = InventoryOperations.applySpoilage(serviceResult.inventoryAfter ?: start.inventory)

        val employeesAfterDay = start.employees.map { employee ->
            when (employee.status) {
                EmployeeStatus.ACTIVE -> EmployeePerformance.applyEndOfDayStress(employee, serviceResult.staffingRatio)
                EmployeeStatus.ON_BREAK -> EmployeePerformance.returnFromDayOff(employee)
                EmployeeStatus.SICK -> EmployeePerformance.advanceSickness(employee)
                EmployeeStatus.QUIT, EmployeeStatus.FIRED -> employee
            }
        }

        val usageIntensity = if (start.restaurant.capacity > 0) {
            (arrivals.size.toDouble() / start.restaurant.capacity).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val equipmentAfterWear = start.equipment.map { EquipmentOperations.applyDailyWear(it, usageIntensity) }
        val equipmentAfterFailureChecks = equipmentAfterWear.map { equipment ->
            if (EquipmentOperations.rollForFailure(equipment, rng)) {
                dayLog += SimulationLogEntry(state.day, "The ${equipment.name} broke down. The kitchen will be slower until it's repaired.", LogTone.BAD)
                equipment.copy(condition = 0)
            } else {
                equipment
            }
        }

        val newCleanliness = CleanlinessModel.endOfDayCleanliness(start.restaurant.cleanliness, customersFed, start.employees)

        // Neutral (50) satisfaction on a customer-free day, so a slow
        // Tuesday doesn't accidentally read as either a triumph or a
        // disaster for reputation purposes.
        val averageSatisfaction = if (serviceResult.outcomes.isEmpty()) {
            50
        } else {
            serviceResult.outcomes.map { it.satisfaction }.average().toInt()
        }
        val reputationDelta = ReputationModel.dailyReputationDelta(averageSatisfaction, start.restaurant.cleanliness)

        val financials = DailyFinancialsCalculator.calculate(
            day = state.day,
            dishesSold = serviceResult.dishesSold,
            menu = start.menu,
            // Wages follow who was actually on shift today, before anyone's
            // day off or sickness rolled over to tomorrow's status.
            employees = start.employees,
            ingredientCosts = morning.spending.ingredients,
            operatingCosts = start.restaurant.operatingCosts,
            equipment = equipmentAfterFailureChecks,
            miscellaneous = morning.spending.staffing + morning.spending.cleaning + morning.spending.menu,
            upgrades = morning.spending.repairs,
        )

        val newCash = start.restaurant.cash + financials.profitOrLoss
        val newReputation = (start.restaurant.reputation + reputationDelta).coerceIn(0, 100)

        dayLog += SimulationLogEntry(
            day = state.day,
            message = "Served $customersFed of ${arrivals.size} customers. " +
                "Revenue ${financials.revenue}, profit/loss ${financials.profitOrLoss}, " +
                "reputation ${if (reputationDelta >= 0) "+" else ""}$reputationDelta.",
            tone = if (financials.profitOrLoss >= 0) LogTone.GOOD else LogTone.BAD,
        )
        serviceResult.missedCount(MissedMealReason.KITCHEN_OVERWHELMED).takeIf { it > 0 }?.let { count ->
            dayLog += SimulationLogEntry(state.day, "The kitchen couldn't keep up: $count customer${if (count == 1) "" else "s"} never got fed.", LogTone.BAD)
        }
        serviceResult.missedCount(MissedMealReason.OUT_OF_STOCK).takeIf { it > 0 }?.let { count ->
            dayLog += SimulationLogEntry(state.day, "Ran out of ingredients: $count customer${if (count == 1) "" else "s"} left hungry.", LogTone.BAD)
        }

        val updatedDishTotals = start.dishSalesTotals.toMutableMap()
        serviceResult.dishesSold.forEach { (dishId, count) -> updatedDishTotals.merge(dishId.value, count, Int::plus) }

        val nextDay = state.day + 1
        val applicants = if (nextDay % StaffingMarket.REFRESH_EVERY_DAYS == 0 || start.applicants.isEmpty()) {
            StaffingMarket.generateApplicants(rng, nextDay)
        } else {
            start.applicants
        }

        val stateBeforeEvent = start.copy(
            day = nextDay,
            restaurant = start.restaurant.copy(
                cash = newCash,
                reputation = newReputation,
                cleanliness = newCleanliness,
                currentDay = nextDay,
            ),
            employees = employeesAfterDay,
            customersPresent = emptyList(),
            inventory = inventoryAfterSpoilage,
            equipment = equipmentAfterFailureChecks,
            ledger = start.ledger.copy(history = start.ledger.history + financials),
            log = start.log + dayLog,
            eventCooldowns = EventEngine.decayCooldowns(start.eventCooldowns),
            dishSalesTotals = updatedDishTotals,
            applicants = applicants,
            recentSatisfaction = averageSatisfaction,
            pendingDemandModifierPercent = 0,
        ).withStatusChecked()

        // 4. Overnight — but not once the run is already over.
        if (stateBeforeEvent.isGameOver()) {
            return DayResult(newState = stateBeforeEvent, log = dayLog)
        }

        val prepared = eventEngine.prepare(stateBeforeEvent)
        val eventOutcome = eventEngine.selectNext(prepared, rng)
        if (eventOutcome == null) {
            return DayResult(newState = prepared, log = dayLog)
        }

        val rule = eventEngine.ruleById(eventOutcome.ruleId)
        val eventLogEntry = SimulationLogEntry(stateBeforeEvent.day, eventOutcome.description, eventOutcome.tone)
        val finalState = eventOutcome.resultingState
            .copy(log = eventOutcome.resultingState.log + eventLogEntry)
            .withStatusChecked()
        val firedEvent = FiredEvent(
            ruleId = eventOutcome.ruleId,
            title = rule?.title ?: eventOutcome.ruleId,
            description = eventOutcome.description,
            severity = rule?.severity ?: Severity.MINOR,
            tone = eventOutcome.tone,
        )
        return DayResult(newState = finalState, log = dayLog, event = firedEvent)
    }

    /** Re-derives game-over status from cash and reputation — run again after events, since a fine can bankrupt you. */
    private fun GameState.withStatusChecked(): GameState {
        val status = when {
            restaurant.status == RestaurantStatus.BANKRUPT || restaurant.status == RestaurantStatus.CONDEMNED -> restaurant.status
            restaurant.cash < 0 -> RestaurantStatus.BANKRUPT
            restaurant.reputation <= 0 -> RestaurantStatus.CONDEMNED
            else -> restaurant.status
        }
        return if (status == restaurant.status) this else copy(restaurant = restaurant.copy(status = status))
    }

    private fun GameState.isGameOver(): Boolean =
        restaurant.status == RestaurantStatus.BANKRUPT || restaurant.status == RestaurantStatus.CONDEMNED
}
