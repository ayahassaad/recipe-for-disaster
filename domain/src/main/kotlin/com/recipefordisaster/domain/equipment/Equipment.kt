package com.recipefordisaster.domain.equipment

data class Equipment(
    val id: EquipmentId,
    val name: String,
    val purchaseCost: Long,
    val condition: Int,
    val capacityEffect: Int,
    val maintenanceCostPerDay: Long,
    val failureProbabilityBase: Double,
    val upgradeLevel: Int,
)

@JvmInline
value class EquipmentId(val value: String)
