# Piano Companion ProGuard Rules

# --- Gson ---
-keep class com.pianocompanion.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- Compose ---
-dontwarn androidx.compose.**

# --- Keep data classes for Gson serialization ---
-keep class com.pianocompanion.data.model.SessionRecord { *; }
-keep class com.pianocompanion.data.model.Score { *; }
-keep class com.pianocompanion.data.model.ScoreNote { *; }
-keep class com.pianocompanion.data.sync.SyncData { *; }
-keep class com.pianocompanion.following.DtwConfig { *; }

# --- Keep enums ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
