package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import external.reactFor
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistPage
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.json
import io.beatmaps.globalContext
import io.beatmaps.readState
import io.beatmaps.setPageTitle
import io.beatmaps.shared.form.errors
import io.beatmaps.shared.form.toggle
import io.beatmaps.upload.UploadRequestConfig
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.id
import kotlinx.html.js.onSubmitFunction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.url.URLSearchParams
import org.w3c.files.get
import org.w3c.xhr.FormData
import react.Props
import react.dom.button
import react.dom.defaultValue
import react.dom.div
import react.dom.form
import react.dom.hr
import react.dom.input
import react.dom.label
import react.dom.textarea
import react.fc
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

val editPlaylist = fc<Props> {
    val captchaRef = useRef<ICaptchaHandler>()
    val coverRef = useRef<HTMLInputElement>()

    val nameRef = useRef<HTMLInputElement>()
    val descriptionRef = useRef<HTMLTextAreaElement>()
    val publicRef = useRef<HTMLInputElement>()

    val location = useLocation()
    val fromState = location.readState<SearchParamsPlaylist>()?.let { SearchPlaylistConfig(it, 100) }

    val params = useParams()
    val history = History(useNavigate())
    val id = params["id"]

    val query = URLSearchParams(location.search)
    val hasSearchQuery = query.has("search")

    val userData = useContext(globalContext)

    useEffectOnce {
        if (userData == null) {
            history.push(if (id != null) "/playlists/$id" else "/")
        }
    }

    val (loading, setLoading) = useState(false)
    val (init, setInit) = useState(false)
    val (playlist, setPlaylist) = useState<PlaylistFull?>(null)
    val (errors, setErrors) = useState(listOf<UploadValidationInfo>())
    val (config, setConfig) = useState(playlist?.config as? SearchPlaylistConfig ?: fromState)
    val isSearch = playlist?.type == EPlaylistType.Search || (playlist == null && (fromState != null || hasSearchQuery))

    fun loadData() {
        if (loading) return

        setLoading(true)

        Axios.get<PlaylistPage>(
            "${Config.apibase}/playlists/id/$id",
            generateConfig<String, PlaylistPage>()
        ).then {
            setPageTitle("Edit Playlist - ${it.data.playlist?.name}")

            setPlaylist(it.data.playlist)
            setLoading(false)
            setInit(true)
        }.catch {
            history.push("/")
        }
    }

    useEffect(params) {
        if (id == null) {
            setPageTitle("Create Playlist")
            setLoading(false)
            setInit(true)
        } else {
            setPageTitle("Edit Playlist")
            loadData()
        }
    }

    if (init) {
        div("card border-dark") {
            div("card-header") {
                +((if (id == null) "Create" else "Edit") + " playlist")
            }
            form(classes = "card-body") {
                attrs.onSubmitFunction = { ev ->
                    ev.preventDefault()
                    setLoading(true)

                    fun sendForm(data: FormData) {
                        data.append("name", nameRef.current?.value ?: "")
                        data.append("description", descriptionRef.current?.value ?: "")

                        if (isSearch) {
                            data.append("type", EPlaylistType.Search.name)
                            data.append(
                                "config",
                                json.encodeToString(config)
                            )
                        } else {
                            data.append(
                                "type",
                                if (publicRef.current?.checked == true) EPlaylistType.Public.name else EPlaylistType.Private.name
                            )
                        }
                        val file = coverRef.current?.files?.let { it[0] }
                        if (file != null) {
                            data.asDynamic().append("file", file) // Kotlin doesn't have an equivalent method to this js
                        }

                        Axios.post<dynamic>(
                            Config.apibase + "/playlists" + if (id == null) "/create" else "/id/$id/edit", data,
                            UploadRequestConfig { }
                        ).then { r ->
                            if (r.status == 200) {
                                history.push("/playlists/${id ?: r.data}")
                            } else {
                                captchaRef.current?.reset()
                                val failedResponse = Json.decodeFromDynamic<FailedUploadResponse>(r.data)
                                setErrors(failedResponse.errors)
                                setLoading(false)
                            }
                        }.catch {
                            setErrors(listOf(UploadValidationInfo("Internal server error")))
                            setLoading(false)
                        }
                    }

                    val data = FormData()
                    captchaRef.current?.execute()?.then({
                        data.append("captcha", it)
                        sendForm(data)
                    }) {
                        setErrors(listOf(UploadValidationInfo(it.message ?: "Unknown error")))
                        setLoading(false)
                    } ?: run {
                        sendForm(data)
                    }
                }
                div("mb-3") {
                    label("form-label") {
                        attrs.reactFor = "name"
                        +"Name"
                    }
                    input(type = InputType.text, classes = "form-control") {
                        key = "name"
                        ref = nameRef
                        attrs.defaultValue = playlist?.name ?: ""
                        attrs.id = "name"
                        attrs.placeholder = "Name"
                        attrs.disabled = loading
                        attrs.required = true
                        attrs.autoFocus = true
                    }
                }
                div("mb-3") {
                    label("form-label") {
                        attrs.reactFor = "description"
                        +"Description"
                    }
                    textarea("10", classes = "form-control") {
                        attrs.id = "description"
                        attrs.disabled = loading
                        attrs.defaultValue = playlist?.description ?: ""
                        ref = descriptionRef
                    }
                }
                if (userData?.suspended == false && !isSearch) {
                    toggle {
                        attrs.id = "public"
                        attrs.disabled = loading
                        attrs.default = playlist?.type?.anonymousAllowed != false
                        attrs.ref = publicRef
                        attrs.text = "Public"
                        attrs.className = "mb-3"
                    }
                }
                div("mb-3 w-25") {
                    label("form-label") {
                        attrs.reactFor = "cover"
                        div("text-truncate") {
                            +"Cover image"
                        }
                    }
                    input(InputType.file, classes = "form-control") {
                        key = "cover"
                        attrs.id = "cover"
                        ref = coverRef
                        attrs.hidden = loading
                    }
                }
                if (isSearch) {
                    hr {}
                    playlistSearchEditor {
                        attrs.loading = loading
                        attrs.config = (playlist?.config as? SearchPlaylistConfig) ?: fromState ?: SearchPlaylistConfig.DEFAULT
                        attrs.callback = {
                            setConfig(it)
                        }
                    }
                }
                errors {
                    attrs.validationErrors = errors
                }
                div("btn-group w-100 mt-3") {
                    routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary w-50") {
                        +"Cancel"
                    }
                    if (id == null) {
                        // Middle element otherwise the button corners don't round properly
                        captcha(captchaRef)
                    }
                    button(classes = "btn btn-success w-50", type = ButtonType.submit) {
                        attrs.disabled = loading
                        +(if (id == null) "Create" else "Save")
                    }
                }
            }
        }
    }
}
