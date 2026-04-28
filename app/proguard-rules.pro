# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signature (needed for reflection)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# ═══════════════════════════════════════════════════════════════════════════
# RETROFIT & OKHTTP
# ═══════════════════════════════════════════════════════════════════════════
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Keep generic type information for Retrofit service calls
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ═══════════════════════════════════════════════════════════════════════════
# GSON
# ═══════════════════════════════════════════════════════════════════════════
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep data model classes - CRITICAL for API responses
# Disable obfuscation completely for these packages
-keep,allowobfuscation class com.example.handheldapp.data.model.** { *; }
-keep,allowobfuscation class com.example.handheldapp.data.api.** { *; }
-keep,allowobfuscation class com.example.handheldapp.data.local.entity.** { *; }

# Keep all data classes and their members
-keepclassmembers class com.example.handheldapp.data.model.** { *; }
-keepclassmembers class com.example.handheldapp.data.api.** { *; }
-keepclassmembers class com.example.handheldapp.data.local.entity.** { *; }

# Keep constructors for data classes
-keepclassmembers class * {
    public <init>(...);
}

# Gson specific classes
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.** { *; }

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ═══════════════════════════════════════════════════════════════════════════
# HILT / DAGGER
# ═══════════════════════════════════════════════════════════════════════════
-dontwarn com.google.errorprone.annotations.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Hilt generated classes
-keep class **_HiltModules { *; }
-keep class **_HiltComponents { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ═══════════════════════════════════════════════════════════════════════════
# ROOM DATABASE
# ═══════════════════════════════════════════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ═══════════════════════════════════════════════════════════════════════════
# KOTLIN COROUTINES
# ═══════════════════════════════════════════════════════════════════════════
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ═══════════════════════════════════════════════════════════════════════════
# CRASHLYTICS / LOGGING (if used)
# ═══════════════════════════════════════════════════════════════════════════
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ═══════════════════════════════════════════════════════════════════════════
# ANDROID COMPONENTS
# ═══════════════════════════════════════════════════════════════════════════
-keep class androidx.lifecycle.** { *; }
-keep class androidx.datastore.** { *; }

# ═══════════════════════════════════════════════════════════════════════════
# ML KIT BARCODE SCANNING
# ═══════════════════════════════════════════════════════════════════════════
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.android.gms.**

# ═══════════════════════════════════════════════════════════════════════════
# PARCELIZE
# ═══════════════════════════════════════════════════════════════════════════
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ═══════════════════════════════════════════════════════════════════════════
# GENERAL ANDROID
# ═══════════════════════════════════════════════════════════════════════════
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}