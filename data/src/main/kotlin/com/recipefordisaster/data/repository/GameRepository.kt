package com.recipefordisaster.data.repository

import com.recipefordisaster.data.db.GameDatabase

/**
 * The boundary between the simulation engine and persistence. `:domain`
 * code should only ever talk to an interface like this — never to Room
 * directly — so the engine can be unit-tested against an in-memory fake
 * without pulling in Android at all.
 *
 * This is a placeholder shape for Phase 2. The real contract (save/load a
 * full GameState, list saves, delete a save) is Phase 4 (Persistence) work,
 * once the save schema itself has been designed rather than assumed here.
 */
interface GameRepository {
    suspend fun hasExistingSave(): Boolean
}

class RoomGameRepository(
    private val database: GameDatabase,
) : GameRepository {
    override suspend fun hasExistingSave(): Boolean =
        database.saveMetadataDao().get() != null
}
