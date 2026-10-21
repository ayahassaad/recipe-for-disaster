package com.recipefordisaster.domain.employee

import com.recipefordisaster.domain.simulation.NewGameFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine
import com.recipefordisaster.domain.simulation.PlayerDecisions
import com.recipefordisaster.domain.simulation.SeededRandomSource
import org.junit.Test

class StaffStoriesTest {

    private val person = NewGameFactory.create(seed = 6L).employees.first()
        .copy(name = "Petunia Fenwick", experienceDays = 0, personalityTraits = setOf(PersonalityTrait.LOYAL))

    @Test
    fun `everyone has a story of four chapters that uses their first name`() {
        PersonalityTrait.entries.forEach { trait ->
            val chapters = StaffStories.chapters(person.copy(personalityTraits = setOf(trait)))
            assertEquals(StaffStories.UNLOCK_AT.size, chapters.size)
            assertTrue(chapters.none { "{name}" in it })
            assertTrue(chapters.first().contains("Petunia"))
        }
        // Someone with no particular character still has one.
        assertEquals(4, StaffStories.chapters(person.copy(personalityTraits = emptySet())).size)
    }

    @Test
    fun `the story is told bit by bit as they work for you`() {
        assertTrue(StaffStories.told(person).isEmpty())
        assertEquals(1, StaffStories.told(person.copy(experienceDays = 3)).size)
        assertEquals(2, StaffStories.told(person.copy(experienceDays = 10)).size)
        assertEquals(4, StaffStories.told(person.copy(experienceDays = 200)).size)
        assertTrue(StaffStories.hasMore(person.copy(experienceDays = 10)))
        assertFalse(StaffStories.hasMore(person.copy(experienceDays = 25)))
    }

    @Test
    fun `a new chapter turns up on exactly the day they reach it`() {
        val before = person.copy(experienceDays = 7)
        assertEquals(StaffStories.chapters(person)[1], StaffStories.newChapter(before, before.copy(experienceDays = 8)))
        assertNull(StaffStories.newChapter(before, before.copy(experienceDays = 7)))
        assertNull(StaffStories.newChapter(before.copy(experienceDays = 8), before.copy(experienceDays = 9)))
    }

    @Test
    fun `different characters have different stories`() {
        val a = StaffStories.chapters(person.copy(personalityTraits = setOf(PersonalityTrait.GOSSIP)))
        val b = StaffStories.chapters(person.copy(personalityTraits = setOf(PersonalityTrait.ANXIOUS)))
        assertTrue(a != b)
    }

    @Test
    fun `the morning log tells you the new chapter`() {
        val game = NewGameFactory.create(seed = 6L)
        val cook = game.employees.first { it.status == EmployeeStatus.ACTIVE }.copy(experienceDays = 2, personalityTraits = setOf(PersonalityTrait.GOSSIP))
        val state = game.copy(employees = game.employees.map { if (it.id == cook.id) cook else it })
        val result = DefaultDayTickEngine(EventEngine(emptyList())).advanceDay(state, PlayerDecisions(), SeededRandomSource(1L))
        val told = StaffStories.chapters(cook).first()
        assertTrue(result.newState.log.any { it.message == told })
    }
}
