plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Change this (and the package folders) to your own unique ID before publishing.
    namespace = "com.example.dualweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dualweather"
        minSdk = 26
        targetSdk = 35
        // GitHub builds pass a run number so each release counts up; local builds use 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1"
    }

    // Test builds are signed with a key kept in the repo, so an APK built on any computer
    // (or by GitHub) installs over the previous one. Not for the Play Store: that needs a
    // private release key kept out of the repo.
    signingConfigs {
        create("tester") {
            storeFile = file("fahrencel-test.keystore")
            storePassword = "fahrencel-test"
            keyAlias = "fahrencel"
            keyPassword = "fahrencel-test"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("tester")
        }
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // App screens (settings, theme editor) use Jetpack Compose.
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
