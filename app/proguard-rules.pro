# Apache POI rules
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemasMicrosoftComOfficeOffice.** { *; }
-keep class schemasMicrosoftComOfficeExcel.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn javax.xml.**
-dontwarn com.graphbuilder.**
-dontwarn java.awt.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep our Room entities (used reflectively by Room)
-keep class bulksms.test.mark.data.entity.** { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }

# Reflection-using classes — keep the BroadcastReceivers and Service
-keep class bulksms.test.mark.service.** { *; }
