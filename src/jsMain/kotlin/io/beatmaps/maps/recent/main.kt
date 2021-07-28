package io.beatmaps.maps.recent

import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapTestplay
import io.beatmaps.api.MapVersion
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import kotlinx.browser.window
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.*
import react.ref
import react.setState

data class RecentTestplaysState(var testplays: List<RecentTestplay> = listOf(), var loading: Boolean = false, var page: Int = 0) : RState
data class RecentTestplay(val mapDetail: MapDetail, val version: MapVersion, val testplay: MapTestplay)

@JsExport
class RecentTestplays : RComponent<RProps, RecentTestplaysState>() {
    private val modalRef = createRef<ModalComponent>()

    init {
        state = RecentTestplaysState()
    }

    override fun componentDidMount() {
        loadNextPage()
    }

    private fun loadNextPage() {
        if (state.loading)
            return

        setState {
            loading = true
        }

        window.fetch("/api/testplay/recent/0").then {
            it.text()
        }.then({
            val response = Json.decodeFromString<Array<MapDetail>>(it)

            val testplaysLocal = response.flatMap { m ->
                m.versions.flatMap { v ->
                    v.testplays?.map { tp ->
                        RecentTestplay(m, v, tp)
                    } ?: listOf()
                }
            }

            setState {
                page++
                loading = testplays.isEmpty()
                testplays = state.testplays.plus(testplaysLocal).sortedWith(
                    compareBy<RecentTestplay> { tp -> tp.testplay.feedback?.isEmpty() == false }
                        .thenByDescending { tp -> tp.testplay.createdAt }
                )
            }
        }) {
            setState {
                loading = false
            }
        }
    }

    override fun RBuilder.render() {
        modal {
            ref = modalRef
        }

        table("table table-dark search-results") {
            tbody {
                state.testplays.forEach { rt ->
                    recentTestplayRow {
                        map = rt.mapDetail
                        version = rt.version
                        feedback = rt.testplay.feedback
                        time = rt.testplay.feedbackAt.toString()
                        modal = modalRef
                    }
                }
            }
        }
    }
}

fun RBuilder.recentTestplays(handler: RProps.() -> Unit): ReactElement {
    return child(RecentTestplays::class) {
        this.attrs(handler)
    }
}