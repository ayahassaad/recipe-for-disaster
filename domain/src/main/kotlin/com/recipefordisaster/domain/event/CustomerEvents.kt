package com.recipefordisaster.domain.event

import com.recipefordisaster.domain.employee.EmployeePerformance
import com.recipefordisaster.domain.employee.Role
import com.recipefordisaster.domain.simulation.KitchenModel
import com.recipefordisaster.domain.simulation.LogTone

/**
 * Events about the outside world's opinion of the restaurant, and about
 * who walks through the door tomorrow. Most of these read
 * [com.recipefordisaster.domain.simulation.GameState.recentSatisfaction] or
 * reputation, so they amplify whatever direction things are already going
 * — the feedback loop from the product brief — while the demand-swing
 * events give the player a reason to plan ahead (stock up, add staff).
 */
internal object CustomerEvents {

    val foodCritic = EventRule(
        id = "food_critic",
        title = "Food critic",
        severity = Severity.MODERATE,
        cooldownDays = 12,
        unique = false,
        prerequisite = { state -> state.day >= 5 && state.restaurant.reputation >= 30 && state.menu.any { it.available } },
        weight = { state -> (0.2 + state.restaurant.reputation / 200.0).toFloat() },
        resolve = { state, rng ->
            val available = state.menu.filter { it.available }
            val signature = available.maxBy { it.quality }
            val kitchen = KitchenModel.qualityBonus(state.employees, state.equipment, state.restaurant.cleanliness)
            val score = available.map { it.quality }.average() + kitchen + (state.restaurant.cleanliness - 50) / 3.0 + (rng.nextInt(21) - 10)
            when {
                score >= 65 -> EventOutcome(
                    ruleId = "food_critic",
                    description = "A food critic called the ${signature.name} \"unexpectedly not terrible.\" From them, that's a rave.",
                    resultingState = state.adjustReputation(8).adjustTomorrowsDemand(20),
                    tone = LogTone.GOOD,
                )
                score >= 50 -> EventOutcome(
                    ruleId = "food_critic",
                    description = "A food critic ate the ${signature.name} in silence, wrote one word, and left. Nobody knows what it was.",
                    resultingState = state.adjustReputation(1),
                    tone = LogTone.NEUTRAL,
                )
                else -> EventOutcome(
                    ruleId = "food_critic",
                    description = "A food critic described the ${signature.name} as \"a cry for help.\" It's in the Sunday paper.",
                    resultingState = state.adjustReputation(-10),
                    tone = LogTone.BAD,
                )
            }
        },
    )

    val viralReview = EventRule(
        id = "viral_review",
        title = "Gone viral",
        severity = Severity.MODERATE,
        cooldownDays = 6,
        unique = false,
        prerequisite = { state -> state.recentSatisfaction >= 70 && state.menu.any { it.available } },
        weight = { state -> ((state.recentSatisfaction - 60) / 15.0).toFloat() },
        resolve = { state, rng ->
            val dish = rng.pick(state.menu.filter { it.available })
            EventOutcome(
                ruleId = "viral_review",
                description = "Someone's photo of the ${dish.name} went viral. Expect a crowd tomorrow.",
                resultingState = state.adjustReputation(5).adjustTomorrowsDemand(25),
                tone = LogTone.GOOD,
            )
        },
    )

    val reviewBombing = EventRule(
        id = "review_bombing",
        title = "One-star reviews",
        severity = Severity.MODERATE,
        cooldownDays = 4,
        unique = false,
        prerequisite = { state -> state.recentSatisfaction <= 40 },
        weight = { state -> ((50 - state.recentSatisfaction) / 10.0).toFloat() },
        resolve = { state, rng ->
            EventOutcome(
                ruleId = "review_bombing",
                description = "Last night's diners left a trail of one-star reviews. ${rng.pick(ONE_STAR_REVIEWS)}",
                resultingState = state.adjustReputation(-6),
                tone = LogTone.BAD,
            )
        },
    )

    val allergenScare = EventRule(
        id = "allergen_scare",
        title = "Allergen scare",
        severity = Severity.MODERATE,
        cooldownDays = 8,
        unique = false,
        prerequisite = { state -> state.menu.any { it.available && it.allergens.isNotEmpty() } },
        // Stressed, unreliable cooks are the ones who mix up the labels.
        weight = { state ->
            val cooks = state.activeEmployees.filter { it.role == Role.COOK }
            val error = if (cooks.isEmpty()) 0.5 else cooks.map { EmployeePerformance.errorProbability(it) }.average()
            (0.1 + error).toFloat()
        },
        resolve = { state, rng ->
            val dish = rng.pick(state.menu.filter { it.available && it.allergens.isNotEmpty() })
            val allergen = rng.pick(dish.allergens.toList()).name.lowercase()
            EventOutcome(
                ruleId = "allergen_scare",
                description = "A customer had a reaction to $allergen in the ${dish.name} — it wasn't on the label. You comped the table and apologised a lot.",
                resultingState = state.adjustReputation(-6).adjustCash(-80),
                tone = LogTone.BAD,
            )
        },
    )

    val celebrityVisit = EventRule(
        id = "celebrity_visit",
        title = "Celebrity sighting",
        severity = Severity.MODERATE,
        cooldownDays = 0,
        unique = true,
        prerequisite = { state -> state.restaurant.reputation >= 60 },
        weight = { 0.2f },
        resolve = { state, _ ->
            EventOutcome(
                ruleId = "celebrity_visit",
                description = "A minor celebrity (famous for a yogurt advert) ate here and posted about it. Tomorrow will be busy.",
                resultingState = state.adjustReputation(6).adjustTomorrowsDemand(40),
                tone = LogTone.GOOD,
            )
        },
    )

    val tourBus = EventRule(
        id = "tour_bus",
        title = "Tour bus",
        severity = Severity.MINOR,
        cooldownDays = 7,
        unique = false,
        prerequisite = { state -> state.restaurant.reputation >= 20 },
        weight = { 0.35f },
        resolve = { state, _ ->
            EventOutcome(
                ruleId = "tour_bus",
                description = "A tour bus broke down outside. A coachload of hungry pensioners is coming in tomorrow — stock up and staff up.",
                resultingState = state.adjustTomorrowsDemand(50),
                tone = LogTone.NEUTRAL,
            )
        },
    )

    val rainstorm = EventRule(
        id = "rainstorm",
        title = "Storm warning",
        severity = Severity.MINOR,
        cooldownDays = 5,
        unique = false,
        prerequisite = { true },
        weight = { 0.4f },
        resolve = { state, _ ->
            EventOutcome(
                ruleId = "rainstorm",
                description = "A truly biblical storm is forecast for tomorrow. Expect a quiet night — maybe don't buy fresh fish.",
                resultingState = state.adjustTomorrowsDemand(-35),
                tone = LogTone.BAD,
            )
        },
    )

    val all = listOf(foodCritic, viralReview, reviewBombing, allergenScare, celebrityVisit, tourBus, rainstorm)

    private val ONE_STAR_REVIEWS = listOf(
        "One just says \"why.\"",
        "One is a single photograph of a fork.",
        "One says \"my wife left me and honestly the soup was worse.\"",
        "One is written entirely in capital letters.",
    )
}
