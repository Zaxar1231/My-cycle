package io.github.zaxar1231.mycycle.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.BlockStyle
import io.github.zaxar1231.mycycle.core.domain.model.HomeBlock
import io.github.zaxar1231.mycycle.core.domain.model.HomeLayout
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Edits the home layout. Changes show at once in [layout] and are saved shortly after the last
 * one, so dragging or turning the colour wheel does not write on every frame.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HomeCustomizerViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    private val draft = MutableStateFlow<HomeLayout?>(null)
    val layout: StateFlow<HomeLayout?> = draft.asStateFlow()
    private var saved: HomeLayout? = null
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        viewModelScope.launch {
            saved = settings.settings.map { it.homeLayout }.first()
            draft.value = saved
            draft.filterNotNull().debounce(SAVE_DELAY_MILLIS).collect { save(it) }
        }
    }

    fun move(from: Int, to: Int) = edit { it.moved(from, to) }

    /** Moves by block, not by index, so fast drag events never move a neighbour by mistake. */
    fun moveBlock(block: HomeBlock, delta: Int) = edit { layout ->
        val from = layout.order.indexOf(block)
        layout.moved(from, from + delta)
    }

    /** Saves at once, before the screen closes, so the home screen shows the change without a jump. */
    fun flush() {
        val pending = draft.value ?: return
        viewModelScope.launch(NonCancellable) { save(pending) }
    }

    fun setStyle(block: HomeBlock, style: BlockStyle) = edit { it.withStyle(block, style) }

    fun reset() = edit { HomeLayout.DEFAULT }

    /**
     * Saves a change still waiting for the debounce when the screen closes. NonCancellable
     * replaces the scope's job, so the write is not cancelled with the cleared ViewModel.
     */
    override fun onCleared() {
        val pending = draft.value ?: return
        if (pending != saved) viewModelScope.launch(NonCancellable) { save(pending) }
    }

    private fun edit(change: (HomeLayout) -> HomeLayout) {
        draft.value = draft.value?.let(change)
    }

    private suspend fun save(layout: HomeLayout) {
        if (layout == saved) return
        try {
            settings.setHomeLayout(layout)
            saved = layout
            _saveFailed.value = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _saveFailed.value = true
        }
    }

    private companion object {
        const val SAVE_DELAY_MILLIS = 300L
    }
}
