plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.getsolace.ai.chat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.getsolace.ai.chat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── 策略配置 ──────────────────────────────────────────
        buildConfigField("String", "STRATEGY_BASE_URL",  "\"https://gitee.com/\"")
        buildConfigField("String", "STRATEGY_OWNER",     "\"gaozhongkui\"")
        buildConfigField("String", "STRATEGY_REPO",      "\"app_config\"")
        buildConfigField("String", "STRATEGY_FILE_PATH", "\"solace_strategy.json\"")
        buildConfigField("String", "STRATEGY_BRANCH",    "\"master\"")
        buildConfigField("String", "STRATEGY_TOKEN",     "\"\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // MediaPipe Hand Gesture
    implementation("com.google.mediapipe:tasks-vision:0.10.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")   // 视频帧缩略图支持

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")

    // Security / Encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Network (Retrofit + OkHttp for AI API)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Permissions helper
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Media3 ExoPlayer — 视频播放
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Compose Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

}