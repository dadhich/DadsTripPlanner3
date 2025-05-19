import java.util.Properties // Make sure this import is present or add it

// Load properties from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties") // Reference to local.properties at project root
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { input ->
        localProperties.load(input)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.dadstripplanner3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dadstripplanner3"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Expose API Key from local.properties as a BuildConfig field
        // The third parameter is the value, ensure it's properly quoted if it's a string
        val tfnswApiKey = localProperties.getProperty("TfNSW_API_KEY") ?: "" // Fallback to empty string if not found
        buildConfigField("String", "TfNSW_API_KEY", "\"$tfnswApiKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true // Ensure buildConfig is enabled (usually true by default)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.material.v1120)
    implementation(libs.play.services.location)

    // Retrofit for networking
    implementation(libs.retrofit)
    implementation(libs.retrofit2.converter.gson) // Gson converter for Retrofit

    // OkHttp Logging Interceptor (for debugging network calls)
    implementation(libs.logging.interceptor)

    // Gson (if not transitively included by converter-gson, though it usually is)
    implementation(libs.gson)

}