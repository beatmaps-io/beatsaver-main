import io.miret.etienne.gradle.sass.CompileSass
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("multiplatform") version "1.5.30-RC"
    kotlin("plugin.serialization") version "1.5.30-RC"
    id("io.miret.etienne.sass") version "1.1.2"
    id("org.flywaydb.flyway") version "7.12.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
    application
}

val exposedVersion: String by project
val ktorVersion: String by project
group = "io.beatmaps"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://artifactory.kirkstall.top-cat.me") }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "15"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        withJava()
    }
    js(IR) {
        browser {
            binaries.executable()
            webpackTask {
                cssSupport.enabled = true
            }
            runTask {
                cssSupport.enabled = true
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            languageSettings.optIn("kotlin.io.path.ExperimentalPathApi")
            languageSettings.optIn("io.ktor.locations.KtorExperimentalLocationsAPI")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("io.ktor.util.KtorExperimentalAPI")
            languageSettings.optIn("kotlin.ExperimentalUnsignedTypes")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
            languageSettings.optIn("kotlinx.coroutines.DelicateCoroutinesApi")
            dependencies {
                api(kotlin("reflect", "1.5.30-RC"))

                // Core
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-html-builder:$ktorVersion")
                implementation("io.ktor:ktor-auth:$ktorVersion")
                implementation("io.ktor:ktor-locations:$ktorVersion")
                implementation("io.ktor:ktor-client-apache:$ktorVersion")
                implementation("io.ktor:ktor-websockets:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
                implementation("ch.qos.logback:logback-classic:1.2.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.5.1")
                implementation("io.jsonwebtoken:jjwt-impl:0.11.2")
                implementation("io.jsonwebtoken:jjwt-jackson:0.11.2")
                implementation("com.ToxicBakery.library.bcrypt:bcrypt:+")

                // Helpful
                implementation("org.valiktor:valiktor-core:0.12.0")
                implementation("io.github.keetraxx:recaptcha:0.5")
                implementation("de.nielsfalk.ktor:ktor-swagger:0.7.+")
                implementation("org.bouncycastle:bcprov-jdk15:1.46")

                // Metrics
                implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
                implementation("io.micrometer:micrometer-registry-influx:latest.release")

                // Database library
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

                // Database drivers
                implementation("org.postgresql:postgresql:42.1.4")
                implementation("io.lettuce:lettuce-core:6.0.1.RELEASE")
                implementation("com.github.JUtupe:ktor-rabbitmq:0.2.0")
                implementation("com.rabbitmq:amqp-client:5.9.0")

                // Serialization
                implementation("io.ktor:ktor-jackson:$ktorVersion")
                implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.6.1")
                implementation("io.ktor:ktor-serialization:$ktorVersion")

                // Multimedia
                implementation("org.jaudiotagger:jaudiotagger:2.0.1")
                implementation("net.coobird:thumbnailator:0.4.13")
                implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.6.1")
                implementation("org.sejda.imageio:webp-imageio:0.1.6")
                // implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
                implementation("com.tagtraum:ffsampledsp-complete:0.9.32")

                implementation("io.beatmaps:BeatMaps-Common:1.0.+")
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")

                runtimeOnly(files("BeatMaps-BeatSage-1.0-SNAPSHOT.jar"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.218-kotlin-1.5.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:17.0.2-pre.218-kotlin-1.5.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:17.0.2-pre.218-kotlin-1.5.21")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:5.2.0-pre.218-kotlin-1.5.21")
                implementation(npm("react-timeago", "5.2.0"))
                implementation(npm("react-dropzone", "11.2.4"))
                implementation(npm("react-dates", "21.8.0"))
                implementation(npm("react-google-recaptcha", "2.1.0"))
                implementation(npm("axios", "0.21.1"))
                implementation(npm("react-slider", "1.1.2"))
                implementation(npm("bootswatch", "4.5.3"))
                implementation(npm("bootstrap", "4.5.3"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}

application {
    mainClass.set("io.beatmaps.ServerKt")
}

flyway {
    url = "jdbc:postgresql://localhost:5432/beatmaps"
    user = "beatmaps"
    password = "insecure-password"
    val locs = mutableListOf("filesystem:$projectDir/src/commonMain/resources/db/migration")
    if (System.getenv("BUILD_NUMBER") == null) {
        // If running locally add test data
        locs.add("filesystem:$projectDir/src/commonMain/resources/db/test")
    }
    locations = locs.toTypedArray()
}

tasks.getByName<CompileSass>("compileSass") {
    dependsOn(tasks.getByName("kotlinNpmInstall"))

    outputDir = file("$buildDir/processedResources/jvm/main")
    setSourceDir(file("$projectDir/src/jvmMain/sass"))
    loadPath(file("$buildDir/js/node_modules"))

    style = compressed
}

tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
    outputFileName = "output.js"
    sourceMaps = true
    report = true
    args = mutableListOf("--config", "..\\..\\..\\..\\webpack.config.extra.js", "--merge")
}

tasks.withType<AbstractCopyTask> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.getByName<Jar>("jvmJar") {
    dependsOn(tasks.getByName("jsBrowserProductionWebpack"), tasks.getByName("compileSass"))
    val jsBrowserProductionWebpack = tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack")

    listOf(jsBrowserProductionWebpack.outputFileName, jsBrowserProductionWebpack.outputFileName + ".map", "modules.js", "modules.js.map").forEach {
        from(File(jsBrowserProductionWebpack.destinationDirectory, it))
    }
}

tasks.getByName<JavaExec>("run") {
    dependsOn(tasks.getByName<Jar>("jvmJar"))
    classpath(tasks.getByName<Jar>("jvmJar"))
}

distributions {
    main {
        contents {
            from("$buildDir/libs") {
                rename("${rootProject.name}-jvm", rootProject.name)
                into("lib")
            }
        }
    }
}
