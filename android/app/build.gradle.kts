plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.riffado.meetings"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riffado.meetings"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        versionCode = 3
        versionName = "2.2.0-phase3"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".v2"
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "Awady meetings v2")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = 
                "riffado-meetings-${buildType.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += "onnx"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Compose screenshot tests (Robolectric + Roborazzi) are opt-in because they download a large
// Robolectric runtime: ./gradlew testDebugUnitTest -Pscreenshots  → PNGs in app/screenshots/
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) {
        maxHeapSize = "3g"
        systemProperty("roborazzi.test.record", "true")
        systemProperty("riffado.screenshot.dir", rootProject.file("app/screenshots").absolutePath)
    } else {
        exclude("**/*ScreenshotTest*")
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.haze)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
