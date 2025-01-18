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
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.router.useLocation
import react.router.useNavigate
import react.use
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLElement
import web.html.HTMLInputElement
import web.html.InputType
import web.url.URLSearchParams
import kotlin.js.Promise

val modlog = fcmemo<Props>("modlog") {
    val resultsTable = useRef<HTMLElement>()

    val modRef = useRef<HTMLInputElement>()
    val userRef = useRef<HTMLInputElement>()

    val userData = use(globalContext)
    val history = History(useNavigate())
    val location = useLocation()

    val (modLocal, userLocal, typeLocal) = URLSearchParams(location.search).let { u ->
        Triple(u["mod"] ?: "", u["user"] ?: "", ModLogOpType.fromName(u["type"] ?: ""))
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
                entry = it
                this.setUser = setUserCb
            }
        }
    }

    form {
        table {
            className = ClassName("table table-dark table-striped-3 modlog")
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
                        input {
                            this.type = InputType.text
                            className = ClassName("form-control")
                            placeholder = "Moderator"
                            ariaLabel = "Moderator"
                            ref = modRef
                        }
                    }
                    td {
                        input {
                            this.type = InputType.text
                            className = ClassName("form-control")
                            placeholder = "User"
                            ariaLabel = "User"
                            ref = userRef
                        }
                    }
                    td { }
                    td {
                        select {
                            className = ClassName("form-select")
                            ariaLabel = "Type"
                            value = newType?.name ?: ""
                            onChange = {
                                setNewType(ModLogOpType.fromName(it.currentTarget.value))
                            }

                            ModLogOpType.entries.forEach {
                                option {
                                    value = it.toString()
                                    +it.toString()
                                }
                            }
                            option {
                                value = ""
                                +"All"
                            }
                        }
                    }
                    td {
                        button {
                            this.type = ButtonType.submit
                            className = ClassName("btn btn-primary")
                            onClick = {
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
                    this.resetRef = resetRef
                    rowHeight = 48.0
                    itemsPerPage = 30
                    container = resultsTable
                    loadPage = loadPageRef
                    childFilter = {
                        !it.classList.contains("hiddenRow")
                    }
                    renderElement = renderer
                }
            }
        }
    }
}

val modLogInfiniteScroll = generateInfiniteScrollComponent(ModLogEntry::class)
