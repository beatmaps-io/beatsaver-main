import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("multiplatform") version "1.4.31"
    kotlin("plugin.serialization") version "1.4.31"
    application
}

val exposedVersion: String by project
val ktorVersion: String by project
group = "io.beatmaps"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-js-wrappers") }
    maven { url = uri("https://dl.bintray.com/kotlin/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/ktor") }
    maven { url = uri("https://jitpack.io") }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "15"
            kotlinOptions.jdkHome = "C:\\Users\\micro\\.jdks\\openjdk-15.0.2"
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.1.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
                implementation("io.beatmaps:CommonMP")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            languageSettings.useExperimentalAnnotation("kotlin.io.path.ExperimentalPathApi")
            languageSettings.useExperimentalAnnotation("io.ktor.locations.KtorExperimentalLocationsAPI")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.flow.FlowPreview")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
            dependencies {
                // Core
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-html-builder:$ktorVersion")
                implementation("io.ktor:ktor-auth:$ktorVersion")
                implementation("io.ktor:ktor-locations:$ktorVersion")
                implementation("io.ktor:ktor-client-apache:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
                implementation("ch.qos.logback:logback-classic:1.2.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.4.2")
                implementation("io.jsonwebtoken:jjwt-impl:0.11.2")
                implementation("io.jsonwebtoken:jjwt-jackson:0.11.2")

                // Helpful
                implementation("org.valiktor:valiktor-core:0.12.0")
                implementation("io.github.keetraxx:recaptcha:0.5")
                implementation("com.github.nielsfalk:ktor-swagger:0.7.0")
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

                // Cloud storage
                implementation("com.backblaze.b2:b2-sdk-core:4.0.0")
                implementation("com.backblaze.b2:b2-sdk-httpclient:4.0.0")

                // Multimedia
                implementation("org.jaudiotagger:jaudiotagger:2.0.1")
                implementation("net.coobird:thumbnailator:0.4.13")
                implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.6.1")
                implementation("org.sejda.imageio:webp-imageio:0.1.6")
                //implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
                implementation("com.tagtraum:ffsampledsp-complete:0.9.32")

                implementation("io.beatmaps:Common")
                implementation("io.beatmaps:CommonMP")

                runtimeOnly(files("BeatMaps-BeatSage-1.0-SNAPSHOT.jar"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains:kotlin-react:16.13.1-pre.113-kotlin-1.4.0")
                implementation("org.jetbrains:kotlin-react-dom:16.13.1-pre.113-kotlin-1.4.0")
                implementation("org.jetbrains:kotlin-react-router-dom:5.1.2-pre.113-kotlin-1.4.0")
                implementation(npm("react-timeago", "5.2.0"))
                implementation(npm("react-dropzone", "11.2.4"))
                implementation(npm("react-dates", "21.8.0"))
                implementation(npm("react-google-recaptcha", "2.1.0"))
                implementation(npm("axios", "0.21.1"))
                implementation(npm("react-slider", "1.1.2"))
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
    mainClassName = "io.beatmaps.ServerKt"
}

tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
    outputFileName = "output.js"
}

tasks.getByName<Jar>("jvmJar") {
    dependsOn(tasks.getByName("jsBrowserProductionWebpack"))
    val jsBrowserProductionWebpack = tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack")
    from(File(jsBrowserProductionWebpack.destinationDirectory, jsBrowserProductionWebpack.outputFileName))
}

tasks.getByName<JavaExec>("run") {
    dependsOn(tasks.getByName<Jar>("jvmJar"))
    classpath(tasks.getByName<Jar>("jvmJar"))
    setExecutable("C:\\Users\\micro\\.jdks\\openjdk-15.0.2\\bin\\java")
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