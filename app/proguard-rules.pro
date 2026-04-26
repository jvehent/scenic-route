-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn kotlinx.coroutines.flow.**

-keep class com.senikroute.data.** { *; }
