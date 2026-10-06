package com.recipefordisaster.data.repository

import com.recipefordisaster.data.db.GameDatabase
import com.recipefordisaster.data.db.SaveEntity
import com.recipefordisaster.domain.service.NightInProgress
import com.recipefordisaster.domain.simulation.GameState
import com.recipefordisaster.domain.simulation.GameStateJson
import com.recipefordisaster.domain.simulation.GameStateValidator
import kotlinx.serialization.SerializationException

/**
 * The boundary between the simulation engine and persistence. `:domain`
 * code should only ever talk to an interface like this — never to Room
 * directly — so the engine can be unit-tested against an in-memory fake
 * without pulling in Android at all.
 */
interface GameRepository {
    suspend fun save(state: GameState)
    suspend fun load(): SaveLoadResult
    suspend fun hasExistingSave(): Boolean
    suspend fun clear()

    /** Saves the night being played, so closing the app mid-service doesn't lose it. */
    suspend fun saveNight(night: NightInProgress)

    /** The night that was being played when the app closed, or null (also if it can't be read). */
    suspend fun loadNight(): NightInProgress?
    suspend fun clearNight()
}

/**
 * A save can fail to come back for reasons worth distinguishing in the UI:
 * there's genuinely nothing saved yet (new player) versus something *was*
 * saved but can't be trusted (section 13: never crash on corrupted save
 * data — surface it and let the player start fresh instead).
 */
sealed interface SaveLoadResult {
    data class Success(val state: GameState) : SaveLoadResult
    data object NoSaveFound : SaveLoadResult
    data class Corrupted(val reason: String) : SaveLoadResult
}

class RoomGameRepository(
    private val database: GameDatabase,
) : GameRepository {

    override suspend fun save(state: GameState) {
        val entity = SaveEntity(
            schemaVersion = GameDatabase.CURRENT_SAVE_SCHEMA_VERSION,
            stateJson = GameStateJson.instance.encodeToString(GameState.serializer(), state),
            savedAtEpochMillis = System.currentTimeMillis(),
        )
        database.saveDao().upsert(entity)
    }

    override suspend fun load(): SaveLoadResult {
        val entity = database.saveDao().get() ?: return SaveLoadResult.NoSaveFound

        if (entity.schemaVersion != GameDatabase.CURRENT_SAVE_SCHEMA_VERSION) {
            return SaveLoadResult.Corrupted(
                "save was written with schema version ${entity.schemaVersion}, " +
                    "this build expects ${GameDatabase.CURRENT_SAVE_SCHEMA_VERSION}",
            )
        }

        val state = try {
            GameStateJson.instance.decodeFromString(GameState.serializer(), entity.stateJson)
        } catch (e: SerializationException) {
            // kotlinx.serialization only ever produces a typed decoding
            // failure or a value — there's no arbitrary-class instantiation
            // risk here the way there would be with Java's native
            // deserialization, so catching this specific exception is
            // sufficient to make a malformed blob safe rather than fatal.
            return SaveLoadResult.Corrupted("malformed save data: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return SaveLoadResult.Corrupted("malformed save data: ${e.message}")
        }

        val problems = GameStateValidator.validate(state)
        if (problems.isNotEmpty()) {
            return SaveLoadResult.Corrupted("save failed validation: ${problems.joinToString("; ")}")
        }

        return SaveLoadResult.Success(state)
    }

    override suspend fun hasExistingSave(): Boolean =
        database.saveDao().get() != null

    override suspend fun clear() {
        database.saveDao().clear()
        database.saveDao().clearNight()
    }

    override suspend fun saveNight(night: NightInProgress) {
        database.saveDao().upsert(
            SaveEntity(
                id = 1,
                schemaVersion = GameDatabase.CURRENT_SAVE_SCHEMA_VERSION,
                stateJson = GameStateJson.instance.encodeToString(NightInProgress.serializer(), night),
                savedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun loadNight(): NightInProgress? {
        val entity = database.saveDao().getNight() ?: return null
        if (entity.schemaVersion != GameDatabase.CURRENT_SAVE_SCHEMA_VERSION) return null
        // A night that can't be read is simply dropped: the player goes back to that morning instead.
        return try {
            GameStateJson.instance.decodeFromString(NightInProgress.serializer(), entity.stateJson)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override suspend fun clearNight() {
        database.saveDao().clearNight()
    }
}
