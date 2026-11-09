plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) apply false
}

// Apply google-services plugin conditionally.
// Debug foundation builds succeed without google-services.json for credential-free local development.
// Release builds strictly require google-services.json to prevent releasing misconfigured production artifacts.
val hasGoogleServices = file("google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    file("src/release/google-services.json").exists()

val isReleaseBuildRequested = gradle.startParameter.taskNames.any { name ->
    name.contains("Release", ignoreCase = true)
}

if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
} else if (isReleaseBuildRequested) {
    throw GradleException(
        "Release build requires google-services.json in app/ or app/src/release/. " +
        "Provide production Firebase configuration before building Release artifacts."
    )
}

android {
    namespace = "com.example.timecapsule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.timecapsule"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Firebase platform BoM and main modules
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    // Maps & Location
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
