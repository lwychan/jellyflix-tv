plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Rename APK output: jellyflix-app-0.1.0-<buildType>.apk
base { archivesName = "jellyflix-app-0.1.0" }

android {
    namespace = "com.jellyflix.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jellyflix.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLIENT_NAME", "\"Jellyflix\"")
        buildConfigField("String", "CLIENT_VERSION", "\"0.1.0\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Committed dev keystore — not a production identity, but lets CI
        // produce release-type APKs with consistent signatures between runs
        // so updates install in-place without an uninstall step.
        create("dev") {
            storeFile = rootProject.file("jellyflix-dev.keystore")
            storePassword = "jellyflix"
            keyAlias = "jellyflix"
            keyPassword = "jellyflix"
        }
        create("release") {
            val keystorePath = providers.environmentVariable("JELLYFLIX_KEYSTORE").orNull
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("JELLYFLIX_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("JELLYFLIX_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("JELLYFLIX_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Prefer the user-configured release signing config when the
            // keystore env vars are set; otherwise fall back to the committed
            // dev keystore so every CI release APK is installable.
            val hasKeystore = providers.environmentVariable("JELLYFLIX_KEYSTORE").orNull
                ?.let { file(it).exists() } ?: false
            signingConfig = if (hasKeystore) signingConfigs.getByName("release")
                            else signingConfigs.getByName("dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
        }
    }
}

dependencies {
    implementation(project(":plugin-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)

    implementation(libs.jellyfin.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
}
