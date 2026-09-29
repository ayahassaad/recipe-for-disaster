package com.recipefordisaster.domain.equipment

import com.recipefordisaster.domain.simulation.RandomSource

/**
 * Wear accumulates from actual use (a busy day costs more condition than a
 * quiet one) rather than a flat per-day tick, so a string of packed nights
 * is what actually drives a fryer toward failure — not just the calendar.
 */
object EquipmentOperations {

    fun applyDailyWear(equipment: Equipment, usageIntensity: Double): Equipment {
        val intensity = usageIntensity.coerceIn(0.0, 1.0)
        val wear = (2 + intensity * 6).toInt() // 2-8 condition points per day depending on how hard it was used
        val effectiveWear = (wear * (1.0 - equipment.upgradeLevel * 0.1)).toInt().coerceAtLeast(1)

        return equipment.copy(condition = (equipment.condition - effectiveWear).coerceIn(0, 100))
    }

    /**
     * Failure probability rises sharply as condition degrades — a machine
     * at 90% condition should almost never fail; one limping along at 10%
     * should fail often enough that ignoring maintenance is a real risk,
     * not a background statistic.
     */
    fun failureProbability(equipment: Equipment): Double {
        val conditionFactor = (100 - equipment.condition.coerceIn(0, 100)) / 100.0
        return (equipment.failureProbabilityBase + conditionFactor * 0.5).coerceIn(0.0, 0.95)
    }

    fun rollForFailure(equipment: Equipment, rng: RandomSource): Boolean =
        rng.nextFloat() < failureProbability(equipment)
}
