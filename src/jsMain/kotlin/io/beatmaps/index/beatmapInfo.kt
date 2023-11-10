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
import io.beatmaps.playlist.addToPlaylist
import io.beatmaps.shared.audioPreview
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.map.bookmarkButton
import io.beatmaps.shared.map.diffIcons
import io.beatmaps.shared.map.links
import io.beatmaps.shared.map.mapTitle
import io.beatmaps.shared.map.rating
import io.beatmaps.shared.map.uploaderWithInfo
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import org.w3c.dom.Audio
import react.RBuilder
import react.RefObject
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.p
import react.dom.span
import react.setState

external interface BeatmapInfoProps : AutoSizeComponentProps<MapDetail> {
    var version: MapVersion?
    var audio: RefObject<Audio>
}

external interface BeatMapInfoState : AutoSizeComponentState {
    var bookmarked: Boolean?
}

class BeatmapInfo : AutoSizeComponent<MapDetail, BeatmapInfoProps, BeatMapInfoState>(30) {
    private fun bookmark(bookmarked: Boolean) =
        Axios.post<String>("${Config.apibase}/bookmark", BookmarkRequest(props.obj?.id, bookmarked = bookmarked), generateConfig<BookmarkRequest, String>())

    override fun RBuilder.render() {
        props.obj?.let { map ->
            val mapAttrs = listOfNotNull(
                if (map.ranked) MapAttr.Ranked else null,
                if (map.qualified && !map.ranked) MapAttr.Qualified else null,
                if (map.curator != null) MapAttr.Curated else null
            ).ifEmpty {
                listOfNotNull(
                    if (map.uploader.verifiedMapper) MapAttr.Verified else null
                )
            }

            div("beatmap") {
                style(this)

                coloredCard {
                    attrs.color = mapAttrs.joinToString(" ") { it.color }
                    attrs.title = mapAttrs.joinToString(" + ") { it.name }

                    div {
                        audioPreview {
                            version = props.version
                            size = "100"
                            audio = props.audio
                        }
                        rating {
                            attrs.up = map.stats.upvotes
                            attrs.down = map.stats.downvotes
                            attrs.rating = map.stats.scoreOneDP
                        }
                    }
                    div("info") {
                        ref = divRef

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
                        globalContext.Consumer { userData ->
                            if (userData != null) {
                                div {
                                    bookmarkButton {
                                        attrs.bookmarked = state.bookmarked ?: (map.bookmarked == true)
                                        attrs.onClick = { e, bm ->
                                            e.preventDefault()
                                            setState {
                                                bookmarked = !bm
                                            }
                                            bookmark(!bm)
                                        }
                                    }
                                    addToPlaylist {
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
                        }
                    }
                }
            }
        } ?: run {
            div("beatmap loading") { }
        }
    }
}

fun RBuilder.beatmapInfo(handler: BeatmapInfoProps.() -> Unit) =
    child(BeatmapInfo::class) {
        this.attrs(handler)
    }
