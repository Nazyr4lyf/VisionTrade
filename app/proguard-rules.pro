# VisionTrade ProGuard Rules

# Keep AndroidX classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep Room classes
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Kotlin classes
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Keep our classes
-keep class com.visiontrade.** { *; }

# Keep data classes
-keepclassmembers class com.visiontrade.** {
    <init>(...);
}

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
