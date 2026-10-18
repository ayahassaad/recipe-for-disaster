package com.recipefordisaster.domain.simulation

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * A copy of a game kept in a file the player can keep, move to another phone, or put back if the game is ever
 * lost: the restaurant as of its last morning, and the longest run so far. Nothing in it is private to the
 * app, and reading one back is checked as strictly as reading a save (a backup from anywhere is just text).
 */
@Serializable
data class SaveBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val savedAtEpochMillis: Long,
    val game: GameState,
    val bestRun: BestRun? = null,
) {
    companion object {
        const val FORMAT = "recipe-for-disaster-backup"

        /** What this build writes; it reads this and anything older. */
        const val VERSION = 1

        /** A backup bigger than this isn't one: it's refused without being read. */
        const val MAX_CHARS = 4_000_000
    }
}

/** Reading a backup back can work, or fail for a reason worth telling the player. */
sealed interface BackupResult {
    data class Restored(val game: GameState, val bestRun: BestRun?) : BackupResult
    data class Refused(val reason: Reason, val detail: String = "") : BackupResult

    enum class Reason { NOT_A_BACKUP, TOO_NEW, DAMAGED, TOO_BIG }
}

object SaveBackups {

    /** Like the game's own JSON, but writing every field, so the format and version are always in the file. */
    private val json = kotlinx.serialization.json.Json {
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(game: GameState, bestRun: BestRun?, savedAtEpochMillis: Long): String =
        json.encodeToString(SaveBackup.serializer(), SaveBackup(savedAtEpochMillis = savedAtEpochMillis, game = game, bestRun = bestRun))

    /** The name to give the file: the restaurant and how far it got, with nothing in it a file system dislikes. */
    fun fileName(game: GameState): String {
        val name = game.restaurant.displayName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).ifEmpty { "restaurant" }
        return "recipe-for-disaster-$name-day-${game.day}.json"
    }

    fun decode(text: String): BackupResult {
        if (text.length > SaveBackup.MAX_CHARS) return BackupResult.Refused(BackupResult.Reason.TOO_BIG)
        val backup = try {
            json.decodeFromString(SaveBackup.serializer(), text)
        } catch (e: SerializationException) {
            return BackupResult.Refused(BackupResult.Reason.NOT_A_BACKUP, e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            return BackupResult.Refused(BackupResult.Reason.NOT_A_BACKUP, e.message.orEmpty())
        }
        if (backup.format != SaveBackup.FORMAT) return BackupResult.Refused(BackupResult.Reason.NOT_A_BACKUP)
        if (backup.version > SaveBackup.VERSION) return BackupResult.Refused(BackupResult.Reason.TOO_NEW)
        val problems = GameStateValidator.validate(backup.game)
        if (problems.isNotEmpty()) return BackupResult.Refused(BackupResult.Reason.DAMAGED, problems.joinToString("; "))
        return BackupResult.Restored(backup.game, backup.bestRun)
    }
}
