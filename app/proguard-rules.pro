# kotlinx.serialization keeps generated serializers reachable via reflection on the
# companion; these rules are the ones recommended by the library.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.twisham.attendance.data.backup.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.twisham.attendance.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
