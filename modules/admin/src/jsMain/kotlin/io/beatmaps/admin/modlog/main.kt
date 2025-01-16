package io.beatmaps.admin.modlog

import external.Axios
import external.CancelTokenSource
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.GenericSearchResponse
import io.beatmaps.api.ModLogEntry
import io.beatmaps.common.ModLogOpType
import io.beatmaps.globalContext
import io.beatmaps.setPageTitle
import io.beatmaps.shared.InfiniteScrollElementRenderer
import io.beatmaps.shared.generateInfiniteScrollComponent
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useDidUpdateEffect
import kotlinx.dom.hasClass
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
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
import react.router.useLocation
import react.router.useNavigate
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import kotlin.js.Promise

val modlog = fcmemo<Props>("modlog") {
    val resultsTable = useRef<HTMLElement>()

    val modRef = useRef<HTMLInputElement>()
    val userRef = useRef<HTMLInputElement>()

    val userData = useContext(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val (modLocal, userLocal, typeLocal) = URLSearchParams(location.search).let { u ->
        Triple(u.get("mod") ?: "", u.get("user") ?: "", ModLogOpType.fromName(u.get("type") ?: ""))
    }

    val (mod, setMod) = useState(modLocal)
    val (user, setUser) = useState(userLocal)
    val (type, setType) = useState(typeLocal)
    val (newType, setNewType) = useState(typeLocal)
    val resetRef = useRef<() -> Unit>()
    val loadPageRef = useRef<(Int, CancelTokenSource) -> Promise<GenericSearchResponse<ModLogEntry>?>>()

    useEffectOnce {
        setPageTitle("ModLog")

        if (userData?.admin != true) {
            history.push("/")
        }
    }

    useEffect(location) {
        modRef.current?.value = modLocal
        userRef.current?.value = userLocal
        setNewType(typeLocal)

        setMod(modLocal)
        setUser(userLocal)
        setType(typeLocal)
    }

    useDidUpdateEffect(mod, user, type) {
        resetRef.current?.invoke()
    }

    fun urlExtension(): String {
        val params = listOfNotNull(
            // Fallback to allow this to be called before first render
            (modRef.current?.value ?: modLocal).let { if (it.isNotBlank()) "mod=$it" else null },
            (userRef.current?.value ?: userLocal).let { if (it.isNotBlank()) "user=$it" else null },
            newType?.let { "type=$it" }
        )

        return if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
    }

    loadPageRef.current = { toLoad: Int, token: CancelTokenSource ->
        Axios.get<List<ModLogEntry>>(
            "${Config.apibase}/modlog/$toLoad" + urlExtension(),
            generateConfig<String, List<ModLogEntry>>(token.token)
        ).then {
            return@then GenericSearchResponse.from(it.data)
        }
    }

    fun updateHistory() {
        val ext = urlExtension()
        if (location.search != ext) {
            history.push("/modlog$ext")
        }
    }

    val renderer = useMemo {
        val setUserCb = { modStr: String, userStr: String ->
            modRef.current?.value = modStr
            userRef.current?.value = userStr
            updateHistory()
        }

        InfiniteScrollElementRenderer<ModLogEntry> {
            modLogEntryRenderer {
                attrs.entry = it
                attrs.setUser = setUserCb
            }
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
                            attrs.value = newType?.name ?: ""
                            attrs.onChangeFunction = {
                                val elem = it.currentTarget as HTMLSelectElement
                                setNewType(ModLogOpType.fromName(elem.value))
                            }

                            ModLogOpType.entries.forEach {
                                option {
                                    attrs.value = it.toString()
                                    +it.toString()
                                }
                            }
                            option {
                                attrs.value = ""
                                +"All"
                            }
                        }
                    }
                    td {
                        button(type = ButtonType.submit, classes = "btn btn-primary") {
                            attrs.onClickFunction = {
                                it.preventDefault()
                                updateHistory()
                            }

                            +"Filter"
                        }
                    }
                }
            }
            tbody {
                ref = resultsTable
                key = "modlogTable"

                modLogInfiniteScroll {
                    attrs.resetRef = resetRef
                    attrs.rowHeight = 48.0
                    attrs.itemsPerPage = 30
                    attrs.container = resultsTable
                    attrs.loadPage = loadPageRef
                    attrs.childFilter = {
                        !it.hasClass("hiddenRow")
                    }
                    attrs.renderElement = renderer
                }
            }
        }
    }
}

val modLogInfiniteScroll = generateInfiniteScrollComponent(ModLogEntry::class)
