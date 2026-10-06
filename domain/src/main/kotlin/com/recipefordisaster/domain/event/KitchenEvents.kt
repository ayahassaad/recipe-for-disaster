package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.equipment.EquipmentOperations
import com.recipefordisaster.domain.equipment.Fridge
import com.recipefordisaster.domain.simulation.LogTone

/**
 * Events about the physical restaurant: worn-out equipment and a dirty
 * kitchen. The two chains here are the point — a neglected machine can
 * catch fire, which brings a fire inspection the next day; a dirty kitchen
 * attracts rats, which brings a health inspector. Both inspections judge
 * cleanliness *at the time they arrive*, so the player gets one morning to
 * scramble (a deep clean, a dishwasher) before the verdict.
 */
internal object KitchenEvents {

    private const val FIRE_INSPECTION_ID = "fire_inspection"
    private const val HEALTH_INSPECTION_ID = "health_inspection"

    val kitchenFire = EventRule(
        id = "kitchen_fire",
        title = "Kitchen fire!",
        severity = Severity.SEVERE,
        cooldownDays = 10,
        unique = false,
        prerequisite = { state -> Fridge.cookingKit(state.equipment).any { !EquipmentOperations.isBroken(it) && it.condition < 35 } },
        weight = { state ->
            val worst = Fridge.cookingKit(state.equipment).filter { !EquipmentOperations.isBroken(it) }.minOf { it.condition }
            val grease = if (state.restaurant.cleanliness < 40) 0.4 else 0.0
            (0.3 + (35 - worst) / 30.0 + grease).toFloat()
        },
        resolve = { state, _ ->
            val burning = Fridge.cookingKit(state.equipment).filter { !EquipmentOperations.isBroken(it) }.minBy { it.condition }
            EventOutcome(
                ruleId = "kitchen_fire",
                description = "The ${burning.name} burst into flames mid-service. Everyone's fine. The ${burning.name} is not. The fire service will inspect tomorrow.",
                resultingState = state
                    .copy(equipment = state.equipment.map { if (it.id == burning.id) it.copy(condition = 0) else it })
                    .adjustCleanliness(-20)
                    .adjustCash(-120)
                    .adjustReputation(-4),
                followUpRuleIds = setOf(FIRE_INSPECTION_ID),
                tone = LogTone.BAD,
            )
        },
    )

    /** Only ever happens as a consequence of [kitchenFire] — its weight is zero, so it never fires by chance. */
    val fireInspection = EventRule(
        id = FIRE_INSPECTION_ID,
        title = "Fire inspection",
        severity = Severity.MODERATE,
        cooldownDays = 0,
        unique = false,
        prerequisite = { true },
        weight = { 0f },
        resolve = { state, _ ->
            if (state.restaurant.cleanliness >= 50) {
                EventOutcome(
                    ruleId = FIRE_INSPECTION_ID,
                    description = "The fire inspector found the kitchen tidy and the extinguisher \"surprisingly recently used.\" No fine.",
                    resultingState = state,
                    tone = LogTone.NEUTRAL,
                )
            } else {
                EventOutcome(
                    ruleId = FIRE_INSPECTION_ID,
                    description = "The fire inspector found grease on every surface and fined you 150. They also took a photo.",
                    resultingState = state.adjustCash(-150).adjustReputation(-3),
                    tone = LogTone.BAD,
                )
            }
        },
    )

    val fridgeFailure = EventRule(
        id = "fridge_failure",
        title = "Fridge failure",
        severity = Severity.MODERATE,
        cooldownDays = 8,
        unique = false,
        // Only a worn fridge gives up, so a morning repair keeps it from happening.
        prerequisite = { state ->
            val fridge = Fridge.of(state)
            state.day >= 3 && fridge != null && !EquipmentOperations.isBroken(fridge) && Fridge.isWorn(fridge) &&
                state.inventory.ingredients.values.filter { Fridge.isCold(it) }.sumOf { it.quantityOnHand } > 3.0
        },
        weight = { state -> (0.2 + (Fridge.WORN - (Fridge.of(state)?.condition ?: Fridge.WORN)) / 40.0).toFloat() },
        resolve = { state, _ ->
            val ingredients = state.inventory.ingredients.mapValues { (_, ingredient) ->
                if (Fridge.isCold(ingredient)) ingredient.copy(quantityOnHand = ingredient.quantityOnHand / 2) else ingredient
            }
            EventOutcome(
                ruleId = "fridge_failure",
                description = "The fridge gave up overnight. Half the perishables are now officially \"compost.\" Fix it before tonight.",
                resultingState = state.copy(
                    inventory = state.inventory.copy(ingredients = ingredients),
                    equipment = state.equipment.map { if (Fridge.isFridge(it)) it.copy(condition = 0) else it },
                ),
                tone = LogTone.BAD,
            )
        },
    )

    val ratSighting = EventRule(
        id = "rat_sighting",
        title = "Rat!",
        severity = Severity.MODERATE,
        cooldownDays = 5,
        unique = false,
        prerequisite = { state -> state.restaurant.cleanliness < 45 },
        weight = { state -> (0.2 + (45 - state.restaurant.cleanliness) / 12.0).toFloat() },
        resolve = { state, _ ->
            EventOutcome(
                ruleId = "rat_sighting",
                description = "A customer saw a rat. The rat saw the customer. Both screamed. Word travels — expect a health inspector.",
                resultingState = state.adjustReputation(-5),
                followUpRuleIds = setOf(HEALTH_INSPECTION_ID),
                tone = LogTone.BAD,
            )
        },
    )

    val healthInspection = EventRule(
        id = HEALTH_INSPECTION_ID,
        title = "Health inspection",
        severity = Severity.MODERATE,
        cooldownDays = 8,
        unique = false,
        prerequisite = { state -> state.day >= 3 },
        weight = { state -> (0.15 + (60 - state.restaurant.cleanliness).coerceAtLeast(0) / 60.0).toFloat() },
        resolve = { state, _ ->
            val cleanliness = state.restaurant.cleanliness
            when {
                cleanliness >= 70 -> EventOutcome(
                    ruleId = HEALTH_INSPECTION_ID,
                    description = "A health inspector dropped by and left a gold sticker. Nobody knew they did stickers.",
                    resultingState = state.adjustReputation(3),
                    tone = LogTone.GOOD,
                )
                cleanliness >= 45 -> EventOutcome(
                    ruleId = HEALTH_INSPECTION_ID,
                    description = "A health inspector sighed deeply, wrote \"adequate,\" and left. A pass is a pass.",
                    resultingState = state,
                    tone = LogTone.NEUTRAL,
                )
                cleanliness >= 20 -> EventOutcome(
                    ruleId = HEALTH_INSPECTION_ID,
                    description = "The health inspector fined you 250 and put a notice in the window. It's not a nice notice.",
                    resultingState = state.adjustCash(-250).adjustReputation(-8),
                    tone = LogTone.BAD,
                )
                else -> EventOutcome(
                    ruleId = HEALTH_INSPECTION_ID,
                    description = "The health inspector left in a hazmat suit they'd brought \"just in case.\" Fined 400.",
                    resultingState = state.adjustCash(-400).adjustReputation(-15),
                    tone = LogTone.BAD,
                )
            }
        },
    )

    val all = listOf(kitchenFire, fireInspection, fridgeFailure, ratSighting, healthInspection)
}
