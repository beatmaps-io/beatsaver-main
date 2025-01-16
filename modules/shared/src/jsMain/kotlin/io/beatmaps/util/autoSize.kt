package io.beatmaps.util

import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import react.Props
import react.RefObject
import react.dom.RDOMBuilder
import react.dom.jsStyle
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef

external interface AutoSizeComponentProps<T> : Props {
    var obj: T?
}

data class AutoSizeHelper(
    val divRef: RefObject<HTMLDivElement>,
    val style: (RDOMBuilder<*>) -> Unit,
    val hide: () -> Unit
)

fun <T> useAutoSize(props: AutoSizeComponentProps<T>, padding: Int): AutoSizeHelper {
    val loaded = useRef(false)
    val styleRef = useRef<HTMLElement>()
    val height = useRef("")
    val margin = useRef<String>()
    val autoSizeHandle = useRef<Int>()

    val divRef = useRef<HTMLDivElement>()

    useEffectOnceWithCleanup {
        onCleanup {
            autoSizeHandle.current?.let { window.clearTimeout(it) }
        }
    }

    fun autoSize() = divRef.current?.scrollHeight?.let { it + padding } ?: 0
    fun setHeight(newHeight: String) {
        height.current = newHeight
        styleRef.current?.style?.height = newHeight
    }
    fun setMargin(newMargin: String?) {
        margin.current = newMargin
        styleRef.current?.style?.margin = newMargin ?: ""
    }

    useEffect(props.obj) {
        if (loaded.current != true && props.obj != null) {
            val handleLocal = window.setTimeout({
                setHeight("auto")
            }, 200)

            loaded.current = true
            setHeight("${autoSize()}px")
            autoSizeHandle.current = handleLocal
        } else if (loaded.current == true && props.obj == null) {
            setHeight("")
            loaded.current = false
            setMargin(null)
        }
    }

    return AutoSizeHelper(
        divRef,
        { builder: RDOMBuilder<*> ->
            builder.ref = styleRef
            builder.attrs.jsStyle {
                this.height = height.current
                margin.current?.let {
                    this.margin = it
                }
            }
        },
        {
            val handleLocal = window.setTimeout({
                setHeight("0px")
            }, 10)

            // Set current size so animation works
            setHeight("${autoSize() + 10}px")
            setMargin("0px")
            autoSizeHandle.current = handleLocal
        }
    )
}
