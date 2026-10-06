package com.recipefordisaster.domain.equipment

/**
 * The better kit a restaurant can buy. The run starts with the Ancient Oven;
 * each step up costs money but cooks faster, breaks down less and wears
 * more slowly. Upgrading keeps the machine's id (it's still "the oven"), so
 * nothing that refers to it needs to change.
 */
object EquipmentCatalog {

    /** One model of oven: what it costs, and how good it is. [speed] feeds [Equipment.capacityEffect]. */
    data class Model(
        val level: Int,
        val name: String,
        val price: Long,
        val speed: Int,
        val maintenancePerDay: Long,
        val failureBase: Double,
    )

    val ovens: List<Model> = listOf(
        Model(level = 1, name = "Ancient Oven", price = 600, speed = 0, maintenancePerDay = 5, failureBase = 0.02),
        Model(level = 2, name = "Sturdy Oven", price = 450, speed = 1, maintenancePerDay = 4, failureBase = 0.01),
        Model(level = 3, name = "Convection Oven", price = 1_200, speed = 2, maintenancePerDay = 3, failureBase = 0.005),
    )

    /** Fridges: a sturdier one breaks down less and wears more slowly. They don't cook, so no speed. */
    val fridges: List<Model> = listOf(
        Model(level = 1, name = "Old Fridge", price = 400, speed = 0, maintenancePerDay = 3, failureBase = 0.01),
        Model(level = 2, name = "Sturdy Fridge", price = 350, speed = 0, maintenancePerDay = 2, failureBase = 0.005),
        Model(level = 3, name = "Steel Fridge", price = 900, speed = 0, maintenancePerDay = 2, failureBase = 0.002),
    )

    /** The models this kind of machine comes in. */
    fun modelsFor(equipment: Equipment): List<Model> = if (Fridge.isFridge(equipment)) fridges else ovens

    /** The next model up from this machine, or null if it's already the best there is. */
    fun nextModel(equipment: Equipment): Model? = modelsFor(equipment).firstOrNull { it.level == equipment.upgradeLevel + 1 }

    fun upgradeCost(equipment: Equipment): Long? = nextModel(equipment)?.price

    /** The machine replaced by the next model up: brand new, in perfect condition. */
    fun upgrade(equipment: Equipment): Equipment {
        val model = nextModel(equipment) ?: return equipment
        return equipment.copy(
            name = model.name,
            purchaseCost = model.price,
            condition = 100,
            capacityEffect = model.speed,
            maintenanceCostPerDay = model.maintenancePerDay,
            failureProbabilityBase = model.failureBase,
            upgradeLevel = model.level,
        )
    }

    /** How much quicker the kitchen cooks with this kit: 1.0 is the ancient oven, higher is faster. */
    fun cookingSpeed(equipment: List<Equipment>): Float =
        1f + 0.25f * equipment.filterNot { EquipmentOperations.isBroken(it) }.sumOf { it.capacityEffect }
}
