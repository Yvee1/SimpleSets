import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.openrndr.template"
version = "0.4.0"

val applicationMainClass = "MainKt"

/**  ## additional ORX features to be added to this project */
val orxFeatures = setOf<String>(
    "orx-color",
    "orx-compositor",
    "orx-fx",
    "orx-noise",
    "orx-shade-styles",
    "orx-shapes",
    "orx-triangulation",
)

// ------------------------------------------------------------------------------------------------------------------ //

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    kotlin("multiplatform") version "1.8.20-RC2"
    kotlin("plugin.serialization") version "1.8.20"
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.runtime)
    alias(libs.plugins.gitarchive.tomarkdown).apply(false)
}

repositories {
    mavenLocal()
    mavenCentral()
}

val openrndrVersion = libs.versions.openrndr.get()
val orxVersion = libs.versions.orx.get()
val os = if (project.hasProperty("targetPlatform")) {
    val supportedPlatforms = setOf("windows", "macos", "linux-x64", "linux-arm64")
    val platform: String = project.property("targetPlatform") as String
    if (platform !in supportedPlatforms) {
        throw IllegalArgumentException("target platform not supported: $platform")
    } else {
        platform
    }
} else when (OperatingSystem.current()) {
    OperatingSystem.WINDOWS -> "windows"
    OperatingSystem.MAC_OS -> when (val h = DefaultNativePlatform("current").architecture.name) {
        "aarch64", "arm-v8" -> "macos-arm64"
        else -> "macos"
    }
    OperatingSystem.LINUX -> when (val h = DefaultNativePlatform("current").architecture.name) {
        "x86-64" -> "linux-x64"
        "aarch64" -> "linux-arm64"
        else -> throw IllegalArgumentException("architecture not supported: $h")
    }
    else -> throw IllegalArgumentException("os not supported")
}
fun openrndr(module: String) = "org.openrndr:openrndr-$module:$openrndrVersion"
fun orx(module: String) = "org.openrndr.extra:$module:$orxVersion"
fun openrndrNatives(module: String) = "org.openrndr:openrndr-$module-natives-$os:$openrndrVersion"

kotlin {
    js(IR) {
        browser {
        }
        binaries.executable()
    }

    js("webworker", IR) {
        binaries.executable()
        browser{
            commonWebpackConfig {
                outputFileName = "worker.js"
            }
        }
    }

    jvm {

    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(openrndr("application"))
                for (feature in orxFeatures) {
                    implementation(orx(feature))
                }

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(orx("orx-gui"))
                runtimeOnly(openrndr("gl3"))
                runtimeOnly(openrndrNatives("gl3"))
                implementation(openrndr("openal"))
                runtimeOnly(openrndrNatives("openal"))
                implementation(openrndr("svg"))
                implementation(openrndr("animatable"))
                implementation(openrndr("extensions"))
                implementation(openrndr("filter"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.junit.jupiter:junit-jupiter:5.8.1")
            }
        }

        val jsMain by getting {
            dependencies {
                // React, React DOM + Wrappers
                implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.527"))
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")

                // Kotlin React Emotion (CSS)
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion")

                implementation(openrndr("dds"))
                implementation(openrndr("draw"))
                implementation(openrndr("webgl"))
            }
        }

        val webworkerMain by getting {
            dependencies {

            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------ //

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

// ------------------------------------------------------------------------------------------------------------------ //

project.setProperty("mainClassName", applicationMainClass)
tasks {
    named<ShadowJar>("shadowJar") {
        manifest {
            attributes["Main-Class"] = applicationMainClass
        }
        minimize {
            exclude(dependency("org.openrndr:openrndr-gl3:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        }
    }
    named<org.beryx.runtime.JPackageTask>("jpackage") {
        doLast {
            when (OperatingSystem.current()) {
                OperatingSystem.WINDOWS, OperatingSystem.LINUX -> {
                    copy {
                        from("data") {
                            include("**/*")
                        }
                        into("build/jpackage/openrndr-application/data")
                    }
                }
                OperatingSystem.MAC_OS -> {
                    copy {
                        from("data") {
                            include("**/*")
                        }
                        into("build/jpackage/openrndr-application.app/data")
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------------ //

tasks.register<Zip>("jpackageZip") {
    archiveFileName.set("openrndr-application.zip")
    from("$buildDir/jpackage") {
        include("**/*")
    }
}
tasks.findByName("jpackageZip")?.dependsOn("jpackage")

// ------------------------------------------------------------------------------------------------------------------ //

runtime {
    jpackage {
        imageName = "openrndr-application"
        skipInstaller = true
        if (OperatingSystem.current() == OperatingSystem.MAC_OS) jvmArgs.add("-XstartOnFirstThread")
    }
    options.set(listOf("--strip-debug", "--compress", "1", "--no-header-files", "--no-man-pages"))
    modules.set(listOf("jdk.unsupported", "java.management"))
}

// ------------------------------------------------------------------------------------------------------------------ //

tasks.register<org.openrndr.extra.gitarchiver.GitArchiveToMarkdown>("gitArchiveToMarkDown") {
    historySize.set(20)
}
