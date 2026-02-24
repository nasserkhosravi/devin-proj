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
    val versionName = "\"4.3.1\""

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
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(mapOf("path" to ":lib-calendar")))
    implementation(project(mapOf("path" to ":devin")))
    implementation(project(mapOf("path" to ":devin-write-okhttp")))
    implementation(project(mapOf("path" to ":lib-har")))

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.13.0")
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

}
