package com.recipefordisaster.domain.inventory

import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    val id: IngredientId,
    val name: String,
    val quantityOnHand: Double,
    val unit: String,
    val purchasePricePerUnit: Long,
    val spoilageRatePerDay: Double,
    val storageSpaceRequired: Double,
)

@Serializable
@JvmInline
value class IngredientId(val value: String)

@Serializable
data class Supplier(
    val id: SupplierId,
    val name: String,
    val reliability: Int,
    val pricingMultiplier: Double,
    val availableIngredientIds: Set<IngredientId>,
)

@Serializable
@JvmInline
value class SupplierId(val value: String)

@Serializable
data class InventoryState(
    val ingredients: Map<IngredientId, Ingredient>,
    val storageCapacity: Double,
    val suppliers: List<Supplier>,
)
