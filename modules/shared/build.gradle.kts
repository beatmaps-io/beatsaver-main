import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_21
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")

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
