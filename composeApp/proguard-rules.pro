# ============================================================================
# StitchPad R8 / ProGuard keep rules (release builds only)
#
# Most libraries in this stack ship their own consumer rules (Compose, Coil,
# Ktor/OkHttp, Media3, Firebase native, Koin). The rules below cover the gaps
# that are NOT auto-handled and would otherwise break silently at RUNTIME:
#   - kotlinx.serialization (every GitLive Firestore DTO round-trips through it)
#   - readable Crashlytics stack traces
# Keep this file minimal; add a rule only when a release smoke test proves a
# need, and comment WHY.
# ============================================================================

# ---------------------------------------------------------------------------
# kotlinx.serialization
# GitLive firebase-kotlin-sdk encodes/decodes every Firestore DTO via the
# generated $serializer. R8 full mode (default on AGP 8) can strip the
# Companion / serializer() lookup paths. These are the canonical rules from
# the kotlinx.serialization README, adapted to our DTO package.
#
# NOTE on field names: serial names are compile-time string constants baked
# into each $serializer, so obfuscating the Kotlin property does NOT change
# the Firestore field name. Round-trips stay stable without @SerialName.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

# Keep the Companion of every @Serializable class (serializer lookup entrypoint).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep serializer() on companion objects (default and named) of serializable types.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep INSTANCE.serializer() of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Belt-and-braces for our own DTOs: keep the synthetic $serializer classes and
# their descriptors intact regardless of how they are reached.
-keep,includedescriptorclasses class com.danzucker.stitchpad.**$$serializer { *; }
-keepclassmembers class com.danzucker.stitchpad.** {
    *** Companion;
}

# ---------------------------------------------------------------------------
# Firebase Crashlytics — keep stack traces de-obfuscatable + readable.
# The firebase-crashlytics-gradle plugin auto-uploads the R8 mapping file on
# release, but we still want source/line attributes preserved.
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ---------------------------------------------------------------------------
# Coroutines — R8-safe, but silence the optional debug-probes reflection warning.
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
