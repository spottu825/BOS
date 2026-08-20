plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bos.app"
        minSdk = 29 // Android 10
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Sender phone hosts local HTTP and WebSocket signaling.
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-cio-jvm:2.3.12")
    implementation("io.ktor:ktor-server-websockets-jvm:2.3.12")

    // Direct encrypted media between sender and viewer.
    implementation("io.github.webrtc-sdk:android:144.7559.12")
    implementation("com.google.zxing:core:3.5.3")

    // JSON signaling messages.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
