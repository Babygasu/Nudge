import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI run number makes every build's version higher than the last, so it installs as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.babygasu.nudge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.babygasu.nudge"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Two capability variants (docs/PLAY_PROTECT.md):
    //  - full: complete Nudge, including notification access and the floating bubble.
    //  - core: no notification listener, no overlay, no package visibility. Dashboard, manual and
    //    shared/pasted reminders, offline scheduling and privacy controls only. Installable from a
    //    browser even where Play Protect blocks sideloaded apps that declare notification access.
    flavorDimensions += "capability"
    productFlavors {
        create("full") {
            dimension = "capability"
            buildConfigField("boolean", "DETECTION_AVAILABLE", "true")
            buildConfigField("boolean", "BUBBLE_AVAILABLE", "true")
            manifestPlaceholders["appName"] = "Nudge"
        }
        create("core") {
            dimension = "capability"
            applicationIdSuffix = ".core"
            versionNameSuffix = "-core"
            buildConfigField("boolean", "DETECTION_AVAILABLE", "false")
            buildConfigField("boolean", "BUBBLE_AVAILABLE", "false")
            manifestPlaceholders["appName"] = "Nudge Core"
        }
    }

    // Debug builds use Android's standard auto-generated debug key. Stable signing is deferred
    // until stable releases (docs/SIGNING.md).

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            manifestPlaceholders["labelSuffix"] = " (dev)"
        }
        release {
            manifestPlaceholders["labelSuffix"] = ""
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:intelligence"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
