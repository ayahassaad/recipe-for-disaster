package com.recipefordisaster.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * Placeholder entity proving the Room wiring (entity -> DAO -> database ->
 * migrations) works end to end. This is NOT the real save-game schema —
 * that's Phase 4 (Persistence) work, where the actual GameState projection,
 * its migrations, and save/load semantics get designed deliberately rather
 * than backed into here.
 *
 * Schema starts at version 1 on purpose (see [GameDatabase]) so migrations
 * in Phase 4 build on a versioned schema from day one instead of retrofitting
 * versioning after the fact.
 */
@Entity(tableName = "save_metadata")
data class SaveMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val schemaVersion: Int,
    val lastSavedAtEpochMillis: Long,
)

@Dao
interface SaveMetadataDao {
    @Upsert
    suspend fun upsert(entity: SaveMetadataEntity)

    @Query("SELECT * FROM save_metadata WHERE id = 0")
    suspend fun get(): SaveMetadataEntity?
}

@Database(
    entities = [SaveMetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun saveMetadataDao(): SaveMetadataDao

    companion object {
        const val DATABASE_NAME = "recipe_for_disaster.db"
    }
}
