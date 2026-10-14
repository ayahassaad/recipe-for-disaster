package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.decision.DecisionApplier
import com.recipefordisaster.domain.simulation.NewGameFactory
import com.recipefordisaster.domain.simulation.PlayerDecisions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {

    private val start = NewGameFactory.create(seed = 4L)

    @Test
    fun `training makes someone better at the job, for a price`() {
        val cook = start.employees.first()
        val after = DecisionApplier.apply(start, PlayerDecisions(training = setOf(cook.id)))
        assertEquals((cook.skill + Training.SKILL_BOOST).coerceAtMost(Training.MAX_SKILL), after.state.employees.first { it.id == cook.id }.skill)
        assertEquals(Training.cost(cook), after.spending.staffing)
    }

    @Test
    fun `nobody can be trained past the best, or without the money`() {
        val cook = start.employees.first()
        assertFalse(Training.canTrain(cook.copy(skill = Training.MAX_SKILL)))
        val broke = start.copy(restaurant = start.restaurant.copy(cash = 0))
        val after = DecisionApplier.apply(broke, PlayerDecisions(training = setOf(cook.id)))
        assertEquals(cook.skill, after.state.employees.first { it.id == cook.id }.skill)
        assertTrue(Training.cost(cook.copy(skill = 80)) > Training.cost(cook.copy(skill = 40)))
    }
}
