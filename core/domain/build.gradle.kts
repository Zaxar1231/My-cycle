plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.javax.inject)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
