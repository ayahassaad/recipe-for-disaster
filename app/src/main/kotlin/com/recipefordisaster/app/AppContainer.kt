package com.recipefordisaster.app

import android.content.Context
import androidx.room.Room
import com.recipefordisaster.data.db.GameDatabase
import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.RoomGameRepository
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.event.EventLibrary
import com.recipefordisaster.domain.simulation.DefaultDayTickEngine

/**
 * The app's hand-wired object graph (see [RecipeForDisasterApplication]).
 * Built once, for the life of the process.
 */
class AppContainer(context: Context) {

    private val database: GameDatabase = Room.databaseBuilder(
        context.applicationContext,
        GameDatabase::class.java,
        GameDatabase.DATABASE_NAME,
    ).build()

    val gameRepository: GameRepository = RoomGameRepository(database)

    // The Phase 6 event library (25 rules) with its tuned quiet-day weight.
    private val eventEngine: EventEngine = EventLibrary.engine()

    val dayTickEngine: DefaultDayTickEngine = DefaultDayTickEngine(eventEngine)
}
