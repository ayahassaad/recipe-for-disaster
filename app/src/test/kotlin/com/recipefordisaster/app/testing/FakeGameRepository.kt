package com.recipefordisaster.app.testing

import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.SaveLoadResult
import com.recipefordisaster.domain.service.NightInProgress
import com.recipefordisaster.domain.simulation.GameState

/**
 * An in-memory [GameRepository] for ViewModel unit tests. Real persistence
 * (Room, corrupted-save handling, schema versioning) is already covered by
 * `:data`'s own instrumented tests (Phase 4) — this fake exists purely so
 * `:app`'s ViewModels can be tested on the plain JVM without an Android
 * device, per docs/architecture.md's testing table.
 */
class FakeGameRepository(private var stored: GameState? = null) : GameRepository {

    var saveCount = 0
        private set

    override suspend fun save(state: GameState) {
        stored = state
        saveCount++
    }

    override suspend fun load(): SaveLoadResult {
        val state = stored ?: return SaveLoadResult.NoSaveFound
        return SaveLoadResult.Success(state)
    }

    override suspend fun hasExistingSave(): Boolean = stored != null

    override suspend fun clear() {
        stored = null
        night = null
    }

    var night: NightInProgress? = null
        private set

    override suspend fun saveNight(night: NightInProgress) {
        this.night = night
    }

    override suspend fun loadNight(): NightInProgress? = night

    override suspend fun clearNight() {
        night = null
    }
}
