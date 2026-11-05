import org.gradle.authentication.http.BasicAuthentication

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

val bitbucketPackagesUrl = providers.gradleProperty("bitbucketPackagesUrl")
    .orElse(
        providers.gradleProperty("bitbucketPackagesWorkspace")
            .map { workspace -> "https://maven.apkg.io/$workspace" },
    )
val bitbucketPackagesUsername = providers.gradleProperty("bitbucketPackagesUsername")
    .orElse(providers.gradleProperty("atlassianEmail"))
    .orElse(providers.environmentVariable("BITBUCKET_PACKAGES_USERNAME"))
val bitbucketPackagesToken = providers.gradleProperty("bitbucketPackagesToken")
    .orElse(providers.gradleProperty("atlassianApiToken"))
    .orElse(providers.environmentVariable("BITBUCKET_PACKAGES_TOKEN"))

android {
    namespace = "com.ibreve.biolink.mqtt"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }

}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.paho.mqttv3)
    implementation(libs.hivemq.mqtt.client)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.localbroadcastmanager)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<org.gradle.api.publish.maven.MavenPublication>("release") {
                from(components["release"])

                groupId = providers.gradleProperty("mqttClientGroup")
                    .getOrElse("com.ibreve.biolink")
                artifactId = providers.gradleProperty("mqttClientArtifactId")
                    .getOrElse("mqtt-client")
                version = providers.gradleProperty("mqttClientVersion")
                    .getOrElse("0.1.0-SNAPSHOT")
            }
        }

        repositories {
            bitbucketPackagesUrl.orNull?.let { repositoryUrl ->
                maven {
                    name = "BitbucketPackages"
                    url = uri(repositoryUrl)
                    credentials {
                        username = bitbucketPackagesUsername.orNull
                        password = bitbucketPackagesToken.orNull
                    }
                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            } ?: providers.gradleProperty("mqttClientRepositoryUrl").orNull?.let { repositoryUrl ->
                maven {
                    name = "mqttClientPrivate"
                    url = uri(repositoryUrl)
                    credentials {
                        username = providers.gradleProperty("mqttClientRepositoryUsername").orNull
                        password = providers.gradleProperty("mqttClientRepositoryPassword").orNull
                    }
                }
            }
        }
    }
}
