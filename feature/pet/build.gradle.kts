plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.zaxar1231.mycycle.feature.pet"
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
}
