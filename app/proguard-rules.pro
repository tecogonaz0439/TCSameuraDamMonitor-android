# R8用の最小限の保持ルール。
# Hilt/Room/Ktor/Glance は生成コードとリフレクションを併用するため、注釈とシグネチャ情報を保持する。
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,Signature,InnerClasses,EnclosingMethod

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * implements androidx.work.ListenableWorker { *; }
-dontwarn dagger.hilt.**
-dontwarn hilt_aggregated_deps.**

# Room
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Ktor CIO / kotlinx.serialization
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**

# Glance AppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-dontwarn androidx.glance.**

# Arrow
-dontwarn arrow.**
