plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Supply all four MOA_UPLOAD_* variables for a Play-uploadable release.
// Without them, Gradle deliberately produces an unsigned release bundle.
val uploadSigning = mapOf(
    "keystore" to System.getenv("MOA_UPLOAD_KEYSTORE"),
    "alias" to System.getenv("MOA_UPLOAD_KEY_ALIAS"),
    "storePassword" to System.getenv("MOA_UPLOAD_STORE_PASSWORD"),
    "keyPassword" to System.getenv("MOA_UPLOAD_KEY_PASSWORD"),
)
val hasAnyUploadSigning = uploadSigning.values.any { !it.isNullOrBlank() }
val hasAllUploadSigning = uploadSigning.values.all { !it.isNullOrBlank() }
require(!hasAnyUploadSigning || hasAllUploadSigning) {
    "Set all four MOA_UPLOAD_* signing environment variables, or none of them."
}

android {
    namespace = "com.moa.babylanguage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moa.babylanguage"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Public configuration only. Server credentials must never enter the APK.
        fun publicConfig(name: String) = "\"" + (System.getenv(name) ?: "")
            .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
        buildConfigField("String", "PLAY_VERIFY_URL", publicConfig("JIGEUM_PLAY_VERIFY_URL"))
        buildConfigField("String", "PLAY_ENTITLEMENT_PUBLIC_KEY", publicConfig("JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY"))
    }

    signingConfigs {
        if (hasAllUploadSigning) {
            create("upload") {
                storeFile = file(uploadSigning.getValue("keystore")!!)
                storePassword = uploadSigning.getValue("storePassword")!!
                keyAlias = uploadSigning.getValue("alias")!!
                keyPassword = uploadSigning.getValue("keyPassword")!!
            }
        }
    }

    buildTypes {
        release {
            // MediaPipe 1.0.0 relies on protobuf fields and JNI bindings that
            // fail in an R8-minified release. Keep release behavior identical
            // to the tested debug model path until an upgraded SDK is verified.
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasAllUploadSigning) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    val cameraXVersion = "1.6.2"
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")
    implementation("com.google.mediapipe:tasks-audio:1.0.0")
    // MediaPipe requests 4.26.1; 4.27.5 patches CVE-2024-7254 while retaining the lite runtime.
    implementation("com.google.protobuf:protobuf-javalite:4.27.5")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
