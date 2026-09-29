package com.recipefordisaster.domain.simulation

import kotlin.random.Random

/**
 * The single entry point for randomness anywhere in the simulation engine.
 * Nothing in `:domain` is permitted to call `kotlin.random.Random` (or any
 * other RNG) directly — everything goes through this interface, which is
 * what makes a seeded run fully reproducible: same seed + same inputs to
 * [com.recipefordisaster.domain.simulation.DayTickEngine] always produces
 * the same sequence of outcomes.
 */
interface RandomSource {
    fun nextFloat(): Float
    fun nextInt(bound: Int): Int
}

/** Default production implementation, backed by a seeded [kotlin.random.Random]. */
class SeededRandomSource(seed: Long) : RandomSource {
    private val random = Random(seed)

    override fun nextFloat(): Float = random.nextFloat()
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
