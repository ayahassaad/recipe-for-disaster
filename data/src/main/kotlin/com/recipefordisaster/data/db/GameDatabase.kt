package com.recipefordisaster.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * The real save schema (Phase 4), replacing the Phase 2 placeholder. One
 * row, always `id = 0`, per the single-save-slot decision from Phase 0 —
 * `stateJson` is a full serialized `GameState` (see
 * [com.recipefordisaster.domain.simulation.GameStateJson]). Storing it as
 * one JSON blob rather than a fully relational schema was a deliberate
 * choice: this game never needs to SQL-query into save data, so the
 * simplicity of "one row, one document" outweighs proper normalization
 * here. See docs/architecture.md for the full reasoning.
 *
 * `schemaVersion` is the save format's own version, independent of Room's
 * database `version` below — it lets [com.recipefordisaster.data.repository.RoomGameRepository]
 * recognize an old save's JSON shape without needing a SQL migration for
 * every change to what's inside the blob.
 */
@Entity(tableName = "saves")
data class SaveEntity(
    @PrimaryKey val id: Int = 0,
    val schemaVersion: Int,
    val stateJson: String,
    val savedAtEpochMillis: Long,
)

@Dao
interface SaveDao {
    @Upsert
    suspend fun upsert(entity: SaveEntity)

    @Query("SELECT * FROM saves WHERE id = 0")
    suspend fun get(): SaveEntity?

    @Query("DELETE FROM saves WHERE id = 0")
    suspend fun clear()
}

@Database(
    entities = [SaveEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun saveDao(): SaveDao

    companion object {
        const val DATABASE_NAME = "recipe_for_disaster.db"
        const val CURRENT_SAVE_SCHEMA_VERSION = 1
    }
}
