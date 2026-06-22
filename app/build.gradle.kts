plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sparklaw.platen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sparklaw.platen"
        minSdk = 24            // ML Kit doc scanner needs 21+; 24 keeps things simple
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // ML Kit document scanner: capture UI, edge detection, deskew, cleanup.
    // No camera permission required (uses Google Play services' camera).
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // PdfBox-Android: gives us CCITTFactory.createFromImage(doc, Bitmap)
    // for true 1-bit Group 4 (fax) compressed PDF pages.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // ML Kit text recognition: bundled Latin model, on-device, no model download needed.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Coroutines bridge for ML Kit's Task<T> API.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
