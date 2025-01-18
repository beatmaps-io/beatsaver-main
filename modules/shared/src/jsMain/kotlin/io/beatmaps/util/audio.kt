package io.beatmaps.util

import react.useEffectOnceWithCleanup
import react.useRef
import web.html.Audio

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
