import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("maven-publish")
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
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    api(project(mapOf("path" to ":devin-api")))

    val roomVersion = "2.5.1"
    implementation("androidx.room:room-common:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
}

fun Project.getRepositoryUrl(): java.net.URI {
    val isReleaseBuild = properties["POM_VERSION_NAME"]?.toString()?.contains("SNAPSHOT") == false
    val releaseRepoUrl =
        properties["RELEASE_REPOSITORY_URL"]?.toString() ?: "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
    val snapshotRepoUrl =
        properties["SNAPSHOT_REPOSITORY_URL"]?.toString() ?: "https://oss.sonatype.org/content/repositories/snapshots/"
    return uri(if (isReleaseBuild) releaseRepoUrl else snapshotRepoUrl)
}


afterEvaluate {
    publishing {
        publications {
            repositories {
                maven {
                    url = getRepositoryUrl()
                    // credentials are stored in ~/.gradle/gradle.properties with ~ being the path of the home directory
                    credentials {
                        username = localProps.getStringOrException("ossUsername")
                        password = localProps.getStringOrException("ossPassword")
                    }
                }
            }


            val publicationName = projProps["POM_NAME"]?.toString() ?: "publication"
            create<MavenPublication>(publicationName) {
                from(project.components["release"])

                pom {
                    groupId = projProps.getStringOrException("POM_GROUP_ID")
                    artifactId = projProps.getStringOrException("POM_ARTIFACT_ID")
                    version = projProps.getStringOrException("POM_VERSION_NAME")

                    name.set(projProps.getStringOrException("POM_NAME"))
                    description.set(projProps.getStringOrException("POM_PROJ_DESCRIPTION"))
                    url.set(projProps.getStringOrException("POM_PROJ_URL"))
                    packaging = projProps.getStringOrException("POM_PACKAGING")

                    scm {
                        url.set(projProps.getStringOrException("POM_SCM_URL"))
                        connection.set(projProps.getStringOrException("POM_SCM_CONNECTION"))
                        developerConnection.set(projProps.getStringOrException("POM_SCM_DEV_CONNECTION"))
                    }

                    developers {
                        developer {
                            id.set("nasserkhosravi")
                            name.set("nasser.khosravi")
                            email.set("jobnaserkhosravi@gmail.com")
                        }
                    }
                    licenses {
                        license {
                            name.set(projProps.getStringOrException("POM_LICENCE_NAME"))
                            url.set(projProps.getStringOrException("POM_LICENCE_URL"))
                            distribution.set(projProps.getStringOrException("POM_LICENCE_DIST"))
                        }
                    }
                }
            }

            signing {
                val signingKeyId = localProps.getStringOrException("signingKeyId")
                val signingKeyPassword = localProps.getStringOrException("signingKeyPassword")
                val signingKey = localProps.getStringOrException("signingKey")
                useInMemoryPgpKeys(signingKeyId, signingKey, signingKeyPassword)
                sign(publishing.publications.getByName(publicationName))
            }

        }

    }
}

fun Map<String, *>.getStringOrException(name: String) = this.get(name)?.toString()

fun Properties.getStringOrException(name: String): String {
    val value = get(name) ?: throw NoSuchElementException("$name not found")
    return value as String
}