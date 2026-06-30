plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.appsbyalok.echohunter"
    testNamespace = "com.appsbyalok.echohunter.test"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appsbyalok.echohunter"
        minSdk = 21
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 16
        versionName = "0.7.2"

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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Local Unit Tests
    testImplementation(libs.androidx.junit.ktx)

    // Android Instrumented Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}