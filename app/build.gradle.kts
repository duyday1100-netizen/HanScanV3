plugins {
    id("com.android.application")
}

android {
    namespace = "com.hanscan.v3"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hanscan.v3"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.0-alpha"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    val camerax = "1.6.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Bundled Chinese OCR model: works offline after install.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // Perspective correction / contrast enhancement.
    implementation("com.quickbirdstudios:opencv:4.5.3.0")

    // Pinyin fallback for characters missing from the curated dictionary.
    implementation("com.belerweb:pinyin4j:2.5.1")
}
