package com.recipefordisaster.app.ui.backup

import androidx.compose.runtime.staticCompositionLocalOf
import com.recipefordisaster.data.repository.GameRepository
import com.recipefordisaster.data.repository.SaveLoadResult
import com.recipefordisaster.domain.simulation.BackupResult
import com.recipefordisaster.domain.simulation.SaveBackups

/** A game turned into a file's worth of text, with the name to give it. */
data class BackupFile(val name: String, val text: String)

/**
 * Backing the game up to a file and putting a backup back. Works on the saved game (as of the last morning),
 * so what's in the file is what Continue would pick up.
 */
class BackupService(private val repository: GameRepository) {

    /** The saved game as a backup, or null if there's nothing saved to back up. */
    suspend fun export(): BackupFile? {
        val saved = repository.load() as? SaveLoadResult.Success ?: return null
        val text = SaveBackups.encode(saved.state, repository.bestRun(), System.currentTimeMillis())
        return BackupFile(SaveBackups.fileName(saved.state), text)
    }

    /** Puts a backup's game in place of the saved one (any night in progress goes), if the text is a good backup. */
    suspend fun restore(text: String): BackupResult {
        val result = SaveBackups.decode(text)
        if (result is BackupResult.Restored) {
            repository.save(result.game)
            repository.clearNight()
            result.bestRun?.let { repository.recordRun(it) }
        }
        return result
    }
}

/** Where the screens find the backup service (null in previews, where there's nothing to back up). */
val LocalBackupService = staticCompositionLocalOf<BackupService?> { null }
