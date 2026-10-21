plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The server address and its public (anon) key come from CI secrets. Without them the build has
// no account features at all; nothing is hardcoded in the repository.
val supabaseUrl: String = providers.environmentVariable("SUPABASE_URL").orElse("").get().trim().trimEnd('/')
val supabaseAnonKey: String = providers.environmentVariable("SUPABASE_ANON_KEY").orElse("").get().trim()

android {
    namespace = "io.github.zaxar1231.mycycle.core.sync"
    buildFeatures.buildConfig = true
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
