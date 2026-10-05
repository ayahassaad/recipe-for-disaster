package com.recipefordisaster.domain.decision

import com.recipefordisaster.domain.employee.EmployeeStatus
import com.recipefordisaster.domain.employee.StaffingMarket
import com.recipefordisaster.domain.equipment.EquipmentCatalog
import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.menu.Dish
import com.recipefordisaster.domain.menu.RecipeBook
import com.recipefordisaster.domain.restaurant.CleanlinessModel
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.LogTone
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SimulationLogEntry
import kotlin.math.ceil

/** Fair-play bounds on what a dish can be priced at, relative to what customers think it's worth. */
object PriceRules {
    fun minPrice(dish: Dish): Long = (dish.referencePrice / 2).coerceAtLeast(1)
    fun maxPrice(dish: Dish): Long = (dish.referencePrice * 2).coerceAtLeast(2)
    fun clamp(dish: Dish, price: Long): Long = price.coerceIn(minPrice(dish), maxPrice(dish))
}

/** Money the morning's decisions committed, by category — these land in the day's [com.recipefordisaster.domain.economy.DailyFinancials]. */
data class DecisionSpending(
    val ingredients: Long = 0,
    val repairs: Long = 0,
    val upgrades: Long = 0,
    val staffing: Long = 0,
    val cleaning: Long = 0,
    val menu: Long = 0,
) {
    val total: Long get() = ingredients + repairs + upgrades + staffing + cleaning + menu
}

data class AppliedDecisions(
    val state: GameState,
    val spending: DecisionSpending,
    val log: List<SimulationLogEntry>,
)

/**
 * Turns a [PlayerDecisions] into a changed [GameState] (Phase 6). Pure and
 * deterministic — no randomness is involved in carrying out an order — so
 * the UI can call it on a draft to preview exactly what the morning will
 * cost, and [com.recipefordisaster.domain.simulation.DefaultDayTickEngine]
 * calls the very same function for real.
 *
 * Cash isn't touched here: every cost is returned in [DecisionSpending] and
 * booked once, in the day's financials, so nothing is double-counted. What
 * this does enforce is affordability — anything that would take committed
 * spending past the cash on hand is skipped (and logged), in a fixed
 * priority order: staffing, then menu, then upkeep, then groceries.
 */
object DecisionApplier {

    const val DEEP_CLEAN_COST = 60L

