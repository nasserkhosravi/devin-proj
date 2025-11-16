plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.khosravi.devin.present"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        versionCode = 4
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val versionName = "\"4.2.0\""

    buildTypes {
        debug {
            buildConfigField("String", "VERSION_NAME", versionName)
        }
        release {
            buildConfigField("String", "VERSION_NAME", versionName)
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
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    implementation(project(mapOf("path" to ":lib-calendar")))
    implementation(project(mapOf("path" to ":devin")))
    implementation(project(mapOf("path" to ":devin-write-okhttp")))
    implementation(project(mapOf("path" to ":lib-har")))

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.4.0")

    implementation("androidx.room:room-runtime:2.5.1")

    implementation("com.google.dagger:dagger-android:2.24")
    kapt("com.google.dagger:dagger-compiler:2.24")

    val fastAdapterVersion = "5.7.0"
    implementation("com.mikepenz:fastadapter:$fastAdapterVersion")
    implementation("com.mikepenz:fastadapter-extensions-binding:$fastAdapterVersion")
    implementation("com.mikepenz:fastadapter-extensions-expandable:$fastAdapterVersion")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation ("com.github.wellingtoncabral:android-spantastic:1.0.0")
    implementation ("com.google.code.gson:gson:2.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2023.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

}
