# ProGuard rules for PulseCoach
# ProGuard shrinks and obfuscates release builds. These rules tell it what NOT to touch.

# Keep Polar SDK classes (they use reflection internally)
-keep class com.polar.** { *; }

# Keep Room entities (Room uses reflection to map columns to fields)
-keep class com.pulsecoach.data.** { *; }
