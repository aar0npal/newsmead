import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.newsmead.mgazenetbenchmark"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.newsmead.mgazenetbenchmark"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-audit"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    sourceSets["main"].apply {
        assets.srcDir("vendor/assets")
        jniLibs.srcDir("vendor/jniLibs")
    }
    // Avoid relying on APK ZIP alignment with the project's existing AGP 8.3.
    packaging { jniLibs { useLegacyPackaging = true } }
}

val verifyVendor by tasks.registering {
    doLast {
        check(file("vendor/assets/base.mnn").isFile) {
            "Run tools/gaze/mgazenet/fetch_vendor.py explicitly before building the benchmark."
        }
        val pins = mapOf(
            "assets/base.mnn" to "2f96b95275fe6d7b79e98df3237ebb96e15ef5522c96968f08167da7e1954a96",
            "assets/face_landmarker.task" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
            "jniLibs/arm64-v8a/libMNN.so" to "c91fb9e65ef45477406583cf374978c61713318893f6af473aecdafed4c17f60",
            "jniLibs/arm64-v8a/libmnncore.so" to "075cbfac452b1a4b601a5e9d74134b800cf13e34a998aee3d1367571c2007bd9",
            "jniLibs/arm64-v8a/libc++_shared.so" to "f9992c4ba6b7c5a716e3a202fceb1ce029d6a2b0605838ac6b3219f489dd7970"
        )
        pins.forEach { (path, expected) ->
            val hash = MessageDigest.getInstance("SHA-256").digest(file("vendor/$path").readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == expected) { "Vendor hash mismatch: $path" }
        }
        check(file("vendor/assets/vendor-manifest.json").isFile)
        check(file("vendor/assets/notices/GazeFollower-CC-BY-NC-SA-4.0.txt").isFile)
        check(file("vendor/assets/notices/MNN-Apache-2.0.txt").isFile)
    }
}
tasks.named("preBuild").configure { dependsOn(verifyVendor) }

dependencies {
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("com.google.mediapipe:tasks-vision:0.10.29")
    implementation("org.opencv:opencv:4.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
