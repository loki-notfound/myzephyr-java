// Android packaging of zephyr-java: the AAR (this project) and a build-only test app (:testapp).
// Maven stays the primary build of the Java library; this build compiles the same sources
// (../src/main/java) and packages the native libraries of bin/build_android.sh (../build/android).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "zephyr-java"
