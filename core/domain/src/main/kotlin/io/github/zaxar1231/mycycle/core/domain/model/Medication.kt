package io.github.zaxar1231.mycycle.core.domain.model

import java.time.LocalTime

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String? = null,
    val reminderTime: LocalTime? = null,
    val isActive: Boolean = true,
)
