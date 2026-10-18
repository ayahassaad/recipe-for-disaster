package com.recipefordisaster.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.recipefordisaster.app.R
import com.recipefordisaster.domain.simulation.BackupResult
import com.recipefordisaster.domain.simulation.SaveBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Back up my game" (saves a file wherever the player chooses) and, if [showRestore], "Restore a backup" (reads
 * one back, asking first if it would replace a game in progress). [onRestored] runs after a game is put back.
 */
@Composable
fun BackupButtons(showRestore: Boolean, replacesGame: Boolean, onRestored: () -> Unit = {}) {
    val service = LocalBackupService.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }

    val saved = stringResource(R.string.backup_saved)
    val failedSave = stringResource(R.string.backup_save_failed)
    val nothing = stringResource(R.string.backup_nothing)
    val restored = stringResource(R.string.backup_restored)
    val notABackup = stringResource(R.string.backup_not_a_backup)
    val tooNew = stringResource(R.string.backup_too_new)
    val damaged = stringResource(R.string.backup_damaged)
    val tooBig = stringResource(R.string.backup_too_big)
    val unreadable = stringResource(R.string.backup_unreadable)

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val file = pending
        pending = null
        if (uri != null && file != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "w")?.use { it.write(file.text.toByteArray()) } != null }.getOrDefault(false)
                }
                message = if (ok) saved else failedSave
            }
        }
    }

    suspend fun restore(text: String) {
        message = when (val result = service.restore(text)) {
            is BackupResult.Restored -> restored.also { onRestored() }
            is BackupResult.Refused -> when (result.reason) {
                BackupResult.Reason.NOT_A_BACKUP -> notABackup
                BackupResult.Reason.TOO_NEW -> tooNew
                BackupResult.Reason.DAMAGED -> damaged
                BackupResult.Reason.TOO_BIG -> tooBig
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // Read at most a little more than a backup can be, so a huge file can't use up the phone's memory.
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            val buffer = CharArray(SaveBackup.MAX_CHARS + 1)
                            val read = reader.read(buffer)
                            if (read <= 0) "" else String(buffer, 0, read)
                        }
                    }.getOrNull()
                }
                when {
                    text == null -> message = unreadable
                    replacesGame -> confirmRestore = text
                    else -> restore(text)
                }
            }
        }
    }

    Column {
        TextButton(onClick = {
            scope.launch {
                val file = service.export()
                if (file == null) {
                    message = nothing
                } else {
                    pending = file
                    create.launch(file.name)
                }
            }
        }) { Text(stringResource(R.string.backup_button)) }
        if (showRestore) {
            TextButton(onClick = { open.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_restore_button)) }
        }
    }

    confirmRestore?.let { text ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = { Text(stringResource(R.string.backup_replace_body)) },
            confirmButton = { TextButton(onClick = { confirmRestore = null; scope.launch { restore(text) } }) { Text(stringResource(R.string.backup_replace_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(stringResource(R.string.backup_title)) },
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.costs_close)) } },
        )
    }
}
