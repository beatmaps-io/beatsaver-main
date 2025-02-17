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
import io.beatmaps.common.UnCurateMapData
import io.beatmaps.common.UnCuratePlaylistData
import io.beatmaps.common.UnpublishData
import io.beatmaps.common.UploadLimitData
import io.beatmaps.common.api.ReviewSentiment
import io.beatmaps.maps.mapTag
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.user.userLink
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.useRef
import web.cssom.ClassName
import web.html.HTMLDivElement

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
            onClick = {
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
                    user = it.moderator
                    callback = userCallback(true, it.moderator)
                }
            }
            td {
                userLink {
                    user = it.user
                    callback = userCallback(false, it.user)
                }
            }
            td {
                it.map?.let { m ->
                    mapTitle {
                        title = m.name
                        mapKey = m.id
                    }
                }
            }
            td { +it.type.name }
            td {
                TimeAgo.default {
                    date = it.time.toString()
                }
            }
        } ?: run {
            td {
                colSpan = 5
            }
        }
    }
    tr {
        className = ClassName("hiddenRow")
        td {
            colSpan = 5
            it.entry?.let {
                div {
                    ref = localRef
                    className = ClassName("text-wrap text-break")

                    when (val action = it.action) {
                        is InfoEditData -> {
                            val curatorEdit = action.newTitle.isEmpty() && action.newDescription.isEmpty()

                            if (!curatorEdit) {
                                diffText {
                                    description = "description"
                                    old = action.oldDescription
                                    new = action.newDescription
                                }
                                diffText {
                                    description = "title"
                                    old = action.oldTitle
                                    new = action.newTitle
                                }
                            }

                            val newTags = action.newTags ?: listOf()
                            val oldTags = action.oldTags ?: listOf()
                            if (newTags != oldTags) {
                                p {
                                    className = ClassName("card-text")
                                    +"Updated tags"
                                    span {
                                        className = ClassName("d-block")
                                        oldTags.forEach {
                                            mapTag {
                                                tag = it
                                            }
                                        }
                                    }
                                    span {
                                        className = ClassName("d-block")
                                        newTags.forEach {
                                            mapTag {
                                                selected = true
                                                tag = it
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
                                p {
                                    className = ClassName("card-text")
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
                            p {
                                className = ClassName("card-text")
                                +"Playlist: "
                                routeLink("/playlist/${action.playlistId}") {
                                    +"${action.playlistId}"
                                }
                            }
                            p {
                                className = ClassName("card-text")
                                +"Reason: ${action.reason}"
                            }
                        }

                        is EditPlaylistData -> {
                            p {
                                className = ClassName("card-text")
                                +"Playlist: "
                                routeLink("/playlist/${action.playlistId}") {
                                    +"${action.playlistId}"
                                }
                            }
                            diffText {
                                description = "description"
                                old = action.oldDescription
                                new = action.newDescription
                            }
                            diffText {
                                description = "title"
                                old = action.oldTitle
                                new = action.newTitle
                            }
                            diffText {
                                description = "public"
                                old = action.oldPublic.toString()
                                new = action.newPublic.toString()
                            }
                        }

                        is UnpublishData -> {
                            p {
                                className = ClassName("card-text")
                                +"Reason: ${action.reason}"
                            }
                        }

                        is UploadLimitData -> {
                            p {
                                className = ClassName("card-text")
                                +"Upload Limit: ${action.newValue}"
                                action.newVivify?.let { nv ->
                                    +" / Vivify Limit: $nv"
                                }
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
                            p {
                                className = ClassName("card-text")
                                +"Suspended: ${action.suspended}"
                                action.reason?.let { reason ->
                                    br {}
                                    +"Reason: $reason"
                                }
                            }
                        }

                        is ReviewDeleteData -> {
                            p {
                                className = ClassName("card-text")
                                +"Deleted review"
                            }
                            p {
                                className = ClassName("card-text")
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
                                description = "text"
                                old = action.oldText
                                new = action.newText
                            }
                            diffText {
                                description = "sentiment"
                                old = ReviewSentiment.fromInt(action.oldSentiment).name
                                new = ReviewSentiment.fromInt(action.newSentiment).name
                            }
                        }

                        is ReplyDeleteData -> {
                            p {
                                className = ClassName("card-text")
                                +"Deleted reply"
                            }
                            p {
                                className = ClassName("card-text")
                                +"Reason: ${action.reason}"
                                action.text?.let { t ->
                                    br {}
                                    +"Text: $t"
                                }
                            }
                        }

                        is ReplyModerationData -> {
                            diffText {
                                description = "text"
                                old = action.oldText
                                new = action.newText
                            }
                        }

                        is RevokeSessionsData -> {
                            p {
                                className = ClassName("card-text")
                                +"Revoked ${if (action.all) "all " else ""}sessions"
                                action.reason?.let { reason ->
                                    br {}
                                    +"Reason: $reason"
                                }
                            }
                        }

                        is UnCurateMapData -> {
                            p {
                                "Reason: ${action.reason}"
                            }
                        }

                        is UnCuratePlaylistData -> {
                            p {
                                className = ClassName("card-text")
                                +"Playlist: "
                                routeLink("/playlist/${action.playlistId}") {
                                    +"${action.playlistId}"
                                }
                            }
                            p {
                                "Reason: ${action.reason}"
                            }
                        }
                    }
                }
            }
        }
    }
}
