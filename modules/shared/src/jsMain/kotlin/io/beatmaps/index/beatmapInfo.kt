package io.beatmaps.index

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.BookmarkRequest
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapVersion
import io.beatmaps.common.api.MapAttr
import io.beatmaps.common.fixed
import io.beatmaps.common.formatTime
import io.beatmaps.globalContext
import io.beatmaps.playlist.playlists
import io.beatmaps.shared.AudioPreviewSize
import io.beatmaps.shared.audioPreview
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.map.bookmarkButton
import io.beatmaps.shared.map.diffIcons
import io.beatmaps.shared.map.links
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.shared.map.rankedStatus
import io.beatmaps.shared.map.rating
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.fcmemo
import io.beatmaps.util.useAutoSize
import org.w3c.dom.Audio
import org.w3c.dom.events.Event
import react.RefObject
import react.Suspense
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.p
import react.dom.span
import react.useCallback
import react.useContext
import react.useEffect
import react.useState

external interface BeatmapInfoProps : AutoSizeComponentProps<MapDetail> {
    var version: MapVersion?
    var audio: RefObject<Audio>
}

val beatmapInfo = fcmemo<BeatmapInfoProps>("beatmapInfo") { props ->
    val autoSize = useAutoSize(props, 30)
    val (bookmarked, setBookmarked) = useState<Boolean?>(null)
    val userData = useContext(globalContext)

    useEffect(props.version) {
        setBookmarked(null)
    }

    val bookmarkCb: (Event, Boolean) -> Unit = useCallback(props.obj) { e: Event, bm: Boolean ->
        e.preventDefault()
        setBookmarked(!bm)

        Axios.post<String>("${Config.apibase}/bookmark", BookmarkRequest(props.obj?.id, bookmarked = !bm), generateConfig<BookmarkRequest, String>())
    }

    props.obj?.let { map ->
        val mapAttrs = listOfNotNull(
            if (map.ranked || map.blRanked) MapAttr.Ranked else null,
            if (map.curator != null) MapAttr.Curated else null
        ).ifEmpty {
            listOfNotNull(
                if (map.uploader.verifiedMapper || map.collaborators?.any { it.verifiedMapper } == true) MapAttr.Verified else null
            )
        }

        div("beatmap") {
            autoSize.style(this)

            coloredCard {
                attrs.color = mapAttrs.joinToString(" ") { it.color }
                attrs.title = mapAttrs.joinToString(" + ") { it.name }

                div {
                    audioPreview {
                        attrs.nsfw = map.nsfw
                        attrs.version = props.version
                        attrs.size = AudioPreviewSize.Small
                        attrs.audio = props.audio
                    }
                    rating {
                        attrs.up = map.stats.upvotes
                        attrs.down = map.stats.downvotes
                        attrs.rating = map.stats.scoreOneDP
                    }
                }
                div("info") {
                    ref = autoSize.divRef

                    mapTitle {
                        attrs.title = map.name
                        attrs.mapKey = map.id
                    }
                    p {
                        uploaderWithInfo {
                            attrs.map = map
                            attrs.version = props.version
                        }
                    }
                    div("diffs") {
                        diffIcons {
                            attrs.diffs = props.version?.diffs
                        }
                    }
                    div("ranked-statuses") {
                        rankedStatus {
                            attrs.map = map
                        }
                    }
                }
                div("additional") {
                    span {
                        +map.id
                        i("fas fa-key") {
                            attrs.attributes["aria-hidden"] = "true"
                        }
                    }
                    span {
                        +map.metadata.duration.formatTime()
                        i("fas fa-clock") {
                            attrs.attributes["aria-hidden"] = "true"
                        }
                    }
                    span {
                        +map.metadata.bpm.fixed(2).toString()
                        img("Metronome", "/static/icons/metronome.svg") {
                            attrs.width = "12"
                            attrs.height = "12"
                        }
                    }
                    if (userData != null) {
                        Suspense {
                            div {
                                bookmarkButton {
                                    attrs.bookmarked = bookmarked ?: (map.bookmarked == true)
                                    attrs.onClick = bookmarkCb
                                }
                                playlists.addTo {
                                    attrs.map = map
                                }
                            }
                        }
                    }
                }
                div("links") {
                    links {
                        attrs.map = map
                        attrs.version = props.version
                        attrs.limited = true
                    }
                }
            }
        }
    } ?: run {
        div("beatmap loading") { }
    }
}
