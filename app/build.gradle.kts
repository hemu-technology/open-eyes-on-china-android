plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openeyesonchina.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.openeyesonchina.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
        viewBinding = true
    }
}

dependencies {
    // Explicit BOM version aligned with Kotlin plugin to avoid metadata mismatch errors
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.1.0"))
    implementation("androidx.core:core-ktx:1.13.1")
    // SplashScreen API support library
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.gms:play-services-ads:24.7.0")
    // Offline caching (OkHttp client with disk cache)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // WorkManager for scheduled offline prefetch tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
