plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.driverfatiguedetection"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.driverfatiguedetection"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    // Keep model files uncompressed in the APK
    packaging {
        resources {
            // use addAll instead of += to avoid "Unresolved reference: noCompress"
            //noCompress.addAll(listOf("task", "tflite", "lite"))
        }
    }

    buildFeatures { compose = false }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")


    // CameraX
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax") // PreviewView

    // Activity KTX
    implementation("androidx.activity:activity-ktx:1.9.2")

    // MediaPipe Tasks
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // AndroidX core/lifecycle (from template)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Tests (optional)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
