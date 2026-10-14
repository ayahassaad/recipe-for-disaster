package com.recipefordisaster.domain.goal

import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyGoalsTest {

    private val start = NewGameFactory.create(seed = 8L)

    @Test
    fun `goals start in the second week, a new one every seven days`() {
        assertFalse(WeeklyGoals.startsAWeek(1))
        assertTrue(WeeklyGoals.startsAWeek(8))
        assertFalse(WeeklyGoals.startsAWeek(9))
        assertTrue(WeeklyGoals.startsAWeek(15))
    }

    @Test
    fun `each week asks for something different`() {
        val kinds = listOf(8, 15, 22).map { WeeklyGoals.newGoal(start.copy(day = it)).kind }
        assertEquals(GoalKind.entries.toSet(), kinds.toSet())
    }

    @Test
    fun `reaching the goal pays the reward once`() {
        val goal = WeeklyGoal(GoalKind.TAKINGS, target = 500, reward = 200, startDay = 8)
        val (halfway, none) = WeeklyGoals.afterDay(goal, day = 8, takings = 300, fed = 10, reputation = 50)
        assertEquals(0L, none)
        val (done, paid) = WeeklyGoals.afterDay(halfway, day = 9, takings = 300, fed = 10, reputation = 50)
        assertEquals(200L, paid)
        assertTrue(done.met)
        assertEquals(0L, WeeklyGoals.afterDay(done, day = 10, takings = 300, fed = 10, reputation = 50).second)
    }

    @Test
    fun `a goal not reached by the end of the week is missed`() {
        val goal = WeeklyGoal(GoalKind.STARS, target = 80, reward = 200, startDay = 8)
        val (after, paid) = WeeklyGoals.afterDay(goal, day = 15, takings = 0, fed = 0, reputation = 90)
        assertEquals(0L, paid)
        assertFalse(after.met)
    }
}
