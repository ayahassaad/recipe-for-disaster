package com.recipefordisaster.domain.customer

import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerFlowTest {

    private fun restaurantWith(reputation: Int, capacity: Int = 20) = Restaurant(
        cash = 10_000,
        reputation = reputation,
        cleanliness = 80,
        capacity = capacity,
        level = 1,
        operatingCosts = OperatingCosts(rentPerDay = 100, utilitiesPerDay = 20, miscPerDay = 10),
        currentDay = 1,
        status = RestaurantStatus.OPEN,
    )

    @Test
    fun `higher reputation yields more demand on average`() {
        val low = restaurantWith(reputation = 10)
        val high = restaurantWith(reputation = 90)
        val rng = SeededRandomSource(seed = 7)

        val lowDemandSamples = (1..50).map { CustomerFlow.calculateDemand(low, rng) }
        val highDemandSamples = (1..50).map { CustomerFlow.calculateDemand(high, rng) }

        assertTrue(highDemandSamples.average() > lowDemandSamples.average())
    }

    @Test
    fun `demand never exceeds capacity or drops below zero`() {
        val restaurant = restaurantWith(reputation = 100, capacity = 5)
        val rng = SeededRandomSource(seed = 1)

        repeat(100) {
            val demand = CustomerFlow.calculateDemand(restaurant, rng)
            assertTrue(demand in 0..restaurant.capacity)
        }
    }

    @Test
    fun `same seed produces the same generated customer`() {
        val a = CustomerFlow.generateCustomer(SeededRandomSource(seed = 42), index = 0)
        val b = CustomerFlow.generateCustomer(SeededRandomSource(seed = 42), index = 0)

        assertEquals(a, b)
    }
}
