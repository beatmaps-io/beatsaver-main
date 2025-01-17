package io.beatmaps.util

import js.objects.jso
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import react.Props
import react.RElementBuilder
import react.RefObject
import react.dom.html.HTMLAttributes
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef
import web.cssom.Auto.Companion.auto
import web.cssom.Height
import web.cssom.Margin
import web.cssom.px

external interface AutoSizeComponentProps<T> : Props {
    var obj: T?
}

data class AutoSizeHelper(
    val divRef: RefObject<HTMLDivElement>,
    val style: (RElementBuilder<HTMLAttributes<*>>) -> Unit,
    val hide: () -> Unit
)

fun <T> useAutoSize(props: AutoSizeComponentProps<T>, padding: Int): AutoSizeHelper {
    val loaded = useRef(false)
    val styleRef = useRef<HTMLElement>()
    val height = useRef<Height>()
    val margin = useRef<Margin>()
    val autoSizeHandle = useRef<Int>()

    val divRef = useRef<HTMLDivElement>()

    useEffectOnceWithCleanup {
        onCleanup {
            autoSizeHandle.current?.let { window.clearTimeout(it) }
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
            val handleLocal = window.setTimeout({
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
        { builder: RElementBuilder<HTMLAttributes<*>> ->
            builder.ref = styleRef
            builder.attrs.style = jso {
                this.height = height.current
                margin.current?.let {
                    this.margin = it
                }
            }
        },
        {
            val handleLocal = window.setTimeout({
                setHeight(0.px)
            }, 10)

            // Set current size so animation works
            setHeight((autoSize() + 10).px)
            setMargin(0.px)
            autoSizeHandle.current = handleLocal
        }
    )
}
