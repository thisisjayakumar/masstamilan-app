# MasstamilanApp R8 rules — modeled on linqmusic (known-working release
# with minifyEnabled=true) plus coverage for this app's extra deps
# (WorkManager, DataStore/protobuf, Coil, OkHttp, Navigation, Guava).
# Without these, release builds crash immediately on launch while
# debug builds (minify off) work fine.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# App code
-keep class com.masstamilan.app.** { *; }

# --- Hilt / Dagger (was: @com.google.dagger.* which matches nothing;
# real annotations live in dagger.* / javax.inject.*) ---
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends javax.inject.Provider { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keepclasseswithmembers class * {
    @dagger.Provides <methods>;
}
-dontwarn javax.annotation.**
-dontwarn dagger.**
-dontwarn javax.inject.**

# --- Room Database (missing entirely before; causes
# "cannot find implementation for AppDatabase" in release) ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- WorkManager + Startup (lazy-init still needs these when shrunk) ---
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

# --- DataStore + protobuf ---
-keep class androidx.datastore.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# --- Coil / OkHttp / okio ---
-keep class coil.** { *; }
-dontwarn coil.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# --- Navigation Compose ---
-keep class androidx.navigation.** { *; }

# --- Guava (used by PlaybackManager via Futures.immediateFuture) ---
-dontwarn com.google.common.**
-keep class com.google.common.util.concurrent.** { *; }

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**
