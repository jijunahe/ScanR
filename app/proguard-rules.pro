# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ARCore classes
-keep class com.google.ar.core.** { *; }
-keep class com.google.ar.** { *; }

# Keep Filament classes
-keep class com.google.android.filament.** { *; }

# Keep Sceneview classes
-keep class io.github.sceneview.** { *; }

# Keep model classes for mesh data
-keepclassmembers class com.scanr.app.model.** { *; }

# Keep AR session classes
-keepclassmembers class com.scanr.app.ar.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Compose
-keepclassmembers class androidx.compose.** { *; }
-keep class androidx.compose.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
