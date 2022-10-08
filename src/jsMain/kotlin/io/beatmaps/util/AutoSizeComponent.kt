package io.beatmaps.util

import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import react.RComponent
import react.RProps
import react.RState
import react.createRef
import react.dom.RDOMBuilder
import react.dom.jsStyle
import react.setState

external interface AutoSizeComponentProps<T> : RProps {
    var obj: T?
}

external interface AutoSizeComponentState : RState {
    var loaded: Boolean?
    var height: String
}

abstract class AutoSizeComponent<T, U : AutoSizeComponentProps<T>, V : AutoSizeComponentState>(private val padding: Int) : RComponent<U, V>() {
    protected val divRef = createRef<HTMLDivElement>()

    fun style(builder: RDOMBuilder<*>) {
        builder.attrs.jsStyle {
            height = state.height
        }
    }

    override fun componentDidMount() {
        setState {
            height = ""
            loaded = false
        }
    }

    override fun componentDidUpdate(prevProps: U, prevState: V, snapshot: Any) {
        if (state.loaded != true && props.obj != null) {
            val innerSize = divRef.current?.scrollHeight?.let { it + padding } ?: 0
            setState {
                loaded = true
                height = "${innerSize}px"
            }

            window.setTimeout({
                setState {
                    height = "auto"
                }
            }, 200)
        } else if (state.loaded == true && props.obj == null) {
            setState {
                height = ""
                loaded = false
            }
        }
    }
}
