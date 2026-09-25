-keep class com.masstamilan.app.** { *; }
-keepclassmembers class * {
    @com.google.dagger.* *;
}
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*
