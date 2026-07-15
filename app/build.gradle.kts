// Configura variantes, dependencias y validaciones de build de la aplicación Android.
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
    return privateLocalProperties.getProperty(name, defaultValue).trim()
}

fun String.asBuildConfigString(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

val firebaseProjectId = privateProperty("firebase.projectId", "example-cartelera-project")
val firebaseApplicationId =
    privateProperty("firebase.applicationId", "1:1234567890:android:exampleapp123456")
val firebaseApiKey = privateProperty("firebase.apiKey", "example-android-api-key")
val firebaseGcmSenderId = privateProperty("firebase.gcmSenderId", "1234567890")

val firebaseProjectIdPattern = Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")
val firebaseApplicationIdPattern = Regex("1:(\\d{6,20}):android:[A-Za-z0-9]+")
val firebaseApiKeyPattern = Regex("AIza[0-9A-Za-z_-]{35}")
val firebaseGcmSenderIdPattern = Regex("\\d{6,20}")

fun String.isValidFirebaseProjectId(): Boolean =
    !startsWith("example-", ignoreCase = true) && firebaseProjectIdPattern.matches(this)

fun String.isValidFirebaseApplicationId(): Boolean =
    !contains(":android:exampleapp", ignoreCase = true) &&
        firebaseApplicationIdPattern.matches(this)

fun String.isValidFirebaseApiKey(): Boolean =
    firebaseApiKeyPattern.matches(this)

fun String.isValidFirebaseGcmSenderId(): Boolean =
    this != "1234567890" && firebaseGcmSenderIdPattern.matches(this)

fun firebaseProjectNumberFromApplicationId(applicationId: String): String? =
    firebaseApplicationIdPattern.matchEntire(applicationId)?.groupValues?.get(1)

fun firebaseProjectNumbersMatch(applicationId: String, gcmSenderId: String): Boolean =
    firebaseProjectNumberFromApplicationId(applicationId) == gcmSenderId

val invalidReleaseFirebaseProperties = listOf(
    "firebase.projectId" to firebaseProjectId.isValidFirebaseProjectId(),
    "firebase.applicationId" to firebaseApplicationId.isValidFirebaseApplicationId(),
    "firebase.apiKey" to firebaseApiKey.isValidFirebaseApiKey(),
    "firebase.gcmSenderId" to firebaseGcmSenderId.isValidFirebaseGcmSenderId(),
).filterNot { (_, isValid) -> isValid }
    .map { (name, _) -> name }

val releaseFirebaseConfigurationErrors = buildList {
    if (invalidReleaseFirebaseProperties.isNotEmpty()) {
        add(
            "Propiedades ausentes o inválidas en private-local.properties: " +
                invalidReleaseFirebaseProperties.joinToString()
        )
    }
    if (
        firebaseApplicationId.isValidFirebaseApplicationId() &&
        firebaseGcmSenderId.isValidFirebaseGcmSenderId() &&
        !firebaseProjectNumbersMatch(firebaseApplicationId, firebaseGcmSenderId)
    ) {
        add(
            "firebase.applicationId y firebase.gcmSenderId deben usar exactamente " +
                "el mismo número de proyecto Firebase."
        )
    }
}
val firebaseConfigurationValid = releaseFirebaseConfigurationErrors.isEmpty()

val releaseStoreFilePath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val releaseSigningRequired =
    providers.gradleProperty("REQUIRE_RELEASE_SIGNING").orNull?.toBooleanStrictOrNull() == true
val releaseSigningConfigured = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrEmpty() }

if (releaseSigningRequired && !releaseSigningConfigured) {
    throw GradleException(
        "La firma release es obligatoria, pero faltan propiedades RELEASE_STORE_FILE, " +
            "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS o RELEASE_KEY_PASSWORD."
    )
}

val releaseTaskRequested = gradle.startParameter.taskNames.any { requestedTask ->
    val taskName = requestedTask.substringAfterLast(':')
    taskName.contains("release", ignoreCase = true) ||
        taskName.lowercase() in setOf("assemble", "build", "bundle")
}

if (releaseTaskRequested && releaseFirebaseConfigurationErrors.isNotEmpty()) {
    throw GradleException(
        "No se puede compilar release por errores en la configuración Firebase:\n- " +
            releaseFirebaseConfigurationErrors.joinToString("\n- ")
    )
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
        versionCode = 26
        versionName = "2.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            firebaseProjectId.asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            firebaseApplicationId.asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            firebaseApiKey.asBuildConfigString()
        )
        buildConfigField(
            "String",
            "FIREBASE_GCM_SENDER_ID",
            firebaseGcmSenderId.asBuildConfigString()
        )
        // El runtime consume el resultado de esta misma validación para evitar reglas distintas.
        buildConfigField(
            "boolean",
            "FIREBASE_CONFIGURATION_VALID",
            firebaseConfigurationValid.toString()
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    //noinspection UseTomlInstead
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
