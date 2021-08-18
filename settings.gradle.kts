rootProject.name = "BeatMaps"

if (File("../beatsaver-common").exists()) {
    includeBuild("../beatsaver-common") {
        dependencySubstitution {
            substitute(module("io.beatmaps:BeatMaps-Common")).using(project(":"))
        }
    }
}

if (File("../beatsaver-common-mp").exists()) {
    includeBuild("../beatsaver-common-mp") {
        dependencySubstitution {
            substitute(module("io.beatmaps:BeatMaps-CommonMP")).using(project(":"))
        }
    }
}
