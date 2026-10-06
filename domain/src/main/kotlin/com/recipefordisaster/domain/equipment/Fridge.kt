package com.recipefordisaster.domain.equipment

import com.recipefordisaster.domain.inventory.Ingredient
import com.recipefordisaster.domain.simulation.GameState

/**
 * The fridge: it keeps the perishables (meat, cheese, lettuce, broth, fish)
 * cold. It wears out like the oven, and once it's worn it can give up —
 * overnight (spoiling half the cold stock) or in the middle of service, when
 * dishes needing cold ingredients can't be made until someone fixes it.
 * A proper repair in the morning puts it right; a quick fix mid-service only
 * keeps it going for the night.
 */
object Fridge {
    val ID = EquipmentId("fridge")

    /** Below this condition the fridge is worn: it can break, and it gets a red ! in the morning. */
    const val WORN = 50

    fun of(state: GameState): Equipment? = state.equipment.firstOrNull { it.id == ID }

    fun isFridge(equipment: Equipment) = equipment.id == ID

    /** The kit that does the cooking (everything but the fridge). */
    fun cookingKit(equipment: List<Equipment>) = equipment.filterNot { isFridge(it) }

    /** Ingredients that need keeping cold: the ones that go off quickly. */
    fun isCold(ingredient: Ingredient) = ingredient.spoilageRatePerDay >= 0.05

    fun isWorn(fridge: Equipment) = fridge.condition < WORN

    /** A new restaurant's fridge: old but working. */
    fun starter() = Equipment(
        id = ID,
        name = "Old Fridge",
        purchaseCost = 400,
        condition = 80,
        capacityEffect = 0,
        maintenanceCostPerDay = 3,
        failureProbabilityBase = 0.01,
        upgradeLevel = 1,
    )

    /** Saves from before the fridge existed get one. */
    fun ensure(state: GameState): GameState = if (of(state) != null) state else state.copy(equipment = state.equipment + starter())
}
