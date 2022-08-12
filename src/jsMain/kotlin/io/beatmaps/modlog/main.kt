package io.beatmaps.modlog

import external.TimeAgo
import external.axiosGet
import io.beatmaps.UserData
import io.beatmaps.api.ModLogEntry
import io.beatmaps.api.UserDetail
import io.beatmaps.common.Config
import io.beatmaps.common.DeletedData
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.UnpublishData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.index.mapTitle
import io.beatmaps.maps.mapTag
import io.beatmaps.setPageTitle
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.TD
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.ReactElement
import react.createRef
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.br
import react.dom.button
import react.dom.div
import react.dom.form
import react.dom.i
import react.dom.input
import react.dom.p
import react.dom.span
import react.dom.table
import react.dom.tbody
import react.dom.td
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.router.dom.RouteResultHistory
import react.router.dom.routeLink
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

    private fun RDOMBuilder<DIV>.diffText(human: String, old: String, new: String) {
        if (new != old) {
            p("card-text") {
                if (new.isNotEmpty()) {
                    +"Updated $human"
                    span("text-danger d-block") {
                        i("fas fa-minus") {}
                        +" $old"
                    }
                    span("text-success d-block") {
                        i("fas fa-plus") {}
                        +" $new"
                    }
                } else {
                    // Shows as empty if curator is changing tags
                    +"Deleted $human"
                }
            }
        }
    }

    private fun RDOMBuilder<TD>.linkUser(mod: Boolean, userDetail: UserDetail) {
        a("#", classes = "me-1") {
            attrs.onClickFunction = { ev ->
                ev.preventDefault()
                modRef.current?.value = if (mod) userDetail.name else ""
                userRef.current?.value = if (mod) "" else userDetail.name
                props.history.push("/modlog" + urlExtension())
            }
            +userDetail.name
        }
        routeLink("/profile/${userDetail.id}") {
            i("fas fa-external-link-alt") {}
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
                        val localRef = createRef<HTMLDivElement>()
                        tr {
                            attrs.onClickFunction = {
                                localRef.current?.let { localRow ->
                                    if (localRow.className.contains("expand")) {
                                        localRow.className = ""
                                    } else {
                                        localRow.className = "expand"
                                    }
                                }
                            }
                            td {
                                linkUser(true, it.moderator)
                            }
                            td {
                                linkUser(false, it.user)
                            }
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
                        tr("hiddenRow") {
                            td {
                                attrs.colSpan = "5"
                                div {
                                    ref = localRef

                                    when (it.action) {
                                        is InfoEditData -> {
                                            val curatorEdit = it.action.newTitle.isEmpty() && it.action.newDescription.isEmpty()

                                            if (!curatorEdit) {
                                                diffText("description", it.action.oldDescription, it.action.newDescription)
                                                diffText("title", it.action.oldTitle, it.action.newTitle)
                                            }

                                            val newTags = it.action.newTags ?: listOf()
                                            val oldTags = it.action.oldTags ?: listOf()
                                            if (newTags != oldTags) {
                                                p("card-text") {
                                                    +"Updated tags"
                                                    span("d-block") {
                                                        oldTags.forEach {
                                                            mapTag {
                                                                attrs.tag = it
                                                            }
                                                        }
                                                    }
                                                    span("d-block") {
                                                        newTags.forEach {
                                                            mapTag {
                                                                attrs.selected = true
                                                                attrs.tag = it
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        is DeletedData -> {
                                            +"Reason: \"${it.action.reason}\""
                                        }
                                        is DeletedPlaylistData -> {
                                            p("card-text") {
                                                +"Playlist: "
                                                routeLink("/playlist/${it.action.playlistId}") {
                                                    +"${it.action.playlistId}"
                                                }
                                            }
                                            p("card-text") {
                                                +"Reason: ${it.action.reason}"
                                            }
                                        }
                                        is EditPlaylistData -> {
                                            p("card-text") {
                                                +"Playlist: "
                                                routeLink("/playlist/${it.action.playlistId}") {
                                                    +"${it.action.playlistId}"
                                                }
                                            }
                                            diffText("description", it.action.oldDescription, it.action.newDescription)
                                            diffText("title", it.action.oldTitle, it.action.newTitle)
                                            diffText("public", it.action.oldPublic.toString(), it.action.newPublic.toString())
                                        }
                                        is UnpublishData -> {
                                            p("card-text") {
                                                +"Reason: ${it.action.reason}"
                                            }
                                        }
                                        is UploadLimitData -> {
                                            p("card-text") {
                                                +"Upload Limit: ${it.action.newValue}"
                                                br {}
                                                +"Curator: ${it.action.newCurator}"
                                            }
                                        }
                                    }
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
