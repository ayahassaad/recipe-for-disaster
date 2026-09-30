package com.recipefordisaster.app

import android.content.Context
import androidx.room.Room
import com.recipefordisaster.data.db.GameDatabase
import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.RoomGameRepository
import com.recipefordisaster.domain.event.EventEngine
import com.recipefordisaster.domain.simulation.DayTickEngine
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

    // No rules yet — the 15-25-event library is Phase 6 content. An
    // EventEngine with an empty rule list is valid and simply never fires,
    // which is the correct state until that content exists.
    private val eventEngine = EventEngine(rules = emptyList())

    val dayTickEngine: DayTickEngine = DefaultDayTickEngine(eventEngine)
}
