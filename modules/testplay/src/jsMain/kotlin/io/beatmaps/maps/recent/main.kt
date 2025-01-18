package io.beatmaps.maps.recent

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapTestplay
import io.beatmaps.api.MapVersion
import io.beatmaps.setPageTitle
import io.beatmaps.shared.ModalCallbacks
import io.beatmaps.shared.modal
import io.beatmaps.shared.modalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.useEffectOnce
import react.useRef
import react.useState
import web.cssom.ClassName

data class RecentTestplay(val mapDetail: MapDetail, val version: MapVersion, val testplay: MapTestplay)

val recentTestplays = fcmemo<Props>("RecentTestplays") { _ ->
    val loading = useRef(false)
    val (testplays, setTestplays) = useState(emptyList<RecentTestplay>())
    val page = useRef(0)
    val modalRef = useRef<ModalCallbacks>()

    fun loadNextPage() {
        if (loading.current == true) return

        loading.current = true

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

            page.current = (page.current ?: 0) + 1
            loading.current = testplaysLocal.isEmpty()
            setTestplays(
                testplays.plus(testplaysLocal).sortedWith(
                    compareBy<RecentTestplay> { tp -> tp.testplay.feedback?.isEmpty() == false }
                        .thenByDescending { tp -> tp.testplay.createdAt }
                )
            )
        }) {
            loading.current = false
        }
    }

    useEffectOnce {
        setPageTitle("Testplays")
        loadNextPage()
    }

    modal {
        callbacks = modalRef
    }

    modalContext.Provider {
        value = modalRef

        table {
            className = ClassName("table table-dark search-results")
            tbody {
                testplays.forEach { rt ->
                    recentTestplayRow {
                        map = rt.mapDetail
                        version = rt.version
                        feedback = rt.testplay.feedback
                        time = rt.testplay.feedbackAt.toString()
                    }
                }
            }
        }
    }
}
