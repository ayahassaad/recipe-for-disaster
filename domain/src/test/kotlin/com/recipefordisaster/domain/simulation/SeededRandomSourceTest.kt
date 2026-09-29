package com.recipefordisaster.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves two things at once: that the :domain test infrastructure is wired
 * correctly (plain JUnit, no Android runtime needed), and that the seeded
 * RNG requirement from section 9 of the project brief actually holds —
 * same seed in, same sequence out.
 */
class SeededRandomSourceTest {

    @Test
    fun `same seed produces identical sequence`() {
        val a = SeededRandomSource(seed = 42L)
        val b = SeededRandomSource(seed = 42L)

        val sequenceA = List(20) { a.nextInt(1000) }
        val sequenceB = List(20) { b.nextInt(1000) }

        assertEquals(sequenceA, sequenceB)
    }

    @Test
    fun `different seeds produce different sequences`() {
        val a = SeededRandomSource(seed = 1L)
        val b = SeededRandomSource(seed = 2L)

        val sequenceA = List(20) { a.nextInt(1000) }
        val sequenceB = List(20) { b.nextInt(1000) }

        assertEquals(false, sequenceA == sequenceB)
    }
}
