package io.beatmaps.modlog

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.UserData
import io.beatmaps.WithRouterProps
import io.beatmaps.api.ModLogEntry
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScroll
import io.beatmaps.shared.InfiniteScrollElementRenderer
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTableSectionElement
import org.w3c.dom.url.URLSearchParams
import react.RBuilder
import react.RComponent
import react.State
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
import react.setState

external interface ModLogProps : WithRouterProps {
    var userData: UserData?
}

external interface ModLogState : State {
    var resultsKey: Any
    var mod: String?
    var user: String?
}

class ModLog : RComponent<ModLogProps, ModLogState>() {
    private val resultsTable = createRef<HTMLTableSectionElement>()

    private val modRef = createRef<HTMLInputElement>()
    private val userRef = createRef<HTMLInputElement>()

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
        val (mod, user) = URLSearchParams(props.location.search).let { u ->
            (u.get("mod") ?: "") to (u.get("user") ?: "")
        }

        modRef.current?.value = mod
        userRef.current?.value = user

        if (mod != state.mod || user != state.user) {
            setState {
                this.mod = mod
                this.user = user
                resultsKey = Any()
            }
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
                        td {
                            attrs.colSpan = "3"
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
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }
}

class ModLogInfiniteScroll : InfiniteScroll<ModLogEntry>()

fun RBuilder.modlog(handler: ModLogProps.() -> Unit) =
    child(ModLog::class) {
        this.attrs(handler)
    }
