plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) apply false
}

// Apply google-services plugin conditionally.
// Debug builds allow credential-free local development when no google-services.json is provided.
// Release builds strictly require root (app/google-services.json) or Release (app/src/release/google-services.json) config.
// Debug-only configuration (app/src/debug/google-services.json) does NOT qualify for Release builds.
val hasRootGoogleServices = file("google-services.json").exists()
val hasDebugGoogleServices = file("src/debug/google-services.json").exists()
val hasReleaseGoogleServices = file("src/release/google-services.json").exists()

val hasValidReleaseConfig = hasRootGoogleServices || hasReleaseGoogleServices
val hasValidDebugConfig = hasRootGoogleServices || hasDebugGoogleServices

if (hasValidReleaseConfig || hasValidDebugConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// Configuration-cache-compatible Release configuration safety task.
val validateReleaseGoogleServices = tasks.register("validateReleaseGoogleServices") {
    val rootExists = file("google-services.json").exists()
    val releaseExists = file("src/release/google-services.json").exists()
    val debugExists = file("src/debug/google-services.json").exists()
    val isValidRelease = rootExists || releaseExists

    inputs.property("isValidRelease", isValidRelease)
    inputs.property("debugExists", debugExists)

    doLast {
        if (!isValidRelease) {
            val causeMsg = if (debugExists) {
                "Debug-only configuration (app/src/debug/google-services.json) is not permitted for Release builds."
            } else {
                "No Release Firebase configuration found."
            }
            throw GradleException(
                "Release build requires google-services.json in app/ or app/src/release/. $causeMsg " +
                "Provide production Firebase configuration before building Release artifacts."
            )
        }
    }
}

// Attach validation to all Release tasks and tasks that trigger Release builds.
tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) && name != "validateReleaseGoogleServices") {
        dependsOn(validateReleaseGoogleServices)
    }
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
