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
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.googleid)
            implementation(libs.installreferrer)
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)

            // Media3 / ExoPlayer — looping background video on the welcome screen
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)

            // Firebase Android (native) — Crashlytics + Messaging. Version resolved via BoM.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.messaging)
            implementation(libs.kotlinx.coroutines.play.services)
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
            implementation(libs.firebase.functions)
            implementation(libs.firebase.analytics)

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
            implementation(libs.turbine)
        }
    }
}

// Derives Android versionCode from git commit count so it monotonically
// increases. NOTE: Gradle's configuration cache is enabled for this project,
// so this value is captured at config time and baked into the cache. A new
// `git commit` does NOT invalidate the cache, so locally-cached builds may
// serve a stale versionCode. The release lanes in fastlane/Fastfile pass
// `--no-configuration-cache` to gradle to guarantee a fresh value at upload
// time. For local debug builds the slight staleness is harmless.
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    workingDir = rootDir
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

android {
    namespace = "com.danzucker.stitchpad"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.danzucker.stitchpad"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = gitCommitCount
        versionName = "1.1.0"

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
    val releaseSigningProps = Properties().apply {
        val propsFile = layout.projectDirectory.file("release-signing.properties").asFile
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = releaseSigningProps.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = releaseSigningProps.getProperty("storePassword")
                keyAlias = releaseSigningProps.getProperty("keyAlias")
                keyPassword = releaseSigningProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 code shrinking + resource shrinking. The bulk of the APK was
            // unshrunk dex (materialIconsExtended alone ships thousands of unused
            // icons). Keep rules live in proguard-rules.pro — the kotlinx.serialization
            // rules there are load-bearing for GitLive Firestore DTOs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
//
// SECURITY: real credentials are ONLY embedded when the opt-in Gradle property
// is explicitly set. Add the following to your global ~/.gradle/gradle.properties
// (NOT to the repo) for local debug testing:
//   debugMenu.embedTestAccountCreds=true
// Never set this property when building release — it would ship the real
// credentials as inspectable string constants in the APK/IPA.
val generateDebugTestAccounts by tasks.registering {
    val propsFile = layout.projectDirectory.file("debug-test-accounts.properties").asFile
    val outputDir = layout.buildDirectory.dir(
        "generated/debugTestAccounts/commonMain/kotlin/com/danzucker/stitchpad/core/debug"
    )

    // Resolve the opt-in property at configuration time so the value is
    // serializable by Gradle's configuration cache and available inside doLast.
    val embedCreds: Boolean =
        providers.gradleProperty("debugMenu.embedTestAccountCreds").orNull == "true"

    inputs.files(propsFile).optional(true)
    // Make Gradle's up-to-date check aware that toggling the opt-in property
    // must invalidate the task output.
    inputs.property("embedCreds", embedCreds.toString())
    outputs.dir(outputDir)

    doLast {
        val props = Properties().apply {
            if (embedCreds && propsFile.exists()) {
                propsFile.inputStream().use { load(it) }
            }
        }
        fun String.escapeForKotlinLiteral(): String =
            this.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

        val folaEmail = props.getProperty("fola.email", "").trim().escapeForKotlinLiteral()
        val folaPassword = props.getProperty("fola.password", "").trim().escapeForKotlinLiteral()
        val gabbyEmail = props.getProperty("gabby.email", "").trim().escapeForKotlinLiteral()
        val gabbyPassword = props.getProperty("gabby.password", "").trim().escapeForKotlinLiteral()

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

// Safety guard: abort any release build if the creds opt-in property is set.
// This prevents accidentally shipping real test-account credentials in production
// artifacts even if a developer forgets to unset the property.
val embedCredsForReleaseCheck: Boolean =
    providers.gradleProperty("debugMenu.embedTestAccountCreds").orNull == "true"
gradle.taskGraph.whenReady {
    if (embedCredsForReleaseCheck) {
        val releaseTaskPatterns = listOf(
            "assembleRelease",
            "bundleRelease",
            Regex("linkRelease.*Framework.*")
        )
        val hasReleaseTask = allTasks.any { task ->
            releaseTaskPatterns.any { pattern ->
                when (pattern) {
                    is String -> task.name == pattern
                    is Regex -> pattern.matches(task.name)
                    else -> false
                }
            }
        }
        if (hasReleaseTask) {
            error(
                "Build aborted: debugMenu.embedTestAccountCreds=true is set but this " +
                    "build graph contains a release task. Remove the property from " +
                    "~/.gradle/gradle.properties before building release."
            )
        }
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
