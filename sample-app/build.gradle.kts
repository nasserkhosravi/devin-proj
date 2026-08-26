plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khosravi.sample.devin"
    compileSdk = libs.versions.project.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.khosravi.sample.devin"
        minSdk = libs.versions.project.minSdk.get().toInt()
        targetSdk = libs.versions.project.targetSdk.get().toInt()
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

    implementation(libs.coreKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.glide)
    implementation(libs.coil)

    implementation(libs.fragmentKtx)
    debugImplementation(project(mapOf("path" to ":devin")))
    releaseImplementation(project(mapOf("path" to ":devin-no-op")))

    debugImplementation(project(mapOf("path" to ":devin-write-okhttp")))
    releaseImplementation(project(mapOf("path" to ":devin-write-okhttp-no-op")))

    //for reading har sample from asset
    implementation(project(mapOf("path" to ":lib-har")))
}
