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
import react.RefObject
import react.Suspense
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.use
import react.useCallback
import react.useEffect
import react.useState
import web.cssom.ClassName
import web.html.Audio
import web.html.HTMLDivElement

external interface BeatmapInfoProps : AutoSizeComponentProps<MapDetail> {
    var version: MapVersion?
    var audio: RefObject<Audio>
}

val beatmapInfo = fcmemo<BeatmapInfoProps>("beatmapInfo") { props ->
    val autoSize = useAutoSize<MapDetail, HTMLDivElement>(props, 30)
    val (bookmarked, setBookmarked) = useState<Boolean?>(null)
    val userData = use(globalContext)

    useEffect(props.version) {
        setBookmarked(null)
    }

    val bookmarkCb: (Boolean) -> Unit = useCallback(props.obj) { bm: Boolean ->
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

        div {
            className = ClassName("beatmap")
            autoSize.style(this)

            coloredCard {
                color = mapAttrs.joinToString(" ") { it.color }
                title = mapAttrs.joinToString(" + ") { it.name }

                div {
                    audioPreview {
                        nsfw = map.nsfw
                        version = props.version
                        size = AudioPreviewSize.Small
                        audio = props.audio
                    }
                    rating {
                        up = map.stats.upvotes
                        down = map.stats.downvotes
                        rating = map.stats.scoreOneDP
                    }
                }
                div {
                    className = ClassName("info")
                    ref = autoSize.divRef

                    mapTitle {
                        title = map.name
                        mapKey = map.id
                    }
                    p {
                        uploaderWithInfo {
                            this.map = map
                            version = props.version
                        }
                    }
                    div {
                        className = ClassName("diffs")
                        diffIcons {
                            diffs = props.version?.diffs
                        }
                    }
                    div {
                        className = ClassName("ranked-statuses")
                        rankedStatus {
                            this.map = map
                        }
                    }
                }
                div {
                    className = ClassName("additional")
                    span {
                        +map.id
                        i {
                            className = ClassName("fas fa-key")
                            ariaHidden = true
                        }
                    }
                    span {
                        +map.metadata.duration.formatTime()
                        i {
                            className = ClassName("fas fa-clock")
                            ariaHidden = true
                        }
                    }
                    span {
                        +map.metadata.bpm.fixed(2).toString()
                        img {
                            alt = "Metronome"
                            src = "/static/icons/metronome.svg"
                            width = 12.0
                            height = 12.0
                        }
                    }
                    if (userData != null) {
                        Suspense {
                            div {
                                bookmarkButton {
                                    this.bookmarked = bookmarked ?: (map.bookmarked == true)
                                    onClick = bookmarkCb
                                }
                                playlists.addTo {
                                    this.map = map
                                }
                            }
                        }
                    }
                }
                div {
                    className = ClassName("links")
                    links {
                        this.map = map
                        version = props.version
                        limited = true
                    }
                }
            }
        }
    } ?: run {
        div {
            className = ClassName("beatmap loading")
        }
    }
}
