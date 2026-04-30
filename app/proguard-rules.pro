# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment to preserve line numbers in crash stack traces:
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------
# kotlinx.serialization
# Required: R8 strips the @Serializable companion and descriptor classes
# without these rules, causing SerializationException at runtime.
# -----------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable data classes in this app
-keep,includedescriptorclasses class com.example.toplutasima.**$$serializer { *; }
-keepclassmembers class com.example.toplutasima.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.toplutasima.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# -----------------------------------------------------------------------
# Retrofit 2
# -----------------------------------------------------------------------
-keepattributes Signature, Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Retrofit uses reflection on generic parameters; keep them
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# -----------------------------------------------------------------------
# OkHttp 3 / Okio
# -----------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# -----------------------------------------------------------------------
# Koin
# R8 can remove internal Koin factory lambda classes
# -----------------------------------------------------------------------
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# -----------------------------------------------------------------------
# Firebase / Google Play Services
# (firebase-android-sdk ships its own consumer ProGuard rules via AAR,
#  so no explicit rules are needed here — listed for documentation only)
# -----------------------------------------------------------------------

# -----------------------------------------------------------------------
# WorkManager
# (androidx.work ships its own consumer rules via AAR, but Room reflection
# for WorkDatabase_Impl sometimes gets stripped by R8)
# -----------------------------------------------------------------------
-keep class androidx.work.impl.WorkDatabase_Impl { *; }