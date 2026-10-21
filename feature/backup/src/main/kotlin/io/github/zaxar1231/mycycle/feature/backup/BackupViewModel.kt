package io.github.zaxar1231.mycycle.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRepository
import io.github.zaxar1231.mycycle.core.domain.backup.RestoreResult
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.stats.CycleStatisticsCalculator
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The outcome of the last action, shown under the buttons. */
enum class DataMessage {
    REPORT_SAVED, CSV_SAVED, BACKUP_SAVED, RESTORED, WRONG_PASSWORD, NOT_A_BACKUP, FAILED
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

    fun saveReport(uri: Uri) = perform(DataMessage.REPORT_SAVED) {
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

    fun saveCsv(uri: Uri) = perform(DataMessage.CSV_SAVED) {
        val csv = backups.exportCsv()
        write(uri) { it.write(csv.toByteArray(Charsets.UTF_8)) }
    }

    fun saveBackup(uri: Uri, password: CharArray) = perform(DataMessage.BACKUP_SAVED) {
        val file = try {
            backups.exportEncrypted(password)
        } finally {
            password.fill('\u0000')
        }
        write(uri) { it.write(file) }
    }

    fun restore(uri: Uri, password: CharArray) {
        if (_state.value.busy) return
        _state.value = DataUiState(busy = true)
        viewModelScope.launch {
            val message = try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    DataMessage.FAILED
                } else {
                    when (backups.restoreEncrypted(bytes, password)) {
                        RestoreResult.RESTORED -> DataMessage.RESTORED
                        RestoreResult.WRONG_PASSWORD -> DataMessage.WRONG_PASSWORD
                        RestoreResult.NOT_A_BACKUP -> DataMessage.NOT_A_BACKUP
                        RestoreResult.FAILED -> DataMessage.FAILED
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

    private suspend fun write(uri: Uri, block: (java.io.OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        // "wt" truncates, so a shorter file does not keep the tail of an older one.
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open $uri")
        out.use(block)
    }

    private fun perform(success: DataMessage, action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = DataUiState(busy = true)
        viewModelScope.launch {
            val message = try {
                action()
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DataMessage.FAILED
            }
            _state.value = DataUiState(message = message)
        }
    }
}
