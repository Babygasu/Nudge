import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: no Android dependencies, so the detection logic is fast to
// unit-test and can never touch storage, network or logs by itself.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
