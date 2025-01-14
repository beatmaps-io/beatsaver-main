plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
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
                implementation(project(":shared"))
                implementation("io.beatmaps:BeatMaps-CommonMP:1.0.+")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-extensions:1.0.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom-legacy:18.3.1-pre.736")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-router-dom:6.23.0-pre.736")
            }
        }
    }
}
