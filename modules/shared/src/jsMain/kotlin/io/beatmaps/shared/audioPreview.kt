package io.beatmaps.shared

import io.beatmaps.api.MapVersion
import io.beatmaps.common.fixed
import io.beatmaps.globalContext
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.use
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.html.Audio
import web.html.HTMLElement
import web.timers.Interval
import web.timers.clearInterval
import web.timers.setInterval

enum class AudioPreviewSize(val size: Double) {
    Small(100.0),
    Large(200.0)
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
    val userData = use(globalContext)

    val shouldBlur = props.nsfw == true && userData?.blurnsfw != false
    val (blur, setBlur) = useState(shouldBlur)

    val handle = useRef<Interval>()

    useEffectOnceWithCleanup {
        onCleanup {
            handle.current?.let { clearInterval(it) }
        }
    }

    useEffect(shouldBlur) {
        setBlur(shouldBlur)
    }

    fun updateView(p: Double = 0.0) {
        val firstHalf = p <= 0.5 || p.isNaN()
        leftProgressRef.current?.style?.transform = "rotate(${(p * 360).fixed(2)}deg)"
        outerProgressRef.current?.style?.clipPath = if (firstHalf) "" else "rect(0px 50px 100% 0%)"
        rightProgressRef.current?.style?.display = if (firstHalf) "none" else "block"
        rightProgressRef.current?.style?.transform = if (firstHalf) "" else "rotate(180deg)"
    }

    val timeUpdate: () -> Unit = {
        props.version?.previewURL.let { ourSrc ->
            props.audio.current?.let {
                if (it.getAttribute("src") == ourSrc && !it.paused) {
                    updateView(it.currentTime / it.duration)
                } else {
                    handle.current?.let { h ->
                        clearInterval(h)
                        handle.current = null
                    }
                    audioContainerRef.current?.classList?.remove("playing")
                    updateView()
                }
            }
        }
    }

    fun play(audio: Audio) {
        audio.playAsync()
        handle.current = setInterval(timeUpdate, 20)
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
        div {
            className = ClassName("audio-progress" + if (props.size == AudioPreviewSize.Large) " large" else "")
            onClick = toggleAudio
            ref = audioContainerRef
            i {
                className = ClassName("fas fa-play")
            }
            div {
                className = ClassName("pie")
                ref = outerProgressRef
                div {
                    className = ClassName("left-size half-circle")
                    ref = leftProgressRef
                }
                div {
                    className = ClassName("right-size half-circle")
                    ref = rightProgressRef
                }
            }
        }
    }
    img {
        src = props.version?.coverURL
        alt = "Cover Image"
        className = ClassName("cover${if (blur) " nsfw" else ""}")
        width = props.size.size
        height = props.size.size
        onClick = {
            setBlur(false)
        }
    }
}
