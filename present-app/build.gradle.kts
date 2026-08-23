plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.khosravi.devin.present"
    compileSdk = libs.versions.project.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.project.minSdk.get().toInt()
        targetSdk = libs.versions.project.targetSdk.get().toInt()
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(mapOf("path" to ":lib-calendar")))
    implementation(project(mapOf("path" to ":devin")))
    implementation(project(mapOf("path" to ":devin-write-okhttp")))
    implementation(project(mapOf("path" to ":lib-har")))

    implementation(libs.coreKtx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.fragmentKtx)

    implementation(libs.room.runtime)

    implementation(libs.dagger.android)
    kapt(libs.dagger.compiler)

    implementation(libs.fastadapter)
    implementation(libs.fastadapter.binding)
    implementation(libs.fastadapter.expandable)
    implementation(libs.glide)
    implementation(libs.android.spantastic)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.json)

}
