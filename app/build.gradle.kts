import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

// ╔═══════════════════════════════════════════════════════════════════════════╗
// ║  VERSION CONTROL - UBAH DI SINI SETIAP RELEASE                            ║
// ╚═══════════════════════════════════════════════════════════════════════════╣
val appVersionCode = 1
val appVersionName = "1.0.0"

// ╔═══════════════════════════════════════════════════════════════════════════╗
// ║  BUILD ENVIRONMENT                                                         ║
// ╚═══════════════════════════════════════════════════════════════════════════╝
val isProduction = false  // ← TEMPORARY: Set false untuk test release build dengan dev URL

// API URLs
// ⚠️ PENTING: Gunakan IP yang sesuai dengan environment:
// - Emulator: gunakan 10.0.2.2 (mapping ke localhost host machine)
// - Real Device (LAN): gunakan IP LAN seperti 192.168.100.227
// - Production: gunakan domain production
val devApiUrlEmulator = "http://10.0.2.2:8000/api/"  // ← Untuk Android Emulator
val devApiUrlRealDevice = "http://127.0.0.1:8080/api/"  // ← Untuk Real Device di LAN
val prodApiUrl = "https://tally-sheet.tirtagroup.co.id/api/"

// ✅ GANTI INI SESUAI KEBUTUHAN:
val useEmulatorUrl = true  // ← true = emulator, false = real device
val devApiUrl = if (useEmulatorUrl) devApiUrlEmulator else devApiUrlRealDevice

android {
    namespace = "com.example.handheldapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.handheldapp"
        minSdk = 24
        targetSdk = 34

        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("int", "VERSION_CODE_INT", "$appVersionCode")
        buildConfigField("String", "VERSION_NAME_STR", "\"$appVersionName\"")
        buildConfigField("String", "BASE_URL", "\"${if (isProduction) prodApiUrl else devApiUrl}\"")
        buildConfigField("boolean", "IS_PRODUCTION", "$isProduction")
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties().apply {
                    // Menggunakan FileInputStream lebih stabil untuk pembacaan properti
                    load(FileInputStream(keystorePropertiesFile))
                }

                // Path harus dari rootProject, bukan dari app module
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile") as String)
                storePassword = keystoreProperties.getProperty("storePassword") as String
                keyAlias = keystoreProperties.getProperty("keyAlias") as String
                keyPassword = keystoreProperties.getProperty("keyPassword") as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "BASE_URL", "\"$devApiUrl\"")
        }

        release {
            isMinifyEnabled = false  // ← TEMPORARY: Disable untuk test
            isShrinkResources = false  // ← TEMPORARY: Disable untuk test
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Memastikan signingConfig release hanya dipasang jika file properties ada
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            // ★ FIX: Respect isProduction flag untuk testing
            buildConfigField("String", "BASE_URL", "\"${if (isProduction) prodApiUrl else devApiUrl}\"")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// APK NAMING
android.applicationVariants.all {
    val variant = this
    outputs.all {
        val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val buildType = variant.buildType.name
        output.outputFileName = "warehouse_app_v${appVersionName}_${appVersionCode}_${buildType}.apk"
    }
}

val roomVersion = "2.6.1"
val workVersion = "2.9.0"
val hiltWorkVersion = "1.1.0"

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.dagger:hilt-android:2.50")
    implementation(libs.androidx.activity)
    kapt("com.google.dagger:hilt-compiler:2.50")

    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("androidx.hilt:hilt-work:$hiltWorkVersion")
    kapt("androidx.hilt:hilt-compiler:$hiltWorkVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

kapt {
    correctErrorTypes = true
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.13.1")
        force("androidx.activity:activity:1.9.3")
        force("androidx.activity:activity-ktx:1.9.3")
        force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
        force("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    }
}