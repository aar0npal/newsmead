plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")

    // Make sure that you have the Google services Gradle plugin
    id("com.google.gms.google-services")

    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")

    // Safe args
    id("androidx.navigation.safeargs.kotlin")

    // Room
    id("kotlin-kapt")
}

android {
    namespace = "com.newsmead"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.newsmead"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.2"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Enable viewBinding
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))

    // Add the dependencies for the Crashlytics and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Import navigation dependencies
    implementation("androidx.navigation:navigation-fragment-ktx:2.4.1")
    implementation("androidx.navigation:navigation-ui-ktx:2.4.1")

    // Import RecyclerView dependencies
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Import Splash Screen dependencies
    implementation("androidx.core:core-splashscreen:1.0.0")

    // Import Facebook Shimmer dependencies
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Import Room dependencies
    implementation("androidx.room:room-runtime:2.6.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    kapt("androidx.room:room-compiler:2.6.0")

    // Import Coroutines dependencies
    implementation("androidx.room:room-ktx:2.6.0")

    // Import Volley dependencies
    implementation("com.android.volley:volley:1.2.1")

    // Import Glide dependencies
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Import ZoomLayout dependencies
    implementation("com.otaliastudios:zoomlayout:1.9.0")

    // Local gaze tracking: CameraX front-camera frames + MediaPipe FaceLandmarker.
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
