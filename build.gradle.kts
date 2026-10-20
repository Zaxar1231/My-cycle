// AGP 9 has built-in Kotlin support; the Kotlin plugin is declared here only to pin its version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
