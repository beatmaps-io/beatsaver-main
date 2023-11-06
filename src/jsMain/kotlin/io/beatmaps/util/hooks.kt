package io.beatmaps.util

import react.EffectBuilder
import react.useEffect
import react.useEffectOnce
import react.useRef

fun useDidUpdateEffect(vararg dependencies: dynamic, effect: EffectBuilder.() -> Unit) {
    val isMountingRef = useRef(false)

    useEffectOnce {
        isMountingRef.current = true
    }

    useEffect(*dependencies) {
        if (isMountingRef.current != true) {
            effect()
        } else {
            isMountingRef.current = false
        }
    }
}

inline fun <reified T : Any> useObjectMemoize(obj: T?): T? {
    val ref = useRef<T>()

    if (obj != ref.current) {
        ref.current = obj
    }

    return ref.current
}
