# ---- pdfbox-android (Tom Roush) ----
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# ---- ML Kit (document scanner + text recognition) ----
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.mlkit.**

# ---- Kotlin coroutines ----
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- App entrypoints ----
-keep class com.sparklaw.platen.** { *; }
