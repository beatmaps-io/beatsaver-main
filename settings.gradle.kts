rootProject.name = "BeatMaps"

if (File("../beatsaver-common-mp").exists()) {
    includeBuild("../beatsaver-common-mp") {
        dependencySubstitution {
            substitute(module("io.beatmaps:BeatMaps-CommonMP")).using(project(":"))
        }
    }
}

file("modules")
    .listFiles()!!
    .filter(File::isDirectory)
    .forEach { directory ->
        val name = directory.name

        include(name)

        project(":$name").apply {
            this.projectDir = directory
        }
    }
