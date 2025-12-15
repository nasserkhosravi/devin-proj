plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khosravi.sample.devin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.khosravi.sample.devin"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("io.coil-kt:coil:2.7.0")
//    implementation("io.github.nasserkhosravi.devin:write:4.2.0")

    implementation("androidx.fragment:fragment-ktx:1.4.0")
    debugImplementation(project(mapOf("path" to ":devin")))
    releaseImplementation(project(mapOf("path" to ":devin-no-op")))

    debugImplementation(project(mapOf("path" to ":devin-write-okhttp")))
    releaseImplementation(project(mapOf("path" to ":devin-write-okhttp-no-op")))

    //for reading har sample from asset
    implementation(project(mapOf("path" to ":lib-har")))
}