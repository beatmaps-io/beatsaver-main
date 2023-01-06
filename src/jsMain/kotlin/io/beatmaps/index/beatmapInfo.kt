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
import io.beatmaps.shared.bookmarkButton
import io.beatmaps.shared.coloredCard
import io.beatmaps.shared.diffIcons
import io.beatmaps.shared.links
import io.beatmaps.shared.mapTitle
import io.beatmaps.shared.rating
import io.beatmaps.shared.uploader
import io.beatmaps.util.AutoSizeComponent
import io.beatmaps.util.AutoSizeComponentProps
import io.beatmaps.util.AutoSizeComponentState
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Audio
import org.w3c.dom.HTMLElement
import react.RBuilder
import react.RefObject
import react.createRef
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.p
import react.dom.span
import react.setState

external interface BeatmapInfoProps : AutoSizeComponentProps<MapDetail> {
    var version: MapVersion?
    var modal: RefObject<ModalComponent>
    var audio: Audio?
}

external interface BeatMapInfoState : AutoSizeComponentState {
    var handle: Int?
    var bookmarked: Boolean?
}

class BeatmapInfo : AutoSizeComponent<MapDetail, BeatmapInfoProps, BeatMapInfoState>(30) {
    private val audioContainerRef = createRef<HTMLElement>()
    private val outerProgressRef = createRef<HTMLElement>()
    private val leftProgressRef = createRef<HTMLElement>()
    private val rightProgressRef = createRef<HTMLElement>()

    override fun componentWillUnmount() {
        state.handle?.let { window.clearInterval(it) }
    }

    private fun updateView(p: Double = 0.0) {
        val firstHalf = p <= 0.5 || p.isNaN()
        leftProgressRef.current?.style?.transform = "rotate(${(p * 360).fixed(2)}deg)"
        outerProgressRef.current?.style?.clip = if (firstHalf) "" else "rect(auto, auto, auto, auto)"
        rightProgressRef.current?.style?.display = if (firstHalf) "none" else "block"
        rightProgressRef.current?.style?.transform = if (firstHalf) "" else "rotate(180deg)"
    }

    private val timeUpdate = { _: Any ->
        props.version?.previewURL?.let { ourSrc ->
            props.audio?.let { audio ->
                if (audio.getAttribute("src") == ourSrc && !audio.paused) {
                    updateView(audio.currentTime / audio.duration)
                } else {
                    if (state.handle != null) {
                        state.handle?.let { window.clearInterval(it) }
                        setState {
                            handle = null
                        }
                    }
                    audioContainerRef.current?.classList?.remove("playing")
                    updateView()
                }
            }
        } ?: Unit
    }

    private fun play(audio: Audio) {
        audio.play()
        val handleLocal = window.setInterval(timeUpdate, 20)
        setState {
            handle = handleLocal
        }
        audioContainerRef.current?.classList?.add("playing")
    }

    private val toggleAudio: (Any) -> Unit = { _: Any ->
        props.version?.previewURL?.let { newSrc ->
            props.audio?.let { audio ->
                if (audio.getAttribute("src") != newSrc) {
                    audio.src = newSrc
                    audio.currentTime = 0.0
                    play(audio)
                } else if (audio.paused) {
                    play(audio)
                } else {
                    audio.pause()
                    audio.currentTime = 0.0
                }
            }
        }
    }

    private fun bookmark(bookmarked: Boolean) =
        Axios.post<String>("${Config.apibase}/bookmark", BookmarkRequest(props.obj?.intId() ?: 0, bookmarked), generateConfig<BookmarkRequest, String>())

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
                        div("audio-progress") {
                            attrs.onClickFunction = toggleAudio
                            ref = audioContainerRef
                            i("fas fa-play") { }
                            div("pie") {
                                ref = outerProgressRef
                                div("left-size half-circle") {
                                    ref = leftProgressRef
                                }
                                div("right-size half-circle") {
                                    ref = rightProgressRef
                                }
                            }
                        }
                        img(src = props.version?.coverURL, alt = "Cover Image", classes = "cover") {
                            attrs.width = "100"
                            attrs.height = "100"
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
                            uploader {
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
                                        this.map = map
                                        modal = props.modal
                                    }
                                }
                            }
                        }
                    }
                    div("links") {
                        links {
                            attrs.map = map
                            attrs.version = props.version
                            attrs.modal = props.modal
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
