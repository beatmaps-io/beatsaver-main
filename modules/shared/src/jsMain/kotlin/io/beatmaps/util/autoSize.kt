package io.beatmaps.util

import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import react.Props
import react.RefObject
import react.dom.RDOMBuilder
import react.dom.jsStyle
import react.useEffect
import react.useEffectOnce
import react.useRef
import react.useState

external interface AutoSizeComponentProps<T> : Props {
    var obj: T?
}

data class AutoSizeHelper(
    val divRef: RefObject<HTMLDivElement>,
    val style: (RDOMBuilder<*>) -> Unit,
    val hide: () -> Unit
)

fun <T> useAutoSize(props: AutoSizeComponentProps<T>, padding: Int): AutoSizeHelper {
    val (loaded, setLoaded) = useState(false)
    val (height, setHeight) = useState("")
    val (margin, setMargin) = useState<String?>(null)
    val autoSizeHandle = useRef<Int>()

    val divRef = useRef<HTMLDivElement>()

    useEffectOnce {
        cleanup {
            autoSizeHandle.current?.let { window.clearTimeout(it) }
        }
    }

    fun autoSize() = divRef.current?.scrollHeight?.let { it + padding } ?: 0

    useEffect(props.obj) {
        if (!loaded && props.obj != null) {
            val handleLocal = window.setTimeout({
                setHeight("auto")
            }, 200)

            setLoaded(true)
            setHeight("${autoSize()}px")
            autoSizeHandle.current = handleLocal
        } else if (loaded && props.obj == null) {
            setHeight("")
            setLoaded(false)
            setMargin(null)
        }
    }

    return AutoSizeHelper(
        divRef,
        { builder: RDOMBuilder<*> ->
            builder.attrs.jsStyle {
                this.height = height
                margin?.let {
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
