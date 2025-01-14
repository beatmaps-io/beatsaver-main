plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "16"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    targets.all {
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
    js(IR).browser()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
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
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")

                implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.23.0-pre.736")
                implementation(npm("react-timeago", "5.2.0"))
                implementation(npm("react-dropzone", "11.2.4"))
                implementation(npm("react-beautiful-dnd", "13.1.0"))
                implementation(npm("react-dates", "21.8.0"))
                implementation(npm("react-google-recaptcha", "2.1.0"))
                implementation(npm("@marsidev/react-turnstile", "1.0.2"))
                implementation(npm("axios", "0.21.1"))
                implementation(npm("react-slider", "1.1.2"))
                implementation(npm("bootswatch", "5.1.3"))
                implementation(npm("bootstrap", "5.1.3"))
            }
        }
    }
}
