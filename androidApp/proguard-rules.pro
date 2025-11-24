# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep SSH-related classes
-keep class com.jcraft.jsch.** { *; }

# Keep SQLDelight generated classes
-keep class com.sshtunnel.db.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes for serialization
-keep,includedescriptorclasses class com.sshtunnel.**$$serializer { *; }
-keepclassmembers class com.sshtunnel.** {
    *** Companion;
}
-keepclasseswithmembers class com.sshtunnel.** {
    kotlinx.serialization.KSerializer serializer(...);
}
