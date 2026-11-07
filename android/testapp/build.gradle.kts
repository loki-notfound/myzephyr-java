// Build-only test app for the zephyr-java AAR: loads the native library on a device, validates
// address vectors natively, and carries one instrumented test (offline in-memory wallet).
// Build: ./gradlew :testapp:assembleDebug :testapp:assembleDebugAndroidTest
// Run (device): adb install, then adb shell am instrument -w <test package>/android.test.InstrumentationTestRunner
plugins {
    id("com.android.application") // AGP 9.5.0, on the class path through the root project
}

android {
    namespace = "io.github.lokinotfound.zephyrjava.testapp"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.lokinotfound.zephyrjava.testapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        // the platform's JUnit 3 runner: AndroidX is not used anywhere in zephyr-java
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    // the platform test classes (InstrumentationTestRunner, AndroidTestCase, junit.framework) are
    // optional libraries of the SDK since API 28
    useLibrary("android.test.runner")
    useLibrary("android.test.base")

    packaging {
        // the libraries stay uncompressed and 16 KB aligned in the APK
        jniLibs.useLegacyPackaging = false
        // httpclient5, httpcore5 and httpcore5-h2 each carry this Maven metadata file and AGP refuses
        // to merge duplicates; every app that uses the AAR needs this line (user guide)
        resources.excludes += "/META-INF/DEPENDENCIES"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":"))
}