    fun apply(state: GameState, decisions: PlayerDecisions): AppliedDecisions {
        var current = state
        var spending = DecisionSpending()
        val log = mutableListOf<SimulationLogEntry>()
        fun note(message: String, tone: LogTone = LogTone.NEUTRAL) {
            log += SimulationLogEntry(state.day, message, tone)
        }
        fun canAfford(cost: Long) = spending.total + cost <= state.restaurant.cash

        // Firing first, so severance is accounted for before anything else spends the cash.
        for (id in decisions.fires) {
            val employee = current.employees.firstOrNull { it.id == id } ?: continue
            val cost = StaffingMarket.severance(employee)
            if (!canAfford(cost)) {
                note("Couldn't afford ${employee.name}'s severance, so they're still here.", LogTone.BAD)
                continue
            }
            spending = spending.copy(staffing = spending.staffing + cost)
            // Watching a colleague get walked out doesn't do wonders for anyone's mood.
            current = current.copy(
                employees = current.employees
                    .filter { it.id != id }
                    .map { it.copy(morale = (it.morale - 4).coerceIn(0, 100)) },
            )
            note("You let ${employee.name} go.")
        }

        for (id in decisions.hires) {
            val applicant = current.applicants.firstOrNull { it.id == id } ?: continue
            val cost = StaffingMarket.hiringFee(applicant)
            if (!canAfford(cost)) {
                note("Couldn't afford to hire ${applicant.name}.", LogTone.BAD)
                continue
            }
            spending = spending.copy(staffing = spending.staffing + cost)
            current = current.copy(
                employees = current.employees + applicant.copy(status = EmployeeStatus.ACTIVE),
                applicants = current.applicants.filter { it.id != id },
            )
            note("Hired ${applicant.name} as a ${applicant.role.name.lowercase()}.", LogTone.GOOD)
        }

        val resting = decisions.restDays.filter { id -> current.employees.any { it.id == id && it.status == EmployeeStatus.ACTIVE } }
        if (resting.isNotEmpty()) {
            current = current.copy(
                employees = current.employees.map { if (it.id in resting) it.copy(status = EmployeeStatus.ON_BREAK) else it },
            )
            note("Gave ${current.employees.filter { it.id in resting }.joinToString { it.name }} the day off.")
        }

        // Menu: prices and availability are free to change; new dishes cost a reprint.
        current = current.copy(
            menu = current.menu.map { dish ->
                val price = decisions.priceChanges[dish.id]?.let { PriceRules.clamp(dish, it) } ?: dish.sellingPrice
                val available = decisions.menuAvailability[dish.id] ?: dish.available
                dish.copy(sellingPrice = price, available = available)
            },
        )
        for (id in decisions.dishesToAdd) {
            val dish = RecipeBook.find(id) ?: continue
            if (current.menu.any { it.id == id }) continue
            if (!canAfford(RecipeBook.ADD_DISH_COST)) {
                note("Couldn't afford to put ${dish.name} on the menu.", LogTone.BAD)
                continue
            }
            spending = spending.copy(menu = spending.menu + RecipeBook.ADD_DISH_COST)
            current = current.copy(menu = current.menu + dish)
            note("Added ${dish.name} to the menu.", LogTone.GOOD)
        }

        val upgraded = mutableSetOf<com.recipefordisaster.domain.equipment.EquipmentId>()
        for (id in decisions.upgrades) {
            val equipment = current.equipment.firstOrNull { it.id == id } ?: continue
            val cost = EquipmentCatalog.upgradeCost(equipment) ?: continue
            if (!canAfford(cost)) {
                note("Couldn't afford a new ${EquipmentCatalog.nextModel(equipment)?.name}.", LogTone.BAD)
                continue
            }
            val better = EquipmentCatalog.upgrade(equipment)
            spending = spending.copy(upgrades = spending.upgrades + cost)
            current = current.copy(equipment = current.equipment.map { if (it.id == id) better else it })
            upgraded += id
            note("Out with the ${equipment.name}, in with a shiny ${better.name}.", LogTone.GOOD)
        }

        for (id in decisions.repairs) {
            if (id in upgraded) continue // a brand-new machine doesn't need fixing
            val equipment = current.equipment.firstOrNull { it.id == id } ?: continue
            val cost = EquipmentOperations.repairCost(equipment)
            if (cost <= 0) continue
            if (!canAfford(cost)) {
                note("Couldn't afford to repair the ${equipment.name}.", LogTone.BAD)
                continue
            }
            spending = spending.copy(repairs = spending.repairs + cost)
            current = current.copy(equipment = current.equipment.map { if (it.id == id) EquipmentOperations.repair(it) else it })
            note("Repaired the ${equipment.name}.", LogTone.GOOD)
        }

        if (decisions.deepClean) {
            if (canAfford(DEEP_CLEAN_COST)) {
                spending = spending.copy(cleaning = spending.cleaning + DEEP_CLEAN_COST)
                val cleaner = (current.restaurant.cleanliness + CleanlinessModel.DEEP_CLEAN_BOOST).coerceIn(0, 100)
                current = current.copy(restaurant = current.restaurant.copy(cleanliness = cleaner))
                note("Paid for a deep clean. It smells of lemons and regret.", LogTone.GOOD)
            } else {
                note("Couldn't afford the deep clean.", LogTone.BAD)
            }
        }

        // Groceries last: buy what fits in storage and in the budget, partially if need be.
        for ((ingredientId, requested) in decisions.purchases) {
            val ingredient = current.inventory.ingredients[ingredientId] ?: continue
            if (requested <= 0.0) continue
            val freeSpace = (current.inventory.storageCapacity - InventoryOperations.totalStorageUsed(current.inventory))
                .coerceAtLeast(0.0)
            val spaceLimited = minOf(requested, freeSpace / ingredient.storageSpaceRequired.coerceAtLeast(0.01))
            val budgetLeft = state.restaurant.cash - spending.total
            val affordable = if (ingredient.purchasePricePerUnit > 0) budgetLeft.toDouble() / ingredient.purchasePricePerUnit else spaceLimited
            val quantity = minOf(spaceLimited, affordable).coerceAtLeast(0.0)
            if (quantity <= 0.0) {
                note("Couldn't buy ${ingredient.name}: ${if (freeSpace <= 0.0) "the storeroom is full" else "not enough cash"}.", LogTone.BAD)
                continue
            }
            val cost = purchaseCost(ingredient.purchasePricePerUnit, quantity)
            spending = spending.copy(ingredients = spending.ingredients + cost)
            current = current.copy(
                inventory = current.inventory.copy(
                    ingredients = current.inventory.ingredients + (ingredientId to ingredient.copy(quantityOnHand = ingredient.quantityOnHand + quantity)),
                ),
            )
            if (quantity < requested) {
                note("Only managed to buy ${formatQuantity(quantity)} ${ingredient.unit} of ${ingredient.name}.", LogTone.BAD)
            }
        }
        if (spending.ingredients > 0) note("Spent ${spending.ingredients} on groceries.")

        return AppliedDecisions(current, spending, log)
    }

    /** Rounded up, so buying a sliver of something still costs at least a coin. */
    fun purchaseCost(pricePerUnit: Long, quantity: Double): Long =
        ceil(pricePerUnit * quantity - 1e-9).toLong().coerceAtLeast(0)

    private fun formatQuantity(quantity: Double): String = "%.1f".format(quantity)
}
