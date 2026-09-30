package com.recipefordisaster.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.recipefordisaster.data.db.GameDatabase
import com.recipefordisaster.data.db.SaveEntity
import com.recipefordisaster.domain.economy.Ledger
import com.recipefordisaster.domain.inventory.InventoryState
import com.recipefordisaster.domain.restaurant.OperatingCosts
import com.recipefordisaster.domain.restaurant.Restaurant
import com.recipefordisaster.domain.restaurant.RestaurantStatus
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.GameStateJson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This is the test file that matters most for section 13's "never crash on
 * corrupted save data" requirement: every one of these bad-data scenarios
 * must come back as [SaveLoadResult.Corrupted], never an unhandled
 * exception that would crash the app on startup.
 */
@RunWith(AndroidJUnit4::class)
class RoomGameRepositoryTest {

    private lateinit var database: GameDatabase
    private lateinit var repository: RoomGameRepository

    private fun sampleState() = GameState(
        seed = 1, day = 3,
        restaurant = Restaurant(
            cash = 4_000, reputation = 55, cleanliness = 75, capacity = 15, level = 1,
            operatingCosts = OperatingCosts(150, 30, 10), currentDay = 3, status = RestaurantStatus.OPEN,
        ),
        employees = emptyList(), customersPresent = emptyList(),
        inventory = InventoryState(emptyMap(), 200.0, emptyList()),
        menu = emptyList(), equipment = emptyList(), ledger = Ledger(emptyList()), log = emptyList(),
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java).build()
        repository = RoomGameRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun loadingWithNoExistingSaveReturnsNoSaveFound() = runTest {
        assertEquals(SaveLoadResult.NoSaveFound, repository.load())
    }

    @Test
    fun aSavedStateLoadsBackIdentical() = runTest {
        val original = sampleState()
        repository.save(original)

        val result = repository.load()

        assertTrue(result is SaveLoadResult.Success)
        assertEquals(original, (result as SaveLoadResult.Success).state)
    }

    @Test
    fun hasExistingSaveReflectsWhetherSomethingWasSaved() = runTest {
        assertEquals(false, repository.hasExistingSave())
        repository.save(sampleState())
        assertEquals(true, repository.hasExistingSave())
    }

    @Test
    fun clearRemovesASave() = runTest {
        repository.save(sampleState())
        repository.clear()
        assertEquals(SaveLoadResult.NoSaveFound, repository.load())
    }

    @Test
    fun malformedJsonIsReportedAsCorruptedNotThrown() = runTest {
        database.saveDao().upsert(
            SaveEntity(schemaVersion = GameDatabase.CURRENT_SAVE_SCHEMA_VERSION, stateJson = "{ this is not valid json", savedAtEpochMillis = 1),
        )

        val result = repository.load()

        assertTrue(result is SaveLoadResult.Corrupted)
    }

    @Test
    fun aMismatchedSchemaVersionIsReportedAsCorrupted() = runTest {
        database.saveDao().upsert(
            SaveEntity(schemaVersion = GameDatabase.CURRENT_SAVE_SCHEMA_VERSION + 99, stateJson = "{}", savedAtEpochMillis = 1),
        )

        val result = repository.load()

        assertTrue(result is SaveLoadResult.Corrupted)
    }

    @Test
    fun aStructurallyValidButOutOfRangeStateFailsValidation() = runTest {
        // Valid JSON, right schema version, but reputation is out of the
        // 0-100 range GameStateValidator enforces — this is the "bit rot,
        // not a parse error" case validation exists specifically to catch.
        val corrupted = sampleState().copy(restaurant = sampleState().restaurant.copy(reputation = 9999))
        val json = GameStateJson.instance.encodeToString(GameState.serializer(), corrupted)
        database.saveDao().upsert(
            SaveEntity(schemaVersion = GameDatabase.CURRENT_SAVE_SCHEMA_VERSION, stateJson = json, savedAtEpochMillis = 1),
        )

        val result = repository.load()

        assertTrue(result is SaveLoadResult.Corrupted)
    }
}
