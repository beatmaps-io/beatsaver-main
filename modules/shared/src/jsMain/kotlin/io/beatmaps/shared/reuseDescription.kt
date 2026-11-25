package io.beatmaps.shared

import external.axiosGet
import io.beatmaps.Config
import io.beatmaps.api.SearchResponse
import io.beatmaps.globalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.select
import react.use
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLSelectElement
import web.window.window
import kotlin.js.Promise

external interface ReuseDescriptionProps : Props {
    var modalCallbacks: RefObject<ModalCallbacks>?
    var disabled: Boolean
    var excludeMapId: String?
    var currentDescription: () -> String
    var onDescriptionSelected: (String) -> Unit
}

val reuseDescriptionButton = fcmemo<ReuseDescriptionProps>("reuseDescriptionButton") { props ->
    val userData = use(globalContext)
    val (loading, setLoading) = useState(false)
    val (error, setError) = useState<String>()
    val selectRef = useRef<HTMLSelectElement>()

    fun handleError() {
        setLoading(false)
        setError("Could not load your maps. Please try again.")
    }

    fun openReuseDescription() {
        val userId = userData?.userId ?: return

        setLoading(true)
        setError(null)

        axiosGet<SearchResponse>(
            "${Config.apibase}/maps/uploader/$userId/0"
        ).then({ publishedResponse ->
            axiosGet<SearchResponse>(
                "${Config.apibase}/maps/wip/0"
            ).then({ unpublishedResponse ->
                setLoading(false)

                val publishedMaps = publishedResponse.data.docs.orEmpty()
                    .filter {
                        it.description.isNotBlank() && it.id != props.excludeMapId
                    }

                val unpublishedMaps = unpublishedResponse.data.docs.orEmpty()
                    .filter {
                        it.description.isNotBlank() && it.id != props.excludeMapId
                    }

                val maps = (unpublishedMaps + publishedMaps)
                    .distinctBy { it.id }

                val unpublishedIds = unpublishedMaps.map { it.id }.toSet()

                props.modalCallbacks?.current?.showDialog?.invoke(
                    ModalData(
                        "Reuse description",
                        bodyCallback = {
                            if (maps.isEmpty()) {
                                p {
                                    +"No published or unpublished maps with descriptions were found."
                                }
                            } else {
                                p {
                                    +"Select one of your maps to reuse its description."
                                }

                                select {
                                    className = ClassName("form-select")
                                    ref = selectRef

                                    maps.forEach { map ->
                                        option {
                                            value = map.id

                                            val status = if (map.id in unpublishedIds) {
                                                "[Unpublished]"
                                            } else {
                                                "[Published]"
                                            }

                                            +"$status ${map.name}"
                                        }
                                    }
                                }
                            }
                        },
                        buttons = if (maps.isEmpty()) {
                            listOf(ModalButton("Close"))
                        } else {
                            listOf(
                                ModalButton("Cancel"),
                                ModalButton("Use description", "primary") {
                                    val selected = maps.firstOrNull {
                                        it.id == selectRef.current?.value
                                    }

                                    if (selected == null) {
                                        Promise.resolve(false)
                                    } else {
                                        val confirmed = if (props.currentDescription().isBlank()) {
                                            true
                                        } else {
                                            window.asDynamic().confirm(
                                                "Replace your current description with the selected map's description?"
                                            ) as Boolean
                                        }

                                        if (confirmed) {
                                            props.onDescriptionSelected(selected.description)
                                        }

                                        Promise.resolve(confirmed)
                                    }
                                }
                            )
                        }
                    )
                )
            }, {
                handleError()
            })
        }, {
            handleError()
        })
    }

    if (userData?.userId != null) {
        div {
            className = ClassName("text-end")

            button {
                type = ButtonType.button
                className = ClassName("btn btn-link btn-sm p-0")
                disabled = props.disabled || loading
                onClick = {
                    openReuseDescription()
                }

                +(if (loading) "Loading..." else "Reuse description")
            }

            error?.let { message ->
                div {
                    className = ClassName("small text-danger")
                    +message
                }
            }
        }
    }
}
