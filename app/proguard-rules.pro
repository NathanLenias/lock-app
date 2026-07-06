# Lock ProGuard rules

# Room — keep entities and converters intact
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Data model classes — defensive: keep names intact even if backup logic
# is refactored to use reflection-based serialization later.
-keep class com.nathanb.lock.data.model.** { *; }

# Kotlin metadata (needed for data class component functions, copy(), etc.)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep class kotlin.Metadata { *; }
