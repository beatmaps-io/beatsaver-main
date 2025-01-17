import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    jvm {
        compilerOptions.jvmTarget = JvmTarget.JVM_21
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        withJava()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("com.appmattus.fixture:fixture:1.2.0")
            }
        }
    }
}
