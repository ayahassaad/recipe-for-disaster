package com.recipefordisaster.domain.simulation

import com.recipefordisaster.domain.customer.CustomerFlow
import com.recipefordisaster.domain.economy.DailyFinancialsCalculator
import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.DishId
import com.recipefordisaster.domain.restaurant.ReputationModel
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import kotlin.math.roundToLong

/**
 * The real implementation of the core simulation loop, replacing the
 * `TODO` left in Phase 2. Ties together every piece built in Phase 3:
 * customer arrivals, service quality, inventory consumption/spoilage,
 * employee stress, equipment wear, the day's financials, and reputation —
 * then hands off to [EventEngine] in case anything should fire. With an
 * empty rule list (the normal state until Phase 6 supplies real events),
 * that hand-off is a no-op.
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
        val dayLog = mutableListOf<SimulationLogEntry>()

        val arrivals = CustomerFlow.generateArrivals(state.restaurant, rng)
        val serviceResult = ServiceSimulator.simulate(arrivals, state.employees, state.menu)

        val inventoryAfterConsumption = consumeSoldDishes(state.inventory, state.menu, serviceResult.dishesSold)
        val ingredientCosts = calculateIngredientCosts(state.inventory, state.menu, serviceResult.dishesSold)
        val inventoryAfterSpoilage = InventoryOperations.applySpoilage(inventoryAfterConsumption)

        val employeesAfterDay = state.employees.map {
            EmployeePerformance.applyEndOfDayStress(it, serviceResult.staffingRatio)
        }

        val usageIntensity = if (state.restaurant.capacity > 0) {
            (arrivals.size.toDouble() / state.restaurant.capacity).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val equipmentAfterWear = state.equipment.map { EquipmentOperations.applyDailyWear(it, usageIntensity) }
        val equipmentAfterFailureChecks = equipmentAfterWear.map { equipment ->
            if (EquipmentOperations.rollForFailure(equipment, rng)) {
                dayLog += SimulationLogEntry(state.day, "${equipment.name} broke down and needs repair.")
                equipment.copy(condition = 0)
            } else {
                equipment
            }
        }

        // Neutral (50) satisfaction on a customer-free day, so a slow
        // Tuesday doesn't accidentally read as either a triumph or a
        // disaster for reputation purposes.
        val averageSatisfaction = if (serviceResult.outcomes.isEmpty()) {
            50
        } else {
            serviceResult.outcomes.map { it.satisfaction }.average().toInt()
        }
        val reputationDelta = ReputationModel.dailyReputationDelta(averageSatisfaction, state.restaurant.cleanliness)

        val financials = DailyFinancialsCalculator.calculate(
            day = state.day,
            dishesSold = serviceResult.dishesSold,
            menu = state.menu,
            employees = employeesAfterDay,
            ingredientCosts = ingredientCosts,
            operatingCosts = state.restaurant.operatingCosts,
            equipment = equipmentAfterFailureChecks,
        )

        val newCash = state.restaurant.cash + financials.profitOrLoss
        val newReputation = (state.restaurant.reputation + reputationDelta).coerceIn(0, 100)
        val newStatus = when {
            newCash < 0 -> RestaurantStatus.BANKRUPT
            newReputation <= 0 -> RestaurantStatus.CONDEMNED
            else -> state.restaurant.status
        }

        dayLog += SimulationLogEntry(
            day = state.day,
            message = "Served ${serviceResult.outcomes.count { it.dish != null }} of ${arrivals.size} arrivals. " +
                "Revenue ${financials.revenue}, profit/loss ${financials.profitOrLoss}, " +
                "reputation ${if (reputationDelta >= 0) "+" else ""}$reputationDelta.",
        )

        val updatedDishTotals = state.dishSalesTotals.toMutableMap()
        serviceResult.dishesSold.forEach { (dishId, count) -> updatedDishTotals.merge(dishId.value, count, Int::plus) }

        val stateBeforeEvent = state.copy(
            day = state.day + 1,
            restaurant = state.restaurant.copy(
                cash = newCash,
                reputation = newReputation,
                currentDay = state.day + 1,
                status = newStatus,
            ),
            employees = employeesAfterDay,
            customersPresent = emptyList(),
            inventory = inventoryAfterSpoilage,
            equipment = equipmentAfterFailureChecks,
            ledger = state.ledger.copy(history = state.ledger.history + financials),
            log = state.log + dayLog,
            eventCooldowns = EventEngine.decayCooldowns(state.eventCooldowns),
            dishSalesTotals = updatedDishTotals,
        )

        val eventOutcome = eventEngine.selectNext(stateBeforeEvent, rng)
        val finalState = if (eventOutcome != null) {
            val eventLogEntry = SimulationLogEntry(stateBeforeEvent.day, eventOutcome.description)
            eventOutcome.resultingState.copy(log = eventOutcome.resultingState.log + eventLogEntry)
        } else {
            stateBeforeEvent
        }

        return DayResult(newState = finalState, log = dayLog)
    }

    private fun consumeSoldDishes(inventory: InventoryState, menu: List<Dish>, dishesSold: Map<DishId, Int>): InventoryState {
        val menuById = menu.associateBy { it.id }
        return dishesSold.entries.fold(inventory) { inv, (dishId, count) ->
            val recipe = menuById[dishId]?.recipe ?: return@fold inv
            InventoryOperations.consume(inv, recipe, count)
        }
    }

    private fun calculateIngredientCosts(inventory: InventoryState, menu: List<Dish>, dishesSold: Map<DishId, Int>): Long {
        val menuById = menu.associateBy { it.id }
        return dishesSold.entries.sumOf { (dishId, count) ->
            val recipe = menuById[dishId]?.recipe ?: return@sumOf 0L
            recipe.ingredientRequirements.entries.sumOf { (ingredientId, amountPerServing) ->
                val price = inventory.ingredients[ingredientId]?.purchasePricePerUnit ?: 0L
                (amountPerServing * count * price).roundToLong()
            }
        }
    }
}
