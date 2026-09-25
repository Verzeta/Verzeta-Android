#
# The release buildType in app/build.gradle has minifyEnabled +
# shrinkResources on. R8 inlines / strips / renames aggressively;
# without explicit keep rules, kotlinx.serialization can't find the
# synthetic .Companion.serializer() shims at runtime and our
# protocol decode throws SerializationException at the first wire
# frame. The rules below are the minimum to keep the app working in
# a fully-minified build.

# ---- kotlinx.serialization ------------------------------------------------
#
# Every @Serializable class generates a synthetic `$$serializer` class
# with a `serializer()` accessor on its Companion. R8 doesn't see those
# as reachable from non-reflective entry points and strips them. Keep
# them explicitly. The two `-keepclassmembers` rules below cover both
# the synthetic serializer object and the Companion accessor.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations,RuntimeInvisibleParameterAnnotations
-keep,includedescriptorclasses class com.verzeta.android.**$$serializer { *; }
-keepclasseswithmembers class com.verzeta.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.verzeta.android.** {
    *** Companion;
}
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.verzeta.android.**

# kotlinx.serialization itself ships with a consumer-proguard file in
# the AAR so its runtime classes are already kept; we only need rules
# for OUR classes.

# ---- OkHttp ---------------------------------------------------------------
#
# OkHttp's AAR ships its own consumer-proguard rules. No project-side
# rules needed beyond what the library carries. Leaving an explicit
# note here so a future contributor doesn't reach for "okhttp keep
# rules" thinking we're missing them.

# ---- Compose --------------------------------------------------------------
#
# Compose runtime / compiler plugin handle their own minification
# safety. No project-side rules needed.

# ---- App entry points -----------------------------------------------------
#
# Keep the Application class + MainActivity so the Android framework
# can instantiate them by name from the manifest.
-keep class com.verzeta.android.VerzetaApplication { *; }
-keep class com.verzeta.android.MainActivity { *; }

# ---- ViewModels -----------------------------------------------------------
#
# AndroidViewModel subclasses are reflectively constructed by the
# Activity's `by viewModels()` delegate. R8's modern entrypoint
# tracking usually catches this but be explicit — losing the no-arg
# constructor would surface as a NoSuchMethodException at runtime
# only.
-keep public class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
-keep public class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ---- Debug logging --------------------------------------------------------
#
# Strip Log.d / Log.v calls from release builds — R8 will inline the
# Log class call sites away when the methods are marked @Removed.
# (Optional; uncomment if log volume becomes a concern in production.)
# -assumenosideeffects class android.util.Log {
#     public static int d(...);
#     public static int v(...);
# }
