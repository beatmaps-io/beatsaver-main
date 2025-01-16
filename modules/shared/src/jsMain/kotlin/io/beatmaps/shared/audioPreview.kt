package io.beatmaps.shared

import io.beatmaps.api.MapVersion
import io.beatmaps.common.fixed
import io.beatmaps.globalContext
import io.beatmaps.util.fcmemo
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import org.w3c.dom.Audio
import org.w3c.dom.HTMLElement
import react.Props
import react.RefObject
import react.dom.div
import react.dom.i
import react.dom.img
import react.useContext
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef
import react.useState

enum class AudioPreviewSize(val size: String) {
    Small("100"),
    Large("200")
}

external interface AudioPreviewProps : Props {
    var version: MapVersion?
    var nsfw: Boolean?
    var size: AudioPreviewSize
    var audio: RefObject<Audio>
}

val audioPreview = fcmemo<AudioPreviewProps>("audioPreview") { props ->
    val audioContainerRef = useRef<HTMLElement>()
    val outerProgressRef = useRef<HTMLElement>()
    val leftProgressRef = useRef<HTMLElement>()
    val rightProgressRef = useRef<HTMLElement>()
    val userData = useContext(globalContext)

    val shouldBlur = props.nsfw == true && userData?.blurnsfw != false
    val (blur, setBlur) = useState(shouldBlur)

    val handle = useRef<Int>()

    useEffectOnceWithCleanup {
        onCleanup {
            handle.current?.let { window.clearInterval(it) }
        }
    }

    useEffect(shouldBlur) {
        setBlur(shouldBlur)
    }

    fun updateView(p: Double = 0.0) {
        val firstHalf = p <= 0.5 || p.isNaN()
        leftProgressRef.current?.style?.transform = "rotate(${(p * 360).fixed(2)}deg)"
        outerProgressRef.current?.style?.clip = if (firstHalf) "" else "rect(auto, auto, auto, auto)"
        rightProgressRef.current?.style?.display = if (firstHalf) "none" else "block"
        rightProgressRef.current?.style?.transform = if (firstHalf) "" else "rotate(180deg)"
    }

    val timeUpdate = { _: Any ->
        props.version?.previewURL.let { ourSrc ->
            props.audio.current?.let {
                if (it.getAttribute("src") == ourSrc && !it.paused) {
                    updateView(it.currentTime / it.duration)
                } else {
                    handle.current?.let { h ->
                        window.clearInterval(h)
                        handle.current = null
                    }
                    audioContainerRef.current?.classList?.remove("playing")
                    updateView()
                }
            }
        }
    }

    fun play(audio: Audio) {
        audio.play()
        handle.current = window.setInterval(timeUpdate, 20)
        audioContainerRef.current?.classList?.add("playing")
    }

    val toggleAudio: (Any) -> Unit = { _: Any ->
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

    if (!blur) {
        div("audio-progress" + if (props.size == AudioPreviewSize.Large) " large" else "") {
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
    }
    img(src = props.version?.coverURL, alt = "Cover Image", classes = "cover${if (blur) " nsfw" else ""}") {
        attrs.width = props.size.size
        attrs.height = props.size.size
        attrs.onClickFunction = {
            setBlur(false)
        }
    }
}
