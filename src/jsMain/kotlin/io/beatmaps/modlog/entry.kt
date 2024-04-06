package io.beatmaps.modlog

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.ModLogEntry
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
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.maps.mapTag
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.user.userLink
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import react.Props
import react.createRef
import react.dom.br
import react.dom.div
import react.dom.p
import react.dom.span
import react.dom.td
import react.dom.tr
import react.fc

external interface ModLogEntryProps : Props {
    var entry: ModLogEntry?
    var setUser: (String, String) -> Unit
}

val modLogEntryRenderer = fc<ModLogEntryProps> {
    fun userCallback(mod: Boolean, userDetail: UserDetail): () -> Unit = {
        it.setUser(
            if (mod) userDetail.name else "",
            if (mod) "" else userDetail.name
        )
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
                userLink {
                    attrs.user = it.moderator
                    attrs.callback = userCallback(true, it.moderator)
                }
            }
            td {
                userLink {
                    attrs.user = it.user
                    attrs.callback = userCallback(false, it.user)
                }
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
                                diffText {
                                    attrs.description = "description"
                                    attrs.old = it.action.oldDescription
                                    attrs.new = it.action.newDescription
                                }
                                diffText {
                                    attrs.description = "title"
                                    attrs.old = it.action.oldTitle
                                    attrs.new = it.action.newTitle
                                }
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
                            diffText {
                                attrs.description = "description"
                                attrs.old = it.action.oldDescription
                                attrs.new = it.action.newDescription
                            }
                            diffText {
                                attrs.description = "title"
                                attrs.old = it.action.oldTitle
                                attrs.new = it.action.newTitle
                            }
                            diffText {
                                attrs.description = "public"
                                attrs.old = it.action.oldPublic.toString()
                                attrs.new = it.action.newPublic.toString()
                            }
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
                                it.action.curatorTab?.let { vm ->
                                    br {}
                                    +"Curator Tab: $vm"
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
                            diffText {
                                attrs.description = "text"
                                attrs.old = it.action.oldText
                                attrs.new = it.action.newText
                            }
                            diffText {
                                attrs.description = "sentiment"
                                attrs.old = ReviewSentiment.fromInt(it.action.oldSentiment).name
                                attrs.new = ReviewSentiment.fromInt(it.action.newSentiment).name
                            }
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
