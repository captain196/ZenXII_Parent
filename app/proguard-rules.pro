# Retrofit — official R8 / R8-full-mode keep rules.
# Retrofit reflects on generic signatures + annotations to build service
# methods. Under R8 full mode (default on AGP 8) generic signatures are
# stripped from classes that aren't explicitly kept — which breaks Kotlin
# `suspend` service methods with:
#   ClassCastException: Class cannot be cast to ParameterizedType
# (a suspend fun compiles to a Continuation<T> param whose type arg Retrofit
# must read). The Continuation / Call / Response keeps below are the fix.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
# Retain service-method type args + suspend Continuation generic signature.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Gson
-keep class com.schoolsync.parent.data.model.** { *; }
-keepattributes *Annotation*

# API request/response DTOs (Retrofit + Gson serialize/deserialize BY FIELD NAME —
# these have no @SerializedName, so R8 must NOT rename their fields or the
# parent_create_order / parent_verify_payment JSON contract breaks in the
# minified release build (debug is unaffected because it isn't minified).
-keep class com.schoolsync.parent.data.remote.** { *; }

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
