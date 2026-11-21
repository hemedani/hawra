# Hawraa ProGuard/R8 rules.

# kotlinx.serialization: keep generated serializers for @Serializable models.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**