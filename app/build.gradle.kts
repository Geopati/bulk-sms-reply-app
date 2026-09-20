plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.georgeapp.bulksmsreply"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.georgeapp.bulksmsreply"
        minSdk = 26
        targetSdk = 34
        // Kept in step with AppInfo.kt's APP_VERSION (Round N = version 1.N).
        versionCode = 3
        versionName = "1.6.0"
    }

    signingConfigs {
        // Pinned debug key so every build (Android Studio or GitHub
        // Actions) is signed identically. Without this, each CI run
        // generated its own throwaway debug key, and Android refuses to
        // install an "update" APK signed with a different key than the
        // one already on the phone - forcing an uninstall each time.
        // This is a debug-only key with Android's own well-known debug
        // credentials (never used for a release build), so committing it
        // is safe and is the standard fix for this problem.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
