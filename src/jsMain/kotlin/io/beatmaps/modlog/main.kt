package io.beatmaps.modlog

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.ModLogEntry
import io.beatmaps.common.ModLogOpType
import io.beatmaps.globalContext
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
import react.Props
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
import react.fc
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

val modlog = fc<Props> {
    val (mod, setMod) = useState("")
    val (user, setUser) = useState("")
    val (type, setType) = useState<ModLogOpType?>(null)
    val (resultsKey, setResultsKey) = useState(Any())

    val resultsTable = useRef<HTMLElement>()

    val modRef = useRef<HTMLInputElement>()
    val userRef = useRef<HTMLInputElement>()
    val typeRef = useRef<HTMLSelectElement>()

    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    useEffectOnce {
        setPageTitle("ModLog")

        if (userData?.admin != true) {
            history.push("/")
        }
    }

    useEffect {
        val (modLocal, userLocal, typeLocal) = URLSearchParams(location.search).let { u ->
            Triple(u.get("mod") ?: "", u.get("user") ?: "", ModLogOpType.fromName(u.get("type") ?: ""))
        }

        modRef.current?.value = modLocal
        userRef.current?.value = userLocal
        typeRef.current?.value = typeLocal?.name ?: ""

        setMod(modLocal)
        setUser(userLocal)
        setType(typeLocal)
    }

    useEffect(mod, user, type) {
        setResultsKey(Any())
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            modRef.current?.value?.let { if (it.isNotBlank()) "mod=$it" else null },
            userRef.current?.value?.let { if (it.isNotBlank()) "user=$it" else null },
            typeRef.current?.value?.let { if (it.isNotBlank()) "type=$it" else null }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    val loadPage = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<Array<ModLogEntry>>(
            "${Config.apibase}/modlog/$toLoad" + urlExtension(),
            generateConfig<String, Array<ModLogEntry>>(token.token)
        ).then {
            return@then it.data.toList()
        }
    }

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
                                    attrs.selected = type == it
                                    +it.toString()
                                }
                            }
                            option {
                                attrs.value = ""
                                attrs.selected = type == null
                                +"All"
                            }
                        }
                    }
                    td {
                        button(type = ButtonType.submit, classes = "btn btn-primary") {
                            attrs.onClickFunction = {
                                it.preventDefault()

                                history.push("/modlog" + urlExtension())
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
                    attrs.resultsKey = resultsKey
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
                                history.push("/modlog" + urlExtension())
                            }
                        }
                    }
                }
            }
        }
    }
}

class ModLogInfiniteScroll : InfiniteScroll<ModLogEntry>()
