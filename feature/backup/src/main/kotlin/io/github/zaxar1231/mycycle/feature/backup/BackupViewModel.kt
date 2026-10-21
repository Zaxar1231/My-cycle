package io.github.zaxar1231.mycycle.feature.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRepository
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRules
import io.github.zaxar1231.mycycle.core.domain.backup.RestoreResult
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.stats.CycleStatisticsCalculator
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate
import javax.inject.Inject

/** The outcome of the last action, shown under the buttons. */
enum class DataMessage {
    REPORT_SAVED, CSV_SAVED, BACKUP_SAVED, RESTORED, WRONG_PASSWORD, NOT_A_BACKUP, TOO_LARGE, NOT_SAVED, FAILED
}

data class DataUiState(val busy: Boolean = false, val message: DataMessage? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupRepository,
    private val observePrediction: ObserveCyclePredictionUseCase,
    private val dayLogs: DayLogRepository,
    private val medications: MedicationRepository,
    private val settings: SettingsRepository,
    private val statistics: CycleStatisticsCalculator,
    private val dates: DateProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    /**
     * The backup password while the file picker is open. Kept here, not in the saved state: it
     * survives a rotation but not the app's process.
     */
    private var pendingPassword: CharArray? = null

    /** For the suggested file names. */
    fun today(): LocalDate = dates.today()

    fun holdPassword(password: CharArray) {
        pendingPassword?.fill('\u0000')
        pendingPassword = password
    }

    /** The file picker returned: save to [uri], or remove it when the password is gone; null means cancelled. */
    fun backupPicked(uri: Uri?) {
        val password = pendingPassword
        pendingPassword = null
        when {
            uri != null && password != null -> saveBackup(uri, password)
            uri != null -> {
                viewModelScope.launch { deleteQuietly(uri) }
                _state.value = DataUiState(message = DataMessage.NOT_SAVED)
            }
            else -> password?.fill('\u0000')
        }
    }

    /** No app on the phone can create or open documents. */
    fun pickerMissing() {
        pendingPassword?.fill('\u0000')
        pendingPassword = null
        _state.value = DataUiState(message = DataMessage.FAILED)
    }

    override fun onCleared() {
        pendingPassword?.fill('\u0000')
    }

    fun saveReport(uri: Uri) = perform(DataMessage.REPORT_SAVED, uri) {
        val today = dates.today()
        val (prediction, cycles) = observePrediction.withCycles(today).first()
        val history = statistics.history(prediction, cycles, today)
        val logs = dayLogs.observeAll().first()
        val user = settings.settings.first()
        val data = ReportData(
            generated = today,
            summary = statistics.summary(history),
            cycles = history.sortedByDescending { it.start },
            symptoms = statistics.logs(prediction, history, logs).symptomFrequency,
            medications = medications.observeMedications().first(),
            usualCycleLength = user.usualCycleLength,
            usualPeriodLength = user.usualPeriodLength,
        )
        write(uri) { ReportPdf.write(context, data, it) }
    }

    fun saveCsv(uri: Uri, includeNotes: Boolean, includeSex: Boolean) = perform(DataMessage.CSV_SAVED, uri) {
        val csv = backups.exportCsv(includeNotes, includeSex)
        write(uri) { it.write(csv.toByteArray(Charsets.UTF_8)) }
    }

    private fun saveBackup(uri: Uri, password: CharArray) = perform(DataMessage.BACKUP_SAVED, uri, onBusy = { password.fill('\u0000') }) {
        val file = try {
            backups.exportEncrypted(password)
        } finally {
            password.fill('\u0000')
        }
        try {
            write(uri) { it.write(file) }
        } finally {
            file.fill(0)
        }
    }

    fun restore(uri: Uri, password: CharArray) {
        if (_state.value.busy) {
            password.fill('\u0000')
            return
        }
        _state.value = DataUiState(busy = true)
        // Not cancelled when the screen is left: a restore is one short transaction the user started.
        viewModelScope.launch {
            val message = try {
                withContext(NonCancellable + Dispatchers.IO) {
                    when (val file = context.contentResolver.openInputStream(uri)?.use(::readBackup)) {
                        null -> DataMessage.FAILED
                        ReadResult.NotABackup -> DataMessage.NOT_A_BACKUP
                        ReadResult.TooLarge -> DataMessage.TOO_LARGE
                        is ReadResult.Bytes -> when (backups.restoreEncrypted(file.bytes, password)) {
                            RestoreResult.RESTORED -> DataMessage.RESTORED
                            RestoreResult.WRONG_PASSWORD -> DataMessage.WRONG_PASSWORD
                            RestoreResult.NOT_A_BACKUP -> DataMessage.NOT_A_BACKUP
                            RestoreResult.FAILED -> DataMessage.FAILED
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DataMessage.FAILED
            } finally {
                password.fill('\u0000')
            }
            _state.value = DataUiState(message = message)
        }
    }

    private sealed interface ReadResult {
        class Bytes(val bytes: ByteArray) : ReadResult
        data object NotABackup : ReadResult
        data object TooLarge : ReadResult
    }

    /** Checks the first bytes before reading on, and stops at [BackupRules.MAX_BACKUP_BYTES]. */
    private fun readBackup(input: InputStream): ReadResult {
        val start = input.readUpTo(MAGIC_BYTES)
        if (!backups.looksLikeBackup(start)) return ReadResult.NotABackup
        val rest = input.readUpTo((BackupRules.MAX_BACKUP_BYTES - MAGIC_BYTES + 1).toInt())
        if (start.size + rest.size > BackupRules.MAX_BACKUP_BYTES) return ReadResult.TooLarge
        return ReadResult.Bytes(start + rest)
    }

    /** InputStream.readNBytes needs API 33. */
    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private suspend fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        // "wt" truncates, so a shorter file does not keep the tail of an older one; some providers know only "w".
        val resolver = context.contentResolver
        val out = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: IllegalArgumentException) {
            resolver.openOutputStream(uri, "w")
        } catch (e: java.io.FileNotFoundException) {
            resolver.openOutputStream(uri, "w")
        } ?: error("cannot open $uri")
        out.use(block)
    }

    private suspend fun deleteQuietly(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            // The provider may not allow deleting; the file then stays, empty or partial.
        }
    }

    /** Runs [action]; when it fails, the half-written file at [target] is removed. */
    private fun perform(success: DataMessage, target: Uri, onBusy: () -> Unit = {}, action: suspend () -> Unit) {
        if (_state.value.busy) {
            onBusy()
            viewModelScope.launch { deleteQuietly(target) }
            return
        }
        _state.value = DataUiState(busy = true)
        // Not cancelled when the screen is left, so no empty or half-written file stays behind.
        viewModelScope.launch {
            val message = try {
                withContext(NonCancellable) { action() }
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) { deleteQuietly(target) }
                DataMessage.FAILED
            }
            _state.value = DataUiState(message = message)
        }
    }

    private companion object {
        const val MAGIC_BYTES = 4
        const val BUFFER_BYTES = 64 * 1024
    }
}
