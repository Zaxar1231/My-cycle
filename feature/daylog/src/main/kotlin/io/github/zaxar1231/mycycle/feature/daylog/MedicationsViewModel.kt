package io.github.zaxar1231.mycycle.feature.daylog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MedicationsViewModel @Inject constructor(private val repository: MedicationRepository) : ViewModel() {
    val medications: StateFlow<List<Medication>> = repository.observeMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun save(id: Long, name: String, dosage: String, reminder: LocalTime?) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            repository.upsert(Medication(id = id, name = cleanName, dosage = dosage.trim().ifEmpty { null }, reminderTime = reminder))
        }
    }

    /** Old logs keep pointing at the medication, so it is archived instead of deleted. */
    fun archive(medication: Medication) {
        viewModelScope.launch { repository.upsert(medication.copy(isActive = false)) }
    }

    fun restore(medication: Medication) {
        viewModelScope.launch { repository.upsert(medication.copy(isActive = true)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
