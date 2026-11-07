// The zephyr-java AAR: the Java sources of the Maven build plus libzephyr-java.so per ABI.
// Build: ./gradlew assembleRelease publishReleasePublicationToLocalRepository
plugins {
    id("com.android.library") version "9.5.0"
    `maven-publish`
}

android {
    namespace = "io.github.lokinotfound.zephyrjava"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 24 // final; Jackson 2.13.5 is the last line for API 24 and 25
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets["main"].java.srcDirs("../src/main/java")
    // arm64-v8a, armeabi-v7a, x86_64 from bin/build_android.sh (16 KB aligned at link time)
    sourceSets["main"].jniLibs.srcDirs("../build/android")

    packaging {
        // store the libraries uncompressed and page aligned in the APK, loaded in place
        jniLibs.useLegacyPackaging = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // the same versions as pom.xml (Jackson 2.13.5: the last line for Android below API 26)
    api("com.fasterxml.jackson.core:jackson-core:2.13.5")
    api("com.fasterxml.jackson.core:jackson-databind:2.13.5")
    api("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    // ZephyrWalletRpc's ZMQ listener needs jeromq; an app that uses that listener adds jeromq itself
    compileOnly("org.zeromq:jeromq:0.5.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("GROUP").get()
            artifactId = providers.gradleProperty("ARTIFACT_ID").get()
            version = providers.gradleProperty("VERSION_NAME").get()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "local"
            // build/repo; bin/make_maven_repo.sh publishes into dist/<version>/maven with -PrepoDir=
            val repoDir = providers.gradleProperty("repoDir")
            url = if (repoDir.isPresent) uri(repoDir.get()) else uri(layout.buildDirectory.dir("repo"))
        }
    }
}
