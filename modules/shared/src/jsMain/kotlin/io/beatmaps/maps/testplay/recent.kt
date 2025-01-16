package io.beatmaps.maps.testplay

import external.component
import js.import.importAsync
import react.ComponentClass
import react.ExoticComponent
import react.Props

external interface NewFeedbackProps : Props {
    var hash: String
}

external interface TestplayModule {
    val recentTestplays: ComponentClass<Props>
    val newFeedback: ComponentClass<NewFeedbackProps>
}

data class TestPlayExotics(
    val recentTestplays: ExoticComponent<Props>,
    val newFeedback: ExoticComponent<NewFeedbackProps>
)

val testplayModule = importAsync<TestplayModule>("./BeatMaps-testplay").let { promise ->
    TestPlayExotics(
        promise.component { it.recentTestplays },
        promise.component { it.newFeedback }
    )
}
