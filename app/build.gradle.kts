plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = System.getenv("MIRA_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("MIRA_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("MIRA_KEY_ALIAS")
val releaseKeyPassword = System.getenv("MIRA_KEY_PASSWORD")
val releaseSigningConfigured = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.naveensmk.mira"

    // Required for the About screen to read version/application metadata.
    buildFeatures {
        buildConfig = true
    }
    compileSdk = 36

    defaultConfig {
        applicationId = "com.naveensmk.mira"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.9.0"
    }

    signingConfigs {
        create("miraRelease") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Keep minification off until LiteRT-LM native/reflection paths are verified.
            isMinifyEnabled = false
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("miraRelease")
        }
    }

    // Keep Java and Kotlin bytecode targets aligned for Android builds.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

// Type-safe Kotlin compiler target configuration (modern Kotlin Gradle plugin DSL).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    // Bundled on-device Latin-script OCR for user-invoked screen snapshots.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Never produce an accidentally unsigned release artifact. Debug builds remain available
// without publisher credentials, while release bundle/APK tasks require the private key.
tasks.configureEach {
    if (name == "bundleRelease" || name == "assembleRelease") {
        doFirst {
            if (!releaseSigningConfigured) {
                throw GradleException("Release artifacts must be signed. Configure MIRA_KEYSTORE_PATH, MIRA_KEYSTORE_PASSWORD, MIRA_KEY_ALIAS, and MIRA_KEY_PASSWORD in your local environment or GitHub Actions secrets.")
            }
        }
    }
}
