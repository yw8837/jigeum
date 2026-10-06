import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// AST is explicit opt-in for local debug research or the validated beta candidate.
// A normal checkout/build requires neither AST weights nor ONNX Runtime.
val astProbeProperty = providers.gradleProperty("agattokAstProbe").orNull
require(astProbeProperty == null || astProbeProperty in setOf("true", "false")) {
    "agattokAstProbe must be explicitly true or false."
}
val astProbeEnabled = astProbeProperty == "true"
val astStandaloneProperty = providers.gradleProperty("agattokAstStandalone").orNull
require(astStandaloneProperty == null || astStandaloneProperty in setOf("true", "false")) {
    "agattokAstStandalone must be explicitly true or false."
}
val astStandaloneEnabled = astStandaloneProperty == "true"
require(!astStandaloneEnabled || astProbeEnabled) { "AST standalone requires agattokAstProbe=true." }
val astCandidateProperty = providers.gradleProperty("agattokAstCandidate").orNull
require(astCandidateProperty == null || astCandidateProperty in setOf("true", "false")) {
    "agattokAstCandidate must be explicitly true or false."
}
val astCandidateEnabled = astCandidateProperty == "true"
require(!astCandidateEnabled || astProbeEnabled) { "AST candidate requires agattokAstProbe=true." }
require(!(astCandidateEnabled && astStandaloneEnabled)) { "AST candidate and standalone are mutually exclusive." }
val astBetaProperty = providers.gradleProperty("agattokAstBeta").orNull
require(astBetaProperty == null || astBetaProperty in setOf("true", "false")) {
    "agattokAstBeta must be explicitly true or false."
}
val astBetaEnabled = astBetaProperty == "true"
require(!astBetaEnabled || !(astProbeEnabled || astStandaloneEnabled || astCandidateEnabled)) {
    "AST beta cannot be mixed with AST probe, standalone or candidate research flags."
}
val astRuntimeEnabled = astProbeEnabled || astBetaEnabled

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
        versionCode = 30
        versionName = "0.5.16"
        buildConfigField("boolean", "FULL_ACCESS_PREVIEW", "false")
        buildConfigField("boolean", "FULL_ACCESS_CLOSED_TEST", "false")
        buildConfigField("boolean", "AST_INTEGRATED_CANDIDATE", "false")
        buildConfigField("boolean", "AST_BABBLE_SUPPORT", "false")
        manifestPlaceholders["testDistribution"] = "false"
        manifestPlaceholders["closedTestDistribution"] = "false"
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
        getByName("debug") {
            if (astStandaloneEnabled) applicationIdSuffix = ".astprobe"
            if (astCandidateEnabled) {
                applicationIdSuffix = ".astcandidate"
                versionNameSuffix = "-astcandidate"
                buildConfigField("boolean", "AST_INTEGRATED_CANDIDATE", "true")
                buildConfigField("boolean", "FULL_ACCESS_CLOSED_TEST", "true")
                buildConfigField("String", "PLAY_VERIFY_URL", "\"\"")
                buildConfigField("String", "PLAY_ENTITLEMENT_PUBLIC_KEY", "\"\"")
                manifestPlaceholders["testDistribution"] = "true"
                manifestPlaceholders["closedTestDistribution"] = "true"
            }
        }
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
        // Explicit sideload-only tester distribution. Never a stored purchase/entitlement.
        create("preview") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-test"
            buildConfigField("boolean", "FULL_ACCESS_PREVIEW", "true")
            buildConfigField("String", "PLAY_VERIFY_URL", "\"\"")
            buildConfigField("String", "PLAY_ENTITLEMENT_PUBLIC_KEY", "\"\"")
            manifestPlaceholders["testDistribution"] = "true"
        }
        // User-authorized full access on the Play closed-test track only.
        // Production release keeps both flags false and checks real purchases.
        create("beta") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-beta"
            buildConfigField("boolean", "FULL_ACCESS_CLOSED_TEST", "true")
            buildConfigField("boolean", "AST_BABBLE_SUPPORT", astBetaEnabled.toString())
            buildConfigField("String", "PLAY_VERIFY_URL", "\"\"")
            buildConfigField("String", "PLAY_ENTITLEMENT_PUBLIC_KEY", "\"\"")
            manifestPlaceholders["testDistribution"] = "true"
            manifestPlaceholders["closedTestDistribution"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets {
        if (astStandaloneEnabled) {
            getByName("debug").manifest.srcFile("src/astStandalone/AndroidManifest.xml")
        }
        if (astCandidateEnabled) {
            getByName("debug").manifest.srcFile("src/astCandidate/AndroidManifest.xml")
        }
        getByName("main") {
            java.srcDir(if (astRuntimeEnabled) "src/astProbe/java" else "src/noAst/java")
            if (astRuntimeEnabled) assets.srcDir("src/astProbe/assets")
        }
        getByName("androidTest") {
            if (astProbeEnabled) {
                java.srcDir("src/astProbeAndroidTest/java")
                assets.srcDir("src/astProbeAndroidTest/assets")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Runtime source selection is per invocation; reject mixed graphs before any task
// runs so another variant cannot package AST with an inactive product flag.
gradle.taskGraph.whenReady {
    if (astBetaEnabled) {
        // These beta tasks handle native debug metadata, not the debug variant.
        val betaNativeMetadataTasks = setOf("stripBetaDebugSymbols", "mergeBetaNativeDebugMetadata")
        val forbidden = allTasks.filter { task ->
            task.project == project && task.name !in betaNativeMetadataTasks &&
                listOf("debug", "release", "preview").any {
                    task.name.contains(it, ignoreCase = true)
                }
        }
        require(forbidden.isEmpty()) {
            "AST beta is beta-only. Remove -PagattokAstBeta=true for debug, release or preview tasks: " +
                forbidden.joinToString { it.path }
        }
    }
}

// Sideload preview remains blocked; beta is only for the authorized closed track.
tasks.configureEach {
    if (astCandidateEnabled && (name == "bundleDebug" || name == "packageDebugBundle")) {
        doFirst { error("AST integrated candidate is local APK-only; bundles and Play upload are prohibited.") }
    }
    if (astProbeEnabled && listOf("release", "beta", "preview").any { name.contains(it, ignoreCase = true) }) {
        doFirst { error("AST probe is debug-only. Remove -PagattokAstProbe=true for release, beta or preview builds.") }
    }
    if (name == "bundlePreview" || name == "packagePreviewBundle") {
        doFirst { error("Preview is sideload-only. Use assemblePreview for testing or bundleRelease for Play.") }
    }
}

// Download/export is an explicit local preparation step, never a Gradle network action.
// Explicit verification and opted-in packaging fail for an absent/changed model.
// Default debug/beta/release builds have no dependency on this task or model.
val verifyAstModel by tasks.registering {
    val model = layout.projectDirectory.file("src/astProbe/assets/ast-dynamic-int8.onnx")
    inputs.file(model)
    doLast {
        val source = model.asFile
        require(source.isFile && source.length() == 91_134_123L) {
            "Pinned AST model absent or wrong size. Run tools/research/setup_android_ast.py with the verified local INT8 graph."
        }
        val hash = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == "3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e") {
            "Pinned AST model SHA256 mismatch. Do not package a substituted graph."
        }
    }
}
tasks.configureEach {
    if (astProbeEnabled && (name == "mergeDebugAssets" || name == "mergeDebugAndroidTestAssets")) dependsOn(verifyAstModel)
    if (astBetaEnabled && name == "mergeBetaAssets") dependsOn(verifyAstModel)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    val cameraXVersion = "1.6.2"
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    // Lifecycle 2.11 Compose artifacts require AGP 9.2; keep the compatible 2.9 line.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-video:$cameraXVersion")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")
    implementation("com.google.mediapipe:tasks-audio:1.0.0")
    // Python probe used 1.24.4; Android's official 1.24 series ends at 1.24.3.
    // Full CPU AAR includes DynamicQuantizeLinear/MatMulInteger; no reduced operator build.
    if (astRuntimeEnabled) implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    // Newer 4.x runtime supports the older MediaPipe gencode; keep parser/model regression coverage.
    implementation("com.google.protobuf:protobuf-javalite:4.36.2")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
