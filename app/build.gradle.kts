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
        versionCode = 12
        versionName = "0.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
//    splits {
//        abi {
//            isEnable = true
//            reset()
//            include( "arm64-v8a")
//            isUniversalApk = false
//        }
//    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Local Unit Tests (Fast, runs on your PC's JVM - For testing Maze Math)
    testImplementation(libs.androidx.junit.ktx)

    // Android Instrumented Tests (Runs on Emulator / Physical Device)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}