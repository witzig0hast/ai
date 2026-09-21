# Add project specific ProGuard rules here.
# Minification is disabled by default for both build types in this scaffold
# (see app/build.gradle.kts), so these rules are currently unused but kept
# as a starting point for when release minification is turned on.

-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class de.hastnetwork.jarvis.**$$serializer { *; }
-keepclassmembers class de.hastnetwork.jarvis.** {
    *** Companion;
}
-keepclasseswithmembers class de.hastnetwork.jarvis.** {
    kotlinx.serialization.KSerializer serializer(...);
}
