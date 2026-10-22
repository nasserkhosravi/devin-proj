import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
}

val appMetricaApiKey: String = run {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    localProperties.getProperty("appmetrica.apiKey")
        ?: System.getenv("APPMETRICA_API_KEY")
        ?: ""
}

android {
    namespace = "com.khosravi.devin.present"
    compileSdk = libs.versions.project.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.project.minSdk.get().toInt()
        targetSdk = libs.versions.project.targetSdk.get().toInt()
        versionCode = 6
        // Stamps the update-check time before Application.onCreate so the e2e suite never hits GitHub.
        testInstrumentationRunner = "com.khosravi.devin.present.e2e.E2eTestRunner"
    }
    val versionName = "\"4.7.0\""

    // Only set on CI (see .github/workflows/release-presenter.yml); local release builds stay as before.
    val releaseKeystorePath: String? = System.getenv("PRESENTER_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("PRESENTER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PRESENTER_KEY_ALIAS")
                keyPassword = System.getenv("PRESENTER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "VERSION_NAME", versionName)
            buildConfigField("String", "APPMETRICA_API_KEY", "\"\"")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            buildConfigField("String", "VERSION_NAME", versionName)
            buildConfigField("String", "APPMETRICA_API_KEY", "\"$appMetricaApiKey\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        animationsDisabled = true
        unitTests {
            isReturnDefaultValues = true
            // Robolectric tests read app resources (strings) and the merged manifest.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(mapOf("path" to ":lib-calendar")))
    implementation(project(mapOf("path" to ":devin-write")))
    implementation(project(mapOf("path" to ":devin-write-okhttp")))
    implementation(project(mapOf("path" to ":lib-har")))

    implementation(libs.coreKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.fragmentKtx)

    implementation(libs.room.runtime)

    implementation(libs.dagger.android)
    kapt(libs.dagger.compiler)

    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.appmetrica.analytics)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.gson)

}
