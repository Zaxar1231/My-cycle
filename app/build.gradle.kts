import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Version: CI passes -PappVersionName=X.Y.Z, local builds use version.properties.
val baseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME")
val appVersionName = (findProperty("appVersionName") as String?)?.removePrefix("v") ?: baseVersion

// 1.2.3 -> 1_002_003, so every new version always gets a higher versionCode.
fun versionCodeOf(name: String): Int {
    val (major, minor, patch) = name.split(".").map { it.toInt() }
    require(minor < 1000 && patch < 1000) { "minor and patch must be below 1000: $name" }
    return major * 1_000_000 + minor * 1_000 + patch
}

// Release signing comes from CI secrets (see README). Without them the release build is unsigned.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.zaxar1231.mycycle"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.zaxar1231.mycycle"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions.unitTests.all { it.useJUnitPlatform() }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // F-Droid/IzzyOnDroid friendly: no Google-encrypted dependency metadata blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:update"))
    implementation(project(":core:sync"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:wellness"))
    implementation(project(":feature:security"))
    implementation(project(":feature:account"))
    implementation(project(":feature:partner"))
    implementation(project(":feature:reminders"))
    implementation(project(":feature:daylog"))
    implementation(project(":feature:pet"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
