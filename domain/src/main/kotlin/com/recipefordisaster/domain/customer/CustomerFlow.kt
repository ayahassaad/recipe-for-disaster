package com.recipefordisaster.domain.customer

import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.simulation.RandomSource
import kotlin.math.roundToInt

/**
 * Turns the restaurant's current reputation and capacity into a concrete
 * list of fictional customers for the day. Demand scaling with reputation
 * (rather than being flat or purely random) is what lets a bad review
 * streak actually starve a restaurant of traffic later, per the emergent
 * chain described in the product brief.
 *
 * Every name/preference here is generated, not drawn from any real person
 * — required by the project's data/privacy principles.
 */
object CustomerFlow {

    fun generateArrivals(restaurant: Restaurant, rng: RandomSource): List<Customer> {
        val demand = calculateDemand(restaurant, rng)
        return (0 until demand).map { generateCustomer(rng, it) }
    }

    internal fun calculateDemand(restaurant: Restaurant, rng: RandomSource): Int {
        val reputationFactor = restaurant.reputation.coerceIn(0, 100) / 100.0
        val baseline = (restaurant.capacity * reputationFactor).roundToInt()
        // +/- a small amount of day-to-day noise so demand isn't a pure
        // function of reputation alone — still seeded/reproducible via rng.
        val noise = rng.nextInt(5) - 2
        return (baseline + noise).coerceIn(0, restaurant.capacity)
    }

    private val FIRST_NAMES = listOf(
        "Bartholomew", "Petunia", "Cornelius", "Marguerite", "Ignatius",
        "Wilhelmina", "Aldric", "Bettina", "Ferdinand", "Ottoline",
    )
    private val LAST_NAMES = listOf(
        "Snoot", "Kettlewhistle", "Bramblegrit", "Fenwick", "Puddlecombe",
        "Vandermeer", "Oglethorpe", "Crumbly", "Wobbleton", "Higgs",
    )

    internal fun generateCustomer(rng: RandomSource, index: Int): Customer {
        val name = "${FIRST_NAMES[rng.nextInt(FIRST_NAMES.size)]} ${LAST_NAMES[rng.nextInt(LAST_NAMES.size)]}"
        val dietaryPool = DietaryRequirement.entries
        // Most customers have no dietary requirement at all; only
        // occasionally assign one, so allergen/preference handling
        // actually gets exercised without dominating every order.
        val dietary = if (rng.nextInt(5) == 0) setOf(dietaryPool[rng.nextInt(dietaryPool.size)]) else emptySet()

        return Customer(
            id = CustomerId("cust-${index}-${rng.nextInt(1_000_000)}"),
            name = name,
            patience = 10 + rng.nextInt(40), // minutes-equivalent tolerance before frustration sets in
            budget = (500 + rng.nextInt(3_000)).toLong(),
            preferences = emptySet(),
            dietaryRequirements = dietary,
            satisfaction = 70, // customers start neutral-to-positive; the visit moves this
            likelihoodOfReturning = 50,
            complaintTendency = rng.nextInt(100),
            reviewInfluence = rng.nextInt(100),
        )
    }
}
