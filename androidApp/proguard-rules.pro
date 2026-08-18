# BYD Stats ProGuard Rules
-keepattributes *Annotation*
-keep class com.ggpark.bydstats.** { *; }
-keep class androidx.room.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
