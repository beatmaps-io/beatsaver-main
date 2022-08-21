package io.beatmaps.modlog

import external.TimeAgo
import external.axiosGet
import io.beatmaps.UserData
import io.beatmaps.api.ModLogEntry
import io.beatmaps.common.Config
import io.beatmaps.shared.mapTitle
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.button
import react.dom.form
import react.dom.input
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.router.dom.RouteResultHistory
import react.setState

external interface ModLogProps : RProps {
    var history: RouteResultHistory
    var userData: UserData?
    var mod: String?
    var user: String?
}

external interface ModLogState : RState {
    var entries: List<ModLogEntry>?
}

class ModLog : RComponent<ModLogProps, ModLogState>() {
    private val modRef = createRef<HTMLInputElement>()
    private val userRef = createRef<HTMLInputElement>()

    override fun componentWillMount() {
        setState {
            entries = listOf()
        }
    }

    override fun componentDidMount() {
        setPageTitle("ModLog")

        if (props.userData?.admin != true) {
            props.history.push("/")
        }

        modRef.current?.value = props.mod ?: ""
        userRef.current?.value = props.user ?: ""

        loadPage(0)
    }

    override fun componentWillReceiveProps(nextProps: ModLogProps) {
        modRef.current?.value = nextProps.mod ?: ""
        userRef.current?.value = nextProps.user ?: ""

        if (props.mod != nextProps.mod || props.user != nextProps.user) {
            loadPage(0)
        }
    }

    private fun loadPage(page: Long) {
        if (page == 0L) {
            setState {
                entries = listOf()
            }
        }

        axiosGet<Array<ModLogEntry>>("${Config.apibase}/modlog/$page" + urlExtension()).then {
            setState {
                entries = entries?.plus(it.data)
            }
        }
    }

    override fun RBuilder.render() {
        form {
            table("table table-dark table-striped modlog") {
                thead {
                    tr {
                        th { +"Moderator" }
                        th { +"User" }
                        th { +"Map" }
                        th { +"Action" }
                        th { +"Time" }
                    }
                }
                tbody {
                    tr {
                        td {
                            input(InputType.text, classes = "form-control") {
                                attrs.placeholder = "Moderator"
                                attrs.attributes["aria-label"] = "Moderator"
                                ref = modRef
                            }
                        }
                        td {
                            input(InputType.text, classes = "form-control") {
                                attrs.placeholder = "User"
                                attrs.attributes["aria-label"] = "User"
                                ref = userRef
                            }
                        }
                        td {
                            attrs.colSpan = "4"
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                attrs.onClickFunction = {
                                    it.preventDefault()

                                    props.history.push("/modlog" + urlExtension())
                                }

                                +"Filter"
                            }
                        }
                    }

                    state.entries?.forEach {
                        tr {
                            td { +it.moderator }
                            td { +it.user }
                            td {
                                if (it.map != null) mapTitle {
                                    attrs.title = it.map.name
                                    attrs.mapKey = it.map.id
                                }
                            }
                            td { +it.type.name }
                            td {
                                TimeAgo.default {
                                    attrs.date = it.time.toString()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun urlExtension(): String {
        val params = listOfNotNull(
            modRef.current?.value?.let { if (it.isNotBlank()) "mod=$it" else null },
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }
}

fun RBuilder.modlog(handler: ModLogProps.() -> Unit): ReactElement {
    return child(ModLog::class) {
        this.attrs(handler)
    }
}
