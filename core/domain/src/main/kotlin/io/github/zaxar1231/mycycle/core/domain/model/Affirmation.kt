package io.github.zaxar1231.mycycle.core.domain.model

data class Affirmation(
    val id: Long = 0,
    val text: String,
    val isFavorite: Boolean = false,
)
