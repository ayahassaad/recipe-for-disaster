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
 * Proves the Room wiring (entity, DAO, in-memory database) works end to
 * end, ahead of the real save/load schema design landing in Phase 4.
 *
 * This is an instrumented test (runs on a device/emulator via the
 * androidTest source set) rather than a Robolectric-based JVM test, so it
 * needs nothing beyond dependencies already declared for this module —
 * no new test framework is being introduced to get this running.
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
        assertNull(database.saveMetadataDao().get())
    }

    @Test
    fun upsertedSaveMetadataCanBeReadBack() = runTest {
        val dao = database.saveMetadataDao()
        val entity = SaveMetadataEntity(schemaVersion = 1, lastSavedAtEpochMillis = 1_700_000_000_000)

        dao.upsert(entity)

        assertEquals(entity, dao.get())
    }
}
