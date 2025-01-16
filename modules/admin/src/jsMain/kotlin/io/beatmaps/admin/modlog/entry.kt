package io.beatmaps.admin.modlog

import external.TimeAgo
import external.routeLink
import io.beatmaps.api.ModLogEntry
import io.beatmaps.api.UserDetail
import io.beatmaps.common.DeletedData
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.FlagsEditData
import io.beatmaps.common.InfoEditData
import io.beatmaps.common.ReplyDeleteData
import io.beatmaps.common.ReplyModerationData
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
import io.beatmaps.util.fcmemo
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLDivElement
import react.Props
import react.dom.br
import react.dom.div
import react.dom.p
import react.dom.span
import react.dom.td
import react.dom.tr
import react.useRef

external interface ModLogEntryProps : Props {
    var entry: ModLogEntry?
    var setUser: (String, String) -> Unit
}

val modLogEntryRenderer = fcmemo<ModLogEntryProps>("modLogEntryRenderer") {
    fun userCallback(mod: Boolean, userDetail: UserDetail): () -> Unit = {
        it.setUser(
            if (mod) userDetail.name else "",
            if (mod) "" else userDetail.name
        )
    }

    val localRef = useRef<HTMLDivElement>()
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
                it.map?.let { m ->
                    mapTitle {
                        attrs.title = m.name
                        attrs.mapKey = m.id
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

                    val action = it.action
                    when (action) {
                        is InfoEditData -> {
                            val curatorEdit = action.newTitle.isEmpty() && action.newDescription.isEmpty()

                            if (!curatorEdit) {
                                diffText {
                                    attrs.description = "description"
                                    attrs.old = action.oldDescription
                                    attrs.new = action.newDescription
                                }
                                diffText {
                                    attrs.description = "title"
                                    attrs.old = action.oldTitle
                                    attrs.new = action.newTitle
                                }
                            }

                            val newTags = action.newTags ?: listOf()
                            val oldTags = action.oldTags ?: listOf()
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

                        is FlagsEditData -> {
                            val info = listOfNotNull(
                                action.ai?.let {
                                    if (it) "Marked as AI" else "Marked as human-made"
                                },
                                action.nsfw?.let {
                                    if (it) "Marked as NSFW" else "Marked as clean"
                                }
                            )
                            if (info.any()) {
                                p("card-text") {
                                    info.forEachIndexed { idx, txt ->
                                        if (idx > 0) br {}
                                        +txt
                                    }
                                }
                            }
                        }

                        is DeletedData -> {
                            +"Reason: \"${action.reason}\""
                        }

                        is DeletedPlaylistData -> {
                            p("card-text") {
                                +"Playlist: "
                                routeLink("/playlist/${action.playlistId}") {
                                    +"${action.playlistId}"
                                }
                            }
                            p("card-text") {
                                +"Reason: ${action.reason}"
                            }
                        }

                        is EditPlaylistData -> {
                            p("card-text") {
                                +"Playlist: "
                                routeLink("/playlist/${action.playlistId}") {
                                    +"${action.playlistId}"
                                }
                            }
                            diffText {
                                attrs.description = "description"
                                attrs.old = action.oldDescription
                                attrs.new = action.newDescription
                            }
                            diffText {
                                attrs.description = "title"
                                attrs.old = action.oldTitle
                                attrs.new = action.newTitle
                            }
                            diffText {
                                attrs.description = "public"
                                attrs.old = action.oldPublic.toString()
                                attrs.new = action.newPublic.toString()
                            }
                        }

                        is UnpublishData -> {
                            p("card-text") {
                                +"Reason: ${action.reason}"
                            }
                        }

                        is UploadLimitData -> {
                            p("card-text") {
                                +"Upload Limit: ${action.newValue}"
                                br {}
                                +"Curator: ${action.newCurator}"
                                action.verifiedMapper?.let { vm ->
                                    br {}
                                    +"Verified Mapper: $vm"
                                }
                                action.curatorTab?.let { vm ->
                                    br {}
                                    +"Curator Tab: $vm"
                                }
                            }
                        }

                        is SuspendData -> {
                            p("card-text") {
                                +"Suspended: ${action.suspended}"
                                action.reason?.let { reason ->
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
                                +"Reason: ${action.reason}"
                                action.text?.let { t ->
                                    br {}
                                    +"Text: $t"
                                }
                                action.sentiment?.let { s ->
                                    br {}
                                    +"Sentiment: ${ReviewSentiment.fromInt(s).name}"
                                }
                            }
                        }

                        is ReviewModerationData -> {
                            diffText {
                                attrs.description = "text"
                                attrs.old = action.oldText
                                attrs.new = action.newText
                            }
                            diffText {
                                attrs.description = "sentiment"
                                attrs.old = ReviewSentiment.fromInt(action.oldSentiment).name
                                attrs.new = ReviewSentiment.fromInt(action.newSentiment).name
                            }
                        }

                        is ReplyDeleteData -> {
                            p("card-text") {
                                +"Deleted reply"
                            }
                            p("card-text") {
                                +"Reason: ${action.reason}"
                                action.text?.let { t ->
                                    br {}
                                    +"Text: $t"
                                }
                            }
                        }

                        is ReplyModerationData -> {
                            diffText {
                                attrs.description = "text"
                                attrs.old = action.oldText
                                attrs.new = action.newText
                            }
                        }

                        is RevokeSessionsData -> {
                            p("card-text") {
                                +"Revoked ${if (action.all) "all " else ""}sessions"
                                action.reason?.let { reason ->
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
