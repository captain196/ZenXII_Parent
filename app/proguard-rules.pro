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

# Retrofit — R8 full-mode consumer rules. Retrofit 2.9 does NOT bundle these
# (added upstream in 2.11). Without them, R8 full mode (AGP 8 default) strips
# the generic signature off suspend-function return types, so Retrofit reads
# the return type as a raw Class → "java.lang.Class cannot be cast to
# java.lang.reflect.ParameterizedType" at the first call (e.g. clear_must_change).
# Keep generic signature of Call, Response (stripped for non-kept types).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Suspend functions are wrapped in a Continuation whose type argument carries
# the response type — keep it so the signature survives.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Keep each @http-annotated service interface WITH its generic signature.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

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
