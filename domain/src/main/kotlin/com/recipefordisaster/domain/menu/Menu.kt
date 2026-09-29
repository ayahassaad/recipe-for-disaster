package com.recipefordisaster.domain.menu

import com.recipefordisaster.domain.customer.DietaryRequirement
import com.recipefordisaster.domain.inventory.IngredientId

data class Dish(
    val id: DishId,
    val name: String,
    val recipe: Recipe,
    val sellingPrice: Long,
    val popularity: Int,
    val quality: Int,
    val available: Boolean,
    val allergens: Set<Allergen>,
)

@JvmInline
value class DishId(val value: String)

data class Recipe(
    val ingredientRequirements: Map<IngredientId, Double>,
    val preparationTimeMinutes: Int,
)

/**
 * Allergens are modeled as an explicit, checkable set on every dish — never
 * inferred from an icon or a color alone (section 3 / accessibility
 * requirements both depend on this being real data).
 */
enum class Allergen {
    GLUTEN,
    DAIRY,
    NUTS,
    SHELLFISH,
    EGGS,
    SOY,
}

fun Dish.violates(requirement: DietaryRequirement): Boolean = when (requirement) {
    DietaryRequirement.GLUTEN_FREE -> Allergen.GLUTEN in allergens
    DietaryRequirement.NUT_ALLERGY -> Allergen.NUTS in allergens
    DietaryRequirement.SHELLFISH_ALLERGY -> Allergen.SHELLFISH in allergens
    DietaryRequirement.LACTOSE_INTOLERANT -> Allergen.DAIRY in allergens
    DietaryRequirement.VEGETARIAN, DietaryRequirement.VEGAN -> false // TODO(Phase 3): needs an explicit ingredient-level meat/animal-product model, not just allergens.
}
