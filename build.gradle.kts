import io.miret.etienne.gradle.sass.CompileSass
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("multiplatform") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    id("io.miret.etienne.sass") version "1.1.2"
    id("org.flywaydb.flyway") version "9.2.2"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
    application
}

val exposedVersion: String by project
val ktorVersion: String by project
val myndocsOauthVersion: String by project
group = "io.beatmaps"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://artifactory.kirkstall.top-cat.me") }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "16"
            kotlinOptions.freeCompilerArgs = listOf("-XXLanguage:+NewInference")
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
                cssSupport {
                    enabled.set(true)
                }
            }
            runTask {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport {
                        enabled.set(true)
                    }
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
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
            with(languageSettings) {
                optIn("kotlin.io.path.ExperimentalPathApi")
                optIn("io.ktor.server.locations.KtorExperimentalLocationsAPI")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.ExperimentalUnsignedTypes")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlinx.coroutines.DelicateCoroutinesApi")
            }
            dependencies {
                api(kotlin("reflect", "1.9.20"))

                // Core
                implementation("io.ktor:ktor-utils:$ktorVersion")
                implementation("io.ktor:ktor-server-core:$ktorVersion")
                implementation("io.ktor:ktor-server-netty:$ktorVersion")
                implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
                implementation("io.ktor:ktor-server-auth:$ktorVersion")
                implementation("io.ktor:ktor-server-locations:$ktorVersion")
                implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
                implementation("io.ktor:ktor-server-conditional-headers:$ktorVersion")
                implementation("io.ktor:ktor-client-apache:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
                implementation("ch.qos.logback:logback-classic:1.5.6")
                implementation("io.github.oshai:kotlin-logging:6.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")
                implementation("io.jsonwebtoken:jjwt-impl:0.11.2")
                implementation("io.jsonwebtoken:jjwt-jackson:0.11.2")
                implementation("com.ToxicBakery.library.bcrypt:bcrypt:+")

                // Helpful
                implementation("org.valiktor:valiktor-core:0.12.0")
                implementation("io.github.keetraxx:recaptcha:0.5")
                implementation("de.nielsfalk.ktor:ktor-swagger:0.8.17")
                implementation("org.bouncycastle:bcprov-jdk15:1.46")

                // Database library
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
                implementation("com.zaxxer:HikariCP:3.4.2")
                implementation("org.flywaydb:flyway-core:9.2.2")

                // Database drivers
                implementation("org.postgresql:postgresql:42.5.0")
                implementation("pl.jutupe:ktor-rabbitmq:0.5.19")
                implementation("com.rabbitmq:amqp-client:5.9.0")
                implementation("org.litote.kmongo:kmongo-serialization:4.9.0")

                // Serialization
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
                implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.6.1")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

                // Multimedia
                implementation("org.jaudiotagger:jaudiotagger:2.0.1")
                implementation("net.coobird:thumbnailator:0.4.13")
                implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.6.1")
                implementation("org.sejda.imageio:webp-imageio:0.1.6")
                // implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
                implementation("com.tagtraum:ffsampledsp-complete:0.9.32")

                implementation("com.amazonaws:aws-java-sdk-s3:1.12.217")

                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")

                // oauth2
                implementation("nl.myndocs:oauth2-server-core:$myndocsOauthVersion")
                implementation("nl.myndocs:oauth2-server-ktor:$myndocsOauthVersion")
                // In memory dependencies
                implementation("nl.myndocs:oauth2-server-device-code-store-inmemory:$myndocsOauthVersion")

                runtimeOnly(files("BeatMaps-BeatSage-1.0-SNAPSHOT.jar"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))

                implementation("io.ktor:ktor-server-test-host:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
                implementation("com.microsoft.playwright:playwright:1.39.0")
                implementation("com.appmattus.fixture:fixture:1.2.0")
                implementation("net.lingala.zip4j:zip4j:2.11.6-SNAPSHOT")
            }
        }
        val jsMain by getting {
            with(languageSettings) {
                optIn("kotlin.js.ExperimentalJsExport")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.serialization.ExperimentalSerializationApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            }
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.23.0-pre.736")
                implementation(npm("react-timeago", "5.2.0"))
                implementation(npm("react-dropzone", "11.2.4"))
                implementation(npm("react-beautiful-dnd", "13.1.0"))
                implementation(npm("react-dates", "21.8.0"))
                implementation(npm("react-google-recaptcha", "2.1.0"))
                implementation(npm("axios", "0.21.1"))
                implementation(npm("react-slider", "1.1.2"))
                implementation(npm("bootswatch", "5.1.3"))
                implementation(npm("bootstrap", "5.1.3"))
                implementation(devNpm("webpack-bundle-analyzer", "4.6.1"))
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

    outputDir = file("$buildDir/processedResources/jvm/main/assets")
    setSourceDir(file("$projectDir/src/jvmMain/sass"))
    loadPath(file("$buildDir/js/node_modules"))

    style = compressed
}

tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack") {
    mainOutputFileName.set("output.js")
    sourceMaps = true
    outputDirectory.set(layout.buildDirectory.file("processedResources/jvm/main/assets").get().asFile)
}

tasks.withType<AbstractCopyTask> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = true
}

tasks.getByName<Jar>("jvmJar") {
    dependsOn(tasks.getByName("jsBrowserProductionWebpack"), tasks.getByName("compileSass"))
    val jsBrowserProductionWebpack = tasks.getByName<KotlinWebpack>("jsBrowserProductionWebpack")

    from(jsBrowserProductionWebpack.outputDirectory.get())
    listOf(jsBrowserProductionWebpack.mainOutputFileName.get(), jsBrowserProductionWebpack.mainOutputFileName.get() + ".map", "modules.js", "modules.js.map").forEach {
        from(File(jsBrowserProductionWebpack.outputDirectory.get().asFile, it))
    }
}

tasks.getByName<Test>("jvmTest") {
    dependsOn(tasks.getByName("jsBrowserProductionWebpack"), tasks.getByName("compileSass"))
}

ktlint {
    version.set("0.50.0")
    reporters {
        reporter(ReporterType.CHECKSTYLE)
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
