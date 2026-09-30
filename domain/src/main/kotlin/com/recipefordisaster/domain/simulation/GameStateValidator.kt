package com.recipefordisaster.domain.simulation

/**
 * Sanity-checks a [GameState] that just came back from persistence.
 * Successfully parsing JSON only proves the *shape* was right — it says
 * nothing about whether the values inside make sense (a hand-edited save
 * file, a bug in an earlier version, or plain bit rot could all produce a
 * structurally valid but nonsensical state). This is the "is this actually
 * trustworthy" check section 13 of the project brief calls for, kept in
 * `:domain` since what counts as a valid `GameState` is a domain rule, not
 * a persistence concern — `:data` only decides whether the bytes parsed.
 */
object GameStateValidator {

    fun validate(state: GameState): List<String> {
        val problems = mutableListOf<String>()

        if (state.day < 0) problems += "day is negative (${state.day})"
        if (state.restaurant.reputation !in 0..100) problems += "reputation out of range (${state.restaurant.reputation})"
        if (state.restaurant.cleanliness !in 0..100) problems += "cleanliness out of range (${state.restaurant.cleanliness})"
        if (state.restaurant.capacity < 0) problems += "capacity is negative (${state.restaurant.capacity})"

        state.employees.forEach { employee ->
            if (employee.morale !in 0..100) problems += "employee ${employee.id.value} morale out of range (${employee.morale})"
            if (employee.stress !in 0..100) problems += "employee ${employee.id.value} stress out of range (${employee.stress})"
            if (employee.skill !in 0..100) problems += "employee ${employee.id.value} skill out of range (${employee.skill})"
        }

        state.inventory.ingredients.values.forEach { ingredient ->
            if (ingredient.quantityOnHand < 0.0) problems += "ingredient ${ingredient.id.value} has negative quantity"
        }

        if (state.inventory.storageCapacity < 0.0) problems += "storage capacity is negative"

        return problems
    }

    fun isValid(state: GameState): Boolean = validate(state).isEmpty()
}
