package io.beatmaps.shared

import io.beatmaps.api.MapVersion
import io.beatmaps.common.fixed
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Audio
import org.w3c.dom.HTMLElement
import react.Props
import react.RBuilder
import react.RComponent
import react.RefObject
import react.State
import react.createRef
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.jsStyle
import react.setState

external interface AudioPreviewProps : Props {
    var version: MapVersion?
    var size: String
    var audio: RefObject<Audio>
}

external interface AudioPreviewState : State {
    var handle: Int?
}

class AudioPreview : RComponent<AudioPreviewProps, AudioPreviewState>() {
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
        props.version?.previewURL.let { ourSrc ->
            props.audio.current?.let {
                if (it.getAttribute("src") == ourSrc && !it.paused) {
                    updateView(it.currentTime / it.duration)
                } else {
                    if (state.handle != null) {
                        state.handle?.let { h -> window.clearInterval(h) }
                        setState {
                            handle = null
                        }
                    }
                    audioContainerRef.current?.classList?.remove("playing")
                    updateView()
                }
            }
        }
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
            props.audio.current?.let {
                if (it.getAttribute("src") != newSrc) {
                    it.src = newSrc
                    it.currentTime = 0.0
                    play(it)
                } else if (it.paused) {
                    play(it)
                } else {
                    it.pause()
                    it.currentTime = 0.0
                }
            }
        }
    }

    override fun RBuilder.render() {
        div("audio-progress") {
            attrs.jsStyle["height"] = props.size + "px"
            attrs.jsStyle["width"] = props.size + "px"
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
            attrs.width = props.size
            attrs.height = props.size
        }
    }
}

fun RBuilder.audioPreview(handler: AudioPreviewProps.() -> Unit) =
    child(AudioPreview::class) {
        this.attrs(handler)
    }
