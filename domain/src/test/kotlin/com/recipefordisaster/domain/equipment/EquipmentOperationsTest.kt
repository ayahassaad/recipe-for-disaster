package com.recipefordisaster.domain.equipment

import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentOperationsTest {

    private fun fryer(condition: Int, upgradeLevel: Int = 0, failureProbabilityBase: Double = 0.01) = Equipment(
        id = EquipmentId("fryer-1"),
        name = "Deep Fryer",
        purchaseCost = 2000,
        condition = condition,
        capacityEffect = 10,
        maintenanceCostPerDay = 5,
        failureProbabilityBase = failureProbabilityBase,
        upgradeLevel = upgradeLevel,
    )

    @Test
    fun `a busy day wears equipment down more than a quiet one`() {
        val equipment = fryer(condition = 100)

        val afterQuietDay = EquipmentOperations.applyDailyWear(equipment, usageIntensity = 0.1)
        val afterBusyDay = EquipmentOperations.applyDailyWear(equipment, usageIntensity = 1.0)

        assertTrue(afterBusyDay.condition < afterQuietDay.condition)
    }

    @Test
    fun `upgrades reduce wear`() {
        val base = fryer(condition = 100, upgradeLevel = 0)
        val upgraded = fryer(condition = 100, upgradeLevel = 3)

        val baseAfter = EquipmentOperations.applyDailyWear(base, usageIntensity = 1.0)
        val upgradedAfter = EquipmentOperations.applyDailyWear(upgraded, usageIntensity = 1.0)

        assertTrue(upgradedAfter.condition > baseAfter.condition)
    }

    @Test
    fun `low condition equipment fails far more often than well-maintained equipment`() {
        val rng = SeededRandomSource(seed = 99)
        val wellMaintained = fryer(condition = 95)
        val neglected = fryer(condition = 10)

        val wellMaintainedFailures = (1..200).count { EquipmentOperations.rollForFailure(wellMaintained, rng) }
        val neglectedFailures = (1..200).count { EquipmentOperations.rollForFailure(neglected, rng) }

        assertTrue(neglectedFailures > wellMaintainedFailures)
    }
}
