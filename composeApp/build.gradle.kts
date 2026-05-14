import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.googleid)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)

            // Firebase Android (native) — Crashlytics only. Version resolved via BoM.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            // Compose
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            // Lifecycle
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Navigation
            implementation(libs.navigation.compose)

            // Koin
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Firebase (GitLive KMP SDK)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.common)

            // Image loading
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)

            // Image picker
            implementation(libs.peekaboo.image.picker)

            // Logging (KMP, Timber-like API)
            implementation(libs.napier)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // DateTime
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.danzucker.stitchpad"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.danzucker.stitchpad"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"683791063936-cbl4lksbu3cpbulak03vr70h4djtb5su.apps.googleusercontent.com\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Generates DebugTestAccounts.kt from debug-test-accounts.properties at build
// time. The properties file is gitignored; this task tolerates its absence by
// producing empty-string defaults (Switch-account buttons render but show a
// "creds not configured" Snackbar at runtime). See debug-menu-design.md.
val generateDebugTestAccounts by tasks.registering {
    val propsFile = layout.projectDirectory.file("debug-test-accounts.properties").asFile
    val outputDir = layout.buildDirectory.dir(
        "generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug"
    )

    inputs.files(propsFile).optional(true)
    outputs.dir(outputDir)

    doLast {
        val props = Properties().apply {
            if (propsFile.exists()) {
                propsFile.inputStream().use { load(it) }
            }
        }
        val folaEmail = props.getProperty("fola.email", "").trim()
        val folaPassword = props.getProperty("fola.password", "").trim()
        val gabbyEmail = props.getProperty("gabby.email", "").trim()
        val gabbyPassword = props.getProperty("gabby.password", "").trim()

        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("DebugTestAccounts.kt").writeText(
            """
            |// GENERATED — do not edit. Source: composeApp/debug-test-accounts.properties
            |package com.danzucker.stitchpad.core.debug
            |
            |internal object DebugTestAccounts {
            |    const val FOLA_EMAIL: String = ${'"'}$folaEmail${'"'}
            |    const val FOLA_PASSWORD: String = ${'"'}$folaPassword${'"'}
            |    const val GABBY_EMAIL: String = ${'"'}$gabbyEmail${'"'}
            |    const val GABBY_PASSWORD: String = ${'"'}$gabbyPassword${'"'}
            |
            |    val isConfigured: Boolean
            |        get() = FOLA_EMAIL.isNotBlank() && FOLA_PASSWORD.isNotBlank() &&
            |                GABBY_EMAIL.isNotBlank() && GABBY_PASSWORD.isNotBlank()
            |}
            |
            """.trimMargin()
        )
    }
}

// Wire the generated source into commonMain so DebugTestAccounts is visible
// from commonMain code as if it were authored there.
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateDebugTestAccounts)
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

kover {
    reports {
        filters {
            excludes {
                packages(
                    "com.danzucker.stitchpad.di",
                    "com.danzucker.stitchpad.navigation",
                    "com.danzucker.stitchpad.ui.theme",
                    "com.danzucker.stitchpad.core.data.dto"
                )
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
    }
}
