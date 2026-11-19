// The zephyr-java AAR: the Java sources of the Maven build plus libzephyr-java.so per ABI.
// Build: ./gradlew assembleRelease publishReleasePublicationToLocalRepository (into build/repo:
// the AAR, its sources jar, POM and Gradle module metadata, which bin/package_release.sh copies to
// dist/<version>/ for the release and bin/make_central_bundle.sh for Maven Central)
plugins {
    id("com.android.library") version "9.5.0"
    `maven-publish`
}

// zephyr-java.properties as the Maven build filters it (src/main/resources is filtered there):
// ${project.version} becomes VERSION_NAME, the only property it uses; ZephyrUtils.getVersion() and
// ZephyrTls.getShippedCertificatesDate() read the file
abstract class ZephyrJavaProperties : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val template: RegularFileProperty

    @get:Input
    abstract val version: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val text = template.get().asFile.readText(Charsets.UTF_8).replace("\${project.version}", version.get())
        outputDir.get().file("zephyr-java.properties").asFile.writeText(text, Charsets.UTF_8)
    }
}

val zephyrJavaProperties = tasks.register<ZephyrJavaProperties>("zephyrJavaProperties") {
    template.set(layout.projectDirectory.file("../src/main/resources/zephyr-java.properties"))
    version.set(providers.gradleProperty("VERSION_NAME"))
    outputDir.set(layout.buildDirectory.dir("generated/zephyr-java-resources"))
}

android {
    namespace = "dev.lateo.zephyrjava"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 24 // final; Jackson 2.13.5 is the last line for API 24 and 25
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets["main"].java.srcDirs("../src/main/java")
    // arm64-v8a, armeabi-v7a, x86_64 from bin/build_android.sh (16 KB aligned at link time)
    sourceSets["main"].jniLibs.srcDirs("../build/android")
    // Java resources in the AAR's classes.jar: the shipped root certificates, byte for byte
    // (zephyr/common/cacert.pem); zephyr-java.properties comes from the task above
    sourceSets["main"].resources.srcDirs("../src/main/resources-raw")

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

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(zephyrJavaProperties, ZephyrJavaProperties::outputDir)
    }
}

dependencies {
    // the same versions as pom.xml (Jackson 2.13.5: the last line for Android below API 26)
    api("com.fasterxml.jackson.core:jackson-core:2.13.5")
    api("com.fasterxml.jackson.core:jackson-databind:2.13.5")
    api("org.apache.httpcomponents.client5:httpclient5:5.2.1")
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
            // the fields Maven Central requires, the same values as pom.xml's (bin/make_central_bundle.sh
            // checks both POMs)
            pom {
                name.set("Zephyr Java Library for Android")
                description.set("Java bindings for the Zephyr wallet core (zephyr-cpp), the AAR for Android with the natives for arm64-v8a, armeabi-v7a and x86_64. An independent project, not affiliated with ZephyrProtocol.")
                url.set("https://github.com/loki-notfound/myzephyr-java")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    }
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://www.mozilla.org/MPL/2.0/")
                        distribution.set("repo")
                        comments.set("The root certificates shipped in the library, zephyr/common/cacert.pem (Mozilla's CA list in curl's PEM form)")
                    }
                }
                developers {
                    developer {
                        name.set("loki-notfound")
                        email.set("loki@lateo.dev")
                        url.set("https://github.com/loki-notfound")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/loki-notfound/myzephyr-java.git")
                    developerConnection.set("scm:git:ssh://git@github.com/loki-notfound/myzephyr-java.git")
                    url.set("https://github.com/loki-notfound/myzephyr-java")
                }
            }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
