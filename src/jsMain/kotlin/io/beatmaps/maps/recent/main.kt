package io.beatmaps.maps.recent

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapTestplay
import io.beatmaps.api.MapVersion
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.modal
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.table
import react.dom.tbody
import react.ref
import react.setState

external interface RecentTestplaysState : State {
    var testplays: List<RecentTestplay>?
    var loading: Boolean?
    var page: Int?
}

data class RecentTestplay(val mapDetail: MapDetail, val version: MapVersion, val testplay: MapTestplay)

class RecentTestplays : RComponent<Props, RecentTestplaysState>() {
    private val modalRef = createRef<ModalComponent>()

    override fun componentDidMount() {
        loadNextPage()
    }

    private fun loadNextPage() {
        if (state.loading == true) return

        setState {
            loading = true
        }

        axiosGet<Array<MapDetail>>(
            "${Config.apibase}/testplay/recent/0"
        ).then({
            val testplaysLocal = it.data.flatMap { m ->
                m.versions.flatMap { v ->
                    v.testplays?.map { tp ->
                        RecentTestplay(m, v, tp)
                    } ?: listOf()
                }
            }

            setState {
                page = (page ?: 0) + 1
                loading = testplaysLocal.isEmpty()
                testplays = (state.testplays ?: listOf()).plus(testplaysLocal).sortedWith(
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
                state.testplays?.forEach { rt ->
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

fun RBuilder.recentTestplays(handler: Props.() -> Unit) =
    child(RecentTestplays::class) {
        this.attrs(handler)
    }
