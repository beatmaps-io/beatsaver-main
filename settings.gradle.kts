rootProject.name = "BeatMaps"

includeBuild("../Common") {
    dependencySubstitution {
        substitute(module("io.beatmaps:Common")).using(project(":"))
    }
}
