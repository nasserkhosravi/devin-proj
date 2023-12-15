plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.khosravi.devin.present"
    compileSdk = 33

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val versionName = "\"1.0.0\""

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
    implementation("io.github.nasserkhosravi.devin:write:1.0.1")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.4.0")

    implementation("androidx.room:room-runtime:2.5.1")

    implementation("com.google.dagger:dagger-android:2.24")
    kapt("com.google.dagger:dagger-compiler:2.24")

    implementation("com.mikepenz:fastadapter:5.7.0")
    implementation("com.mikepenz:fastadapter-extensions-binding:5.7.0")
}
