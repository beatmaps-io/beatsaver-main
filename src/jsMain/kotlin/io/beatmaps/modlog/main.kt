package io.beatmaps.modlog

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.UserData
import io.beatmaps.WithRouterProps
import io.beatmaps.api.ModLogEntry
import io.beatmaps.common.ModLogOpType
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.State
import react.createRef
import react.dom.button
import react.dom.form
import react.dom.input
import react.dom.option
import react.dom.select
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.setState

external interface ModLogProps : WithRouterProps {
    var userData: UserData?
}

external interface ModLogState : State {
    var resultsKey: Any
    var mod: String?
    var user: String?
    var type: ModLogOpType?
}

class ModLog : RComponent<ModLogProps, ModLogState>() {
    private val resultsTable = createRef<HTMLElement>()

    private val modRef = createRef<HTMLInputElement>()
    private val userRef = createRef<HTMLInputElement>()
    private val typeRef = createRef<HTMLSelectElement>()

    override fun componentDidMount() {
        setPageTitle("ModLog")

        if (props.userData?.admin != true) {
            props.history.push("/")
        }

        updateFromURL()
    }

    override fun componentDidUpdate(prevProps: ModLogProps, prevState: ModLogState, snapshot: Any) {
        updateFromURL()
    }

    private fun updateFromURL() {
        val (mod, user, type) = URLSearchParams(props.location.search).let { u ->
            Triple(u.get("mod") ?: "", u.get("user") ?: "", ModLogOpType.fromName(u.get("type") ?: ""))
        }

        modRef.current?.value = mod
        userRef.current?.value = user
        typeRef.current?.value = type?.name ?: ""

        if (mod != state.mod || user != state.user || type != state.type) {
            setState {
                this.mod = mod
                this.user = user
                this.type = type
                resultsKey = Any()
            }
        }

        if (type != state.type) {
            typeRef.current?.value = type?.name ?: ""
        }
    }

    private val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<Array<ModLogEntry>>(
            "${Config.apibase}/modlog/$toLoad" + urlExtension(),
            generateConfig<String, Array<ModLogEntry>>(token.token)
        ).then {
            return@then it.data.toList()
        }
    }

    override fun RBuilder.render() {
        form {
            table("table table-dark table-striped-3 modlog") {
                thead {
                    tr {
                        th { +"Moderator" }
                        th { +"User" }
                        th { +"Map" }
                        th { +"Action" }
                        th { +"Time" }
                    }
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
                        td { }
                        td {
                            select("form-select") {
                                attrs.attributes["aria-label"] = "Type"
                                ref = typeRef

                                ModLogOpType.values().forEach {
                                    option {
                                        attrs.value = it.toString()
                                        attrs.selected = state.type == it
                                        +it.toString()
                                    }
                                }
                                option {
                                    attrs.value = ""
                                    attrs.selected = state.type == null
                                    +"All"
                                }
                            }
                        }
                        td {
                            button(type = ButtonType.submit, classes = "btn btn-primary") {
                                attrs.onClickFunction = {
                                    it.preventDefault()

                                    props.history.push("/modlog" + urlExtension())
                                }

                                +"Filter"
                            }
                        }
                    }
                }
                tbody {
                    ref = resultsTable
                    key = "modlogTable"

                    child(ModLogInfiniteScroll::class) {
                        attrs.resultsKey = state.resultsKey
                        attrs.rowHeight = 48.5
                        attrs.itemsPerPage = 30
                        attrs.container = resultsTable
                        attrs.loadPage = loadPage
                        attrs.childFilter = {
                            !it.hasClass("hiddenRow")
                        }
                        attrs.renderElement = InfiniteScrollElementRenderer {
                            modLogEntryRenderer {
                                attrs.entry = it
                                attrs.setUser = { modStr, userStr ->
                                    modRef.current?.value = modStr
                                    userRef.current?.value = userStr
                                    props.history.push("/modlog" + urlExtension())
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
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null },
            typeRef.current?.value?.let { if (it.isNotBlank()) "type=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }
}

class ModLogInfiniteScroll : InfiniteScroll<ModLogEntry>()

fun RBuilder.modlog(handler: ModLogProps.() -> Unit) =
    child(ModLog::class) {
        this.attrs(handler)
    }
