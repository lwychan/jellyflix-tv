# Notflix TV — R8/Proguard rules.

# --- Keep source attributes + generic signatures ---
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,Exceptions,EnclosingMethod

# --- Kotlin metadata (needed for reflection-based libraries) ---
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# --- kotlinx.serialization ---
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class ** {
    static **$$serializer INSTANCE;
}
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** { *; }
-dontnote kotlinx.serialization.SerializersKt
-keep class kotlinx.serialization.** { *; }

# --- Jellyfin SDK (reflection over model classes) ---
-keep class org.jellyfin.sdk.model.api.** { *; }
-keep class org.jellyfin.sdk.model.** { *; }
-keep interface org.jellyfin.sdk.** { *; }

# --- Hilt / Dagger generated components ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep class hilt_aggregated_deps.** { *; }
-keep class **_HiltModules { *; }
-keep class **_HiltModules$** { *; }
-keep class **_Factory { *; }
-keep class **_Factory$** { *; }
-keep class **_MembersInjector { *; }
-keep class **_MembersInjector$** { *; }

# --- Compose runtime keeps ---
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.tv.material3.** { *; }
-dontwarn androidx.compose.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Plugin API — third-party plugin APKs DexClassLoad these types by name ---
-keep class com.notflix.plugin.** { *; }
-keep interface com.notflix.plugin.** { *; }

# --- OkHttp / Ktor / Coil ---
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# --- Timber ---
-dontwarn org.jetbrains.annotations.**

# --- Navigation Compose routes ---
-keep class androidx.navigation.** { *; }
