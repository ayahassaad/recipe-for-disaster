package com.recipefordisaster.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Room wiring itself (entity, DAO, in-memory database) works —
 * separate from [com.recipefordisaster.data.repository.RoomGameRepositoryTest],
 * which covers the higher-level save/load/corruption behavior built on top
 * of this.
 */
@RunWith(AndroidJUnit4::class)
class GameDatabaseTest {

    private lateinit var database: GameDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun noSaveExistsInAFreshDatabase() = runTest {
        assertNull(database.saveDao().get())
    }

    @Test
    fun upsertedSaveCanBeReadBack() = runTest {
        val dao = database.saveDao()
        val entity = SaveEntity(schemaVersion = 1, stateJson = """{"hello":"world"}""", savedAtEpochMillis = 1_700_000_000_000)

        dao.upsert(entity)

        assertEquals(entity, dao.get())
    }

    @Test
    fun upsertingTwiceReplacesRatherThanDuplicates() = runTest {
        val dao = database.saveDao()
        dao.upsert(SaveEntity(schemaVersion = 1, stateJson = "first", savedAtEpochMillis = 1))
        dao.upsert(SaveEntity(schemaVersion = 1, stateJson = "second", savedAtEpochMillis = 2))

        assertEquals("second", dao.get()?.stateJson)
    }

    @Test
    fun clearRemovesTheSave() = runTest {
        val dao = database.saveDao()
        dao.upsert(SaveEntity(schemaVersion = 1, stateJson = "data", savedAtEpochMillis = 1))

        dao.clear()

        assertNull(dao.get())
    }
}
