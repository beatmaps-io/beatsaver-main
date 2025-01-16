package io.beatmaps.util

import org.w3c.dom.Audio
import react.useEffectOnceWithCleanup
import react.useRef

fun useAudio() = useRef(
    Audio().also {
        it.volume = 0.4
    }
).also { audio ->
    useEffectOnceWithCleanup {
        onCleanup {
            audio.current?.pause()
        }
    }
}
