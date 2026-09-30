package com.recipefordisaster.domain.equipment

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
@JvmInline
value class EquipmentId(val value: String)
