package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.inventory.InventoryOperations
import com.recipefordisaster.domain.simulation.LogTone

/**
 * Events about suppliers and money. Price hikes and sales move ingredient
 * prices in both directions, so what a dish costs to make drifts over a run
 * and the menu's margins need revisiting. The benefactor is a deliberate
 * one-time lifeline for a restaurant on the edge — a run should be able to
 * come back from the brink once, through luck, but not lean on it.
 */
internal object SupplyEvents {

    private const val MAX_INGREDIENT_PRICE = 15L

    val supplierPriceHike = EventRule(
        id = "supplier_price_hike",
        title = "Price hike",
        severity = Severity.MINOR,
        cooldownDays = 6,
        unique = false,
        prerequisite = { state -> state.inventory.ingredients.values.any { it.purchasePricePerUnit < MAX_INGREDIENT_PRICE } },
        weight = { 0.3f },
        resolve = { state, rng ->
            val target = rng.pick(state.inventory.ingredients.values.filter { it.purchasePricePerUnit < MAX_INGREDIENT_PRICE })
            val increase = (target.purchasePricePerUnit / 4).coerceAtLeast(1)
            val newPrice = (target.purchasePricePerUnit + increase).coerceAtMost(MAX_INGREDIENT_PRICE)
            EventOutcome(
                ruleId = "supplier_price_hike",
                description = "Your supplier says ${target.name} is \"in very high demand.\" It now costs $newPrice per ${target.unit}.",
                resultingState = state.copy(
                    inventory = state.inventory.copy(
                        ingredients = state.inventory.ingredients + (target.id to target.copy(purchasePricePerUnit = newPrice)),
                    ),
                ),
                tone = LogTone.BAD,
            )
        },
    )

    val supplierSale = EventRule(
        id = "supplier_sale",
        title = "Supplier sale",
        severity = Severity.TRIVIAL,
        cooldownDays = 6,
        unique = false,
        prerequisite = { state -> state.inventory.ingredients.values.any { it.purchasePricePerUnit >= 3 } },
        weight = { 0.25f },
        resolve = { state, rng ->
            val target = rng.pick(state.inventory.ingredients.values.filter { it.purchasePricePerUnit >= 3 })
            val newPrice = target.purchasePricePerUnit - (target.purchasePricePerUnit / 4).coerceAtLeast(1)
            EventOutcome(
                ruleId = "supplier_sale",
                description = "Your supplier over-ordered ${target.name}, so it's down to $newPrice per ${target.unit}. Good time to stock up.",
                resultingState = state.copy(
                    inventory = state.inventory.copy(
                        ingredients = state.inventory.ingredients + (target.id to target.copy(purchasePricePerUnit = newPrice)),
                    ),
                ),
                tone = LogTone.GOOD,
            )
        },
    )

    val mysteryDelivery = EventRule(
        id = "mystery_delivery",
        title = "Mystery delivery",
        severity = Severity.TRIVIAL,
        cooldownDays = 8,
        unique = false,
        prerequisite = { state -> state.inventory.ingredients.isNotEmpty() && !InventoryOperations.isOverCapacity(state.inventory) },
        weight = { 0.2f },
        resolve = { state, rng ->
            val target = rng.pick(state.inventory.ingredients.values.toList())
            val freeSpace = state.inventory.storageCapacity - InventoryOperations.totalStorageUsed(state.inventory)
            val quantity = minOf(3.0 + rng.nextInt(6), freeSpace / target.storageSpaceRequired.coerceAtLeast(0.01)).coerceAtLeast(0.0)
            EventOutcome(
                ruleId = "mystery_delivery",
                description = "A delivery nobody ordered turned up: ${quantity.toInt()} ${target.unit} of ${target.name}. Finders keepers.",
                resultingState = state.copy(
                    inventory = state.inventory.copy(
                        ingredients = state.inventory.ingredients + (target.id to target.copy(quantityOnHand = target.quantityOnHand + quantity)),
                    ),
                ),
                tone = LogTone.GOOD,
            )
        },
    )

    val mysteriousBenefactor = EventRule(
        id = "mysterious_benefactor",
        title = "A mysterious benefactor",
        severity = Severity.MODERATE,
        cooldownDays = 0,
        unique = true,
        prerequisite = { state -> state.restaurant.cash < 600 },
        weight = { 0.6f },
        resolve = { state, _ ->
            EventOutcome(
                ruleId = "mysterious_benefactor",
                description = "A stranger in a cape paid for their soup with a 400-coin gold piece and vanished. They didn't want change.",
                resultingState = state.adjustCash(400),
                tone = LogTone.GOOD,
            )
        },
    )

    /**
     * The slow squeeze: the longer a run goes, the likelier the landlord
     * notices. This is what keeps a comfortable mid-game from becoming a
     * run that can never end.
     */
    val rentHike = EventRule(
        id = "rent_hike",
        title = "Rent increase",
        severity = Severity.MODERATE,
        cooldownDays = 10,
        unique = false,
        prerequisite = { state -> state.day >= 12 },
        weight = { state -> (0.1 + state.day / 150.0).toFloat() },
        resolve = { state, _ ->
            val costs = state.restaurant.operatingCosts
            val newRent = costs.rentPerDay + (costs.rentPerDay / 8).coerceAtLeast(5)
            EventOutcome(
                ruleId = "rent_hike",
                description = "The landlord popped in, admired how busy you are, and raised the rent to $newRent a day.",
                resultingState = state.copy(restaurant = state.restaurant.copy(operatingCosts = costs.copy(rentPerDay = newRent))),
                tone = LogTone.BAD,
            )
        },
    )

    val all = listOf(supplierPriceHike, supplierSale, mysteryDelivery, mysteriousBenefactor, rentHike)
}
