import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val privateLocalProperties = Properties().apply {
    val privateFile = rootProject.file("private-local.properties")
    if (privateFile.exists()) {
        privateFile.inputStream().use { load(it) }
    }
}

fun privateProperty(name: String, defaultValue: String): String {
    return privateLocalProperties.getProperty(name, defaultValue)
}

fun String.asBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.overcoders.unlpcarteleranotifier"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.overcoders.unlpcarteleranotifier"
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 22
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            privateProperty("firebase.projectId", "example-cartelera-project").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            privateProperty("firebase.applicationId", "1:1234567890:android:exampleapp123456").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            privateProperty("firebase.apiKey", "example-android-api-key").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_GCM_SENDER_ID",
            privateProperty("firebase.gcmSenderId", "1234567890").asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_SERVER_BASE_URL",
            "\"\""
        )
        buildConfigField(
            "String",
            "FIREBASE_SERVER_API_TOKEN",
            "\"\""
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "FIREBASE_SERVER_BASE_URL",
                privateProperty(
                    "firebase.serverBaseUrl",
                    "https://your-firebase-sync-server.example.com"
                ).asBuildConfigString()
            )
            buildConfigField(
                "String",
                "FIREBASE_SERVER_API_TOKEN",
                privateProperty("firebase.serverApiToken", "").asBuildConfigString()
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            ndk {
                debugSymbolLevel = "FULL"
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.datastore.preferences)
    // Compatibilidad transitoria: hoy WorkManager solo queda para cancelar trabajos legacy.
    // Cuando se eliminen `BootReceiver` y `cancelLegacyPolling`, esta dependencia debería salir.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    //noinspection UseTomlInstead
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
