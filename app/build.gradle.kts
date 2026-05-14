plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.appsbyalok.echohunter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.appsbyalok.echohunter"
        minSdk = 21
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 6
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include( "arm64-v8a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Local Unit Tests (Fast, runs on your PC's JVM - For testing Maze Math)
    testImplementation("junit:junit:4.13.2")

    // Android Instrumented Tests (Runs on Emulator / Physical Device)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}