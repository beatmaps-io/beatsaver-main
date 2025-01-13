import external.component
import js.import.import
import react.ComponentClass
import react.ExoticComponent
import react.Props

external interface TestplayModule {
    val recentTestplays: ComponentClass<Props>
}

data class TestPlayExotics(
    val recentTestplays: ExoticComponent<Props>
)

val testplayModule by lazy {
    import<TestplayModule>("./BeatMaps-testplay").let { promise ->
        promise.then { console.log(it) }
        TestPlayExotics(
            promise.component { it.recentTestplays }
        )
    }
}
