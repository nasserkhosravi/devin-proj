import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.vanniktech.maven.publish")
    id("signing")
}

val projProps: MutableMap<String, *> = project.properties
val localProps = Properties().apply {
    load(rootProject.file("local.properties").reader())
}

android {
    namespace = "com.khosravi.devin.write"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val pomVersionName = projProps.getStringOrException("POM_VERSION_NAME")
        buildConfigField("String", "DEVIN_WRITE_VERSION", "\"{$pomVersionName}\"")
    }

    buildTypes {
        release {
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation(kotlin("reflect"))

    val roomVersion = "2.5.1"
    implementation("androidx.room:room-common:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
}

mavenPublishing {
    coordinates(
        projProps.getStringOrException("POM_GROUP_ID"),
        projProps.getStringOrException("POM_ARTIFACT_ID"),
        projProps.getStringOrException("POM_VERSION_NAME")
    )

    pom {
        name.set(projProps.getStringOrException("POM_NAME"))
        description.set(projProps.getStringOrException("POM_PROJ_DESCRIPTION"))
        url.set(projProps.getStringOrException("POM_PROJ_URL"))
        inceptionYear.set("2024")


        licenses {
            license {
                name.set(projProps.getStringOrException("POM_LICENCE_NAME"))
                url.set(projProps.getStringOrException("POM_LICENCE_URL"))
                distribution.set(projProps.getStringOrException("POM_LICENCE_DIST"))
            }
        }

        developers {
            developer {
                id.set("nasserkhosravi")
                name.set("nasser.khosravi")
                email.set("jobnaserkhosravi@gmail.com")
            }
        }
        scm {
            url.set(projProps.getStringOrException("POM_SCM_URL"))
            connection.set(projProps.getStringOrException("POM_SCM_CONNECTION"))
            developerConnection.set(projProps.getStringOrException("POM_SCM_DEV_CONNECTION"))
        }
    }

    signing {
        useGpgCmd()
        val signingKeyId = localProps.getStringOrException("signingKeyId")
        val signingKeyPassword = localProps.getStringOrException("signingKeyPassword")
        val signingKey = localProps.getStringOrException("signingKey")

        useInMemoryPgpKeys(
            signingKeyId,
            signingKey,
            signingKeyPassword,
        )
        sign(publishing.publications)
    }

    publishToMavenCentral()
    signAllPublications()
}


fun Map<String, *>.getStringOrException(name: String) = this.get(name)?.toString()

fun Properties.getStringOrException(name: String): String {
    val value = get(name) ?: throw NoSuchElementException("$name not found")
    return value as String
}