plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khosravi.devin.bench"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khosravi.devin.bench"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("io.github.nasserkhosravi.devin:write:4.5.0")
    implementation("io.github.nasserkhosravi.devin:write-okhttp:1.0.2")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    testImplementation("junit:junit:4.13.2")
    // The real org.json, so parsers work in JVM unit tests (Android's stub throws).
    testImplementation("org.json:json:20231013")
}
