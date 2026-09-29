package com.recipefordisaster.domain.inventory

data class Ingredient(
    val id: IngredientId,
    val name: String,
    val quantityOnHand: Double,
    val unit: String,
    val purchasePricePerUnit: Long,
    val spoilageRatePerDay: Double,
    val storageSpaceRequired: Double,
)

@JvmInline
value class IngredientId(val value: String)

data class Supplier(
    val id: SupplierId,
    val name: String,
    val reliability: Int,
    val pricingMultiplier: Double,
    val availableIngredientIds: Set<IngredientId>,
)

@JvmInline
value class SupplierId(val value: String)

data class InventoryState(
    val ingredients: Map<IngredientId, Ingredient>,
    val storageCapacity: Double,
    val suppliers: List<Supplier>,
)
