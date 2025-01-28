package io.beatmaps.playlist

import external.Axios
import external.generateConfig
import external.routeLink
import io.beatmaps.Config
import io.beatmaps.History
import io.beatmaps.api.UploadResponse
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
import io.beatmaps.util.fcmemo
import kotlinx.serialization.encodeToString
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.textarea
import react.router.useLocation
import react.router.useNavigate
import react.router.useParams
import react.use
import react.useCallback
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import web.cssom.ClassName
import web.form.FormData
import web.html.ButtonType
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement
import web.html.InputType
import web.url.URLSearchParams

val editPlaylist = fcmemo<Props>("editPlaylist") { props ->
    val captchaRef = useRef<ICaptchaHandler>()
    val coverRef = useRef<HTMLInputElement>()

    val nameRef = useRef<HTMLInputElement>()
    val descriptionRef = useRef<HTMLTextAreaElement>()
    val publicRef = useRef<HTMLInputElement>()

    val location = useLocation()
    val fromState = useMemo(location) { location.readState<SearchParamsPlaylist>()?.let { SearchPlaylistConfig(it, 100) } }

    val params = useParams()
    val history = History(useNavigate())
    val id = params["id"]

    val query = URLSearchParams(location.search)
    val hasSearchQuery = query.has("search")

    val userData = use(globalContext)

    useEffectOnce {
        if (userData == null) {
            history.push(if (id != null) "/playlists/$id" else "/")
        }
    }

    val (loading, setLoading) = useState(false)
    val (init, setInit) = useState(false)
    val (playlist, setPlaylist) = useState<PlaylistFull?>(null)
    val (errors, setErrors) = useState(listOf<UploadValidationInfo>())
    val config = useRef(playlist?.config as? SearchPlaylistConfig ?: fromState)
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

    val configCallback = useCallback { it: SearchPlaylistConfig ->
        config.current = it
    }

    if (init) {
        div {
            className = ClassName("card border-dark")
            div {
                className = ClassName("card-header")
                +((if (id == null) "Create" else "Edit") + " playlist")
            }
            form {
                className = ClassName("card-body")
                onSubmit = { ev ->
                    ev.preventDefault()
                    setLoading(true)

                    fun sendForm(data: FormData) {
                        data.append("name", nameRef.current?.value ?: "")
                        data.append("description", descriptionRef.current?.value ?: "")

                        if (isSearch) {
                            data.append("type", EPlaylistType.Search.name)
                            data.append(
                                "config",
                                json.encodeToString(config.current)
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

                        Axios.post<UploadResponse>(
                            Config.apibase + "/playlists" + if (id == null) "/create" else "/id/$id/edit", data,
                            UploadRequestConfig { }
                        ).then { r ->
                            if (r.status == 200) {
                                history.push("/playlists/${id ?: r.data}")
                            } else {
                                captchaRef.current?.reset()
                                setErrors(r.data.errors)
                                setLoading(false)
                            }
                        }.catch {
                            setErrors(listOf(UploadValidationInfo("Internal server error")))
                            setLoading(false)
                        }
                    }

                    val data = FormData()
                    captchaRef.current?.execute()?.then({
                        data.append("recaptcha", it)
                        sendForm(data)
                    }) {
                        setErrors(listOf(UploadValidationInfo(it.message ?: "Unknown error")))
                        setLoading(false)
                    } ?: run {
                        sendForm(data)
                    }
                }
                div {
                    className = ClassName("mb-3")
                    label {
                        className = ClassName("form-label")
                        htmlFor = "name"
                        +"Name"
                    }
                    input {
                        type = InputType.text
                        className = ClassName("form-control")
                        key = "name"
                        ref = nameRef
                        defaultValue = playlist?.name ?: ""
                        this.id = "name"
                        placeholder = "Name"
                        disabled = loading
                        required = true
                        autoFocus = true
                    }
                }
                div {
                    className = ClassName("mb-3")
                    label {
                        className = ClassName("form-label")
                        htmlFor = "description"
                        +"Description"
                    }
                    textarea {
                        rows = 10
                        className = ClassName("form-control")
                        this.id = "description"
                        disabled = loading
                        defaultValue = playlist?.description ?: ""
                        ref = descriptionRef
                    }
                }
                if (userData?.suspended == false && !isSearch) {
                    toggle {
                        this.id = "public"
                        disabled = loading
                        default = playlist?.type?.anonymousAllowed != false
                        toggleRef = publicRef
                        text = "Public"
                        className = "mb-3"
                    }
                }
                div {
                    className = ClassName("mb-3 w-25")
                    label {
                        className = ClassName("form-label")
                        htmlFor = "cover"
                        div {
                            className = ClassName("text-truncate")
                            +"Cover image"
                        }
                    }
                    input {
                        type = InputType.file
                        className = ClassName("form-control")
                        key = "cover"
                        this.id = "cover"
                        ref = coverRef
                        hidden = loading
                    }
                }
                if (isSearch) {
                    hr {}
                    playlistSearchEditor {
                        this.loading = loading
                        this.config = (playlist?.config as? SearchPlaylistConfig) ?: fromState ?: SearchPlaylistConfig.DEFAULT
                        callback = configCallback
                    }
                }
                errors {
                    validationErrors = errors
                }
                div {
                    className = ClassName("btn-group w-100 mt-3")
                    routeLink(id?.let { "/playlists/$it" } ?: "/", className = "btn btn-secondary w-50") {
                        +"Cancel"
                    }
                    if (id == null) {
                        // Middle element otherwise the button corners don't round properly
                        captcha {
                            key = "captcha"
                            this.captchaRef = captchaRef
                            page = "playlist"
                        }
                    }
                    button {
                        className = ClassName("btn btn-success w-50")
                        type = ButtonType.submit
                        disabled = loading
                        +(if (id == null) "Create" else "Save")
                    }
                }
            }
        }
    }
}
