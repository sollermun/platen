import java.util.Properties
import java.io.FileInputStream

// Load signing secrets from a gitignored keystore.properties (never committed).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.sparklaw.platen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sparklaw.platen"
        minSdk = 24            // ML Kit doc scanner needs 21+; 24 keeps things simple
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"  // bump versionCode on EVERY Play upload; bump versionName per release
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            // Attach signing only when keystore.properties is present locally.
            signingConfig = if (keystorePropsFile.exists())
                signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // ML Kit document scanner: capture UI, edge detection, deskew, cleanup.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // PdfBox-Android for PDF assembly.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // ML Kit text recognition: bundled Latin model, on-device.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Coroutines bridge for ML Kit's Task<T> API.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
