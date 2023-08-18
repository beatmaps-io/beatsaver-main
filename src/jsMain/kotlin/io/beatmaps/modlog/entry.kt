package io.beatmaps.modlog

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.ModLogEntry
import io.beatmaps.api.ReviewSentiment
import io.beatmaps.api.UserDetail
import io.beatmaps.common.DeletedData
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.ReviewDeleteData
import io.beatmaps.common.ReviewModerationData
import io.beatmaps.common.RevokeSessionsData
import io.beatmaps.common.SuspendData
import io.beatmaps.common.UnpublishData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.maps.mapTag
import io.beatmaps.shared.mapTitle
import kotlinx.html.DIV
import kotlinx.html.TD
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import react.Props
import react.createRef
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.br
import react.dom.div
import react.dom.i
import react.dom.p
import react.dom.span
import react.dom.td
import react.dom.tr
import react.fc

external interface ModLogEntryProps : Props {
    var entry: ModLogEntry?
    var setUser: (String, String) -> Unit
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

val modLogEntryRenderer = fc<ModLogEntryProps> {
    fun RDOMBuilder<TD>.linkUser(mod: Boolean, userDetail: UserDetail) {
        a("#", classes = "me-1") {
            attrs.onClickFunction = { ev ->
                ev.preventDefault()
                it.setUser(
                    if (mod) userDetail.name else "",
                    if (mod) "" else userDetail.name
                )
            }
            +userDetail.name
        }
        routeLink(userDetail.profileLink()) {
            i("fas fa-external-link-alt") {}
        }
    }

    val localRef = createRef<HTMLDivElement>()
    tr {
        it.entry?.let {
            attrs.onClickFunction = {
                localRef.current?.let { localRow ->
                    if (localRow.classList.contains("expand")) {
                        localRow.classList.remove("expand")
                    } else {
                        localRow.classList.add("expand")
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
                if (it.map != null) {
                    mapTitle {
                        attrs.title = it.map.name
                        attrs.mapKey = it.map.id
                    }
                }
            }
            td { +it.type.name }
            td {
                TimeAgo.default {
                    attrs.date = it.time.toString()
                }
            }
        } ?: run {
            td {
                attrs.colSpan = "5"
            }
        }
    }
    tr("hiddenRow") {
        td {
            attrs.colSpan = "5"
            it.entry?.let {
                div("text-wrap text-break") {
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
                                it.action.verifiedMapper?.let { vm ->
                                    br {}
                                    +"Verified Mapper: $vm"
                                }
                            }
                        }

                        is SuspendData -> {
                            p("card-text") {
                                +"Suspended: ${it.action.suspended}"
                                it.action.reason?.let { reason ->
                                    br {}
                                    +"Reason: $reason"
                                }
                            }
                        }

                        is ReviewDeleteData -> {
                            p("card-text") {
                                +"Deleted review"
                            }
                            p("card-text") {
                                +"Reason: ${it.action.reason}"
                                it.action.text?.let { t ->
                                    br {}
                                    +"Text: $t"
                                }
                                it.action.sentiment?.let { s ->
                                    br {}
                                    +"Sentiment: ${ReviewSentiment.fromInt(s).name}"
                                }
                            }
                        }

                        is ReviewModerationData -> {
                            diffText("text", it.action.oldText, it.action.newText)
                            diffText("sentiment", ReviewSentiment.fromInt(it.action.oldSentiment).name, ReviewSentiment.fromInt(it.action.newSentiment).name)
                        }

                        is RevokeSessionsData -> {
                            p("card-text") {
                                +"Revoked ${if (it.action.all) "all " else ""}sessions"
                                it.action.reason?.let { reason ->
                                    br {}
                                    +"Reason: $reason"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
