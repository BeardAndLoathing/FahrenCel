import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Change this (and the package folders) to your own unique ID before publishing.
    namespace = "com.fahrencel.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fahrencel.app"
        minSdk = 26
        targetSdk = 35
        // GitHub builds pass a run number so each release counts up; local builds use 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "0.1"
    }

    // Test builds are signed with a shared test key so an APK built on any computer (or by
    // GitHub) installs over the previous one. The key and its passwords are NOT in the repo:
    // they come from keystore.properties (gitignored, see keystore.properties.example) or from
    // environment variables (GitHub Actions secrets). Without them, the normal debug key is used.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        keystoreProps.getProperty(prop) ?: System.getenv(env)

    val testStoreFile = signingValue("storeFile", "FAHRENCEL_KEYSTORE_FILE")?.let { rootProject.file(it) }
    val hasTestKey = testStoreFile?.exists() == true

    signingConfigs {
        if (hasTestKey) {
            create("tester") {
                storeFile = testStoreFile
                storePassword = signingValue("storePassword", "FAHRENCEL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "FAHRENCEL_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "FAHRENCEL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (hasTestKey) signingConfig = signingConfigs.getByName("tester")
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
