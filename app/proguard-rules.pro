# Retrofit
-keepattributes Signature
-keepattributes Annotation
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.schoolsync.parent.data.model.** { *; }
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Firestore document models (needed for deserialization)
-keep class com.schoolsync.parent.data.model.firestore.** { *; }

# Strip debug/verbose logs in release builds (security: prevents PII leakage)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Razorpay checkout
-keepattributes *Annotation*,Signature
-keepclassmembers class * {
    @proguard.annotation.Keep *;
    @proguard.annotation.KeepClassMembers *;
}
-keep class com.razorpay.** { *; }
-keep class proguard.annotation.Keep
-keep class proguard.annotation.KeepClassMembers
-dontwarn com.razorpay.**
-dontwarn proguard.annotation.**

# Better obfuscation
-repackageclasses ''
