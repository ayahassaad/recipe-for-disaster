package com.recipefordisaster.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveBackupTest {

    private val game = NewGameFactory.create(seed = 21L).let { it.copy(day = 14, loan = 300, restaurant = it.restaurant.copy(name = "The Leaky Ladle!")) }

    @Test
    fun `a backup puts the same game back, with the best run`() {
        val text = SaveBackups.encode(game, BestRun(22, "Ladle"), savedAtEpochMillis = 1_000L)
        val result = SaveBackups.decode(text)

        assertTrue(result is BackupResult.Restored)
        result as BackupResult.Restored
        assertEquals(game, result.game)
        assertEquals(BestRun(22, "Ladle"), result.bestRun)
    }

    @Test
    fun `the file is named after the restaurant, in a file-safe way`() {
        assertEquals("recipe-for-disaster-the-leaky-ladle-day-14.json", SaveBackups.fileName(game))
        val odd = game.copy(restaurant = game.restaurant.copy(name = "////"))
        assertEquals("recipe-for-disaster-restaurant-day-14.json", SaveBackups.fileName(odd))
    }

    @Test
    fun `text that isn't a backup is refused, not crashed on`() {
        listOf("", "not json", "{}", "[1,2,3]", "{\"format\":\"x\"}", "null").forEach { junk ->
            val result = SaveBackups.decode(junk)
            assertTrue("'$junk' gave $result", result is BackupResult.Refused)
        }
    }

    @Test
    fun `a file in some other format is refused even if it reads like one`() {
        val text = SaveBackups.encode(game, null, 1L).replace(SaveBackup.FORMAT, "something-else")
        val result = SaveBackups.decode(text)
        assertEquals(BackupResult.Reason.NOT_A_BACKUP, (result as BackupResult.Refused).reason)
    }

    @Test
    fun `a backup from a newer game is refused, with a reason`() {
        val text = SaveBackups.encode(game, null, 1L).replace("\"version\":${SaveBackup.VERSION}", "\"version\":${SaveBackup.VERSION + 1}")
        val result = SaveBackups.decode(text)
        assertEquals(BackupResult.Reason.TOO_NEW, (result as BackupResult.Refused).reason)
    }

    @Test
    fun `a game that fails the usual checks is refused as damaged`() {
        val broken = game.copy(day = -5)
        val result = SaveBackups.decode(SaveBackups.encode(broken, null, 1L))
        assertEquals(BackupResult.Reason.DAMAGED, (result as BackupResult.Refused).reason)
    }

    @Test
    fun `something enormous is refused without being read`() {
        val result = SaveBackups.decode("x".repeat(SaveBackup.MAX_CHARS + 1))
        assertEquals(BackupResult.Reason.TOO_BIG, (result as BackupResult.Refused).reason)
    }

    @Test
    fun `unknown extra fields from a newer build don't stop it loading`() {
        val text = SaveBackups.encode(game, null, 1L).replaceFirst("{", "{\"somethingNew\":123,")
        assertTrue(SaveBackups.decode(text) is BackupResult.Restored)
    }
}
