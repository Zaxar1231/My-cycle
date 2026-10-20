package io.github.zaxar1231.mycycle.feature.calendar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(dateProvider: DateProvider) : ViewModel() {
    val today: LocalDate = dateProvider.today()
}
