package io.github.zaxar1231.mycycle.feature.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.usecase.RewardPetUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A finished session feeds the pet: coins and XP once a day. */
@HiltViewModel
class BreathingViewModel @Inject constructor(private val rewardPet: RewardPetUseCase) : ViewModel() {
    fun sessionFinished() {
        viewModelScope.launch {
            try {
                rewardPet.breathingFinished()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No reward this time; the session itself is unaffected.
            }
        }
    }
}
