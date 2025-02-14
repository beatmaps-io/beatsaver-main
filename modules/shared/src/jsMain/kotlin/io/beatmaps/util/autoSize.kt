package io.beatmaps.util

import js.objects.jso
import react.Props
import react.RefObject
import react.dom.html.HTMLAttributes
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef
import web.cssom.Auto.Companion.auto
import web.cssom.Height
import web.cssom.Margin
import web.cssom.px
import web.html.HTMLDivElement
import web.html.HTMLElement
import web.timers.Timeout
import web.timers.clearTimeout
import web.timers.setTimeout

external interface AutoSizeComponentProps<T> : Props {
    var obj: T?
}

data class AutoSizeHelper<T : HTMLElement>(
    val divRef: RefObject<HTMLDivElement>,
    val style: (HTMLAttributes<T>) -> Unit
)

fun <T, U : HTMLElement> useAutoSize(props: AutoSizeComponentProps<T>, padding: Int): AutoSizeHelper<U> {
    val loaded = useRef(false)
    val styleRef = useRef<HTMLElement>()
    val height = useRef<Height>()
    val margin = useRef<Margin>()
    val autoSizeHandle = useRef<Timeout>()

    val divRef = useRef<HTMLDivElement>()

    useEffectOnceWithCleanup {
        onCleanup {
            autoSizeHandle.current?.let { clearTimeout(it) }
        }
    }

    fun autoSize() = divRef.current?.scrollHeight?.let { it + padding } ?: 0
    fun setHeight(newHeight: Height?) {
        height.current = newHeight
        styleRef.current?.style?.height = newHeight?.let { "$it" } ?: ""
    }
    fun setMargin(newMargin: Margin?) {
        margin.current = newMargin
        styleRef.current?.style?.margin = newMargin?.let { "$it" } ?: ""
    }

    useEffect(props.obj) {
        if (loaded.current != true && props.obj != null) {
            val handleLocal = setTimeout({
                setHeight(auto)
            }, 200)

            loaded.current = true
            setHeight(autoSize().px)
            autoSizeHandle.current = handleLocal
        } else if (loaded.current == true && props.obj == null) {
            setHeight(null)
            loaded.current = false
            setMargin(null)
        }
    }

    return AutoSizeHelper(
        divRef,
        { builder: HTMLAttributes<U> ->
            builder.ref = styleRef
            builder.style = jso {
                this.height = height.current
                margin.current?.let {
                    this.margin = it
                }
            }
        }
    )
}
