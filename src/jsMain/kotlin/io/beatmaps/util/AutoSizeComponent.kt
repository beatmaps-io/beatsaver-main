package io.beatmaps.util

import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import react.Props
import react.RComponent
import react.State
import react.createRef
import react.dom.RDOMBuilder
import react.dom.jsStyle
import react.setState

external interface AutoSizeComponentProps<T> : Props {
    var obj: T?
}

external interface AutoSizeComponentState : State {
    var loaded: Boolean?
    var height: String
    var margin: String?
}

abstract class AutoSizeComponent<T, U : AutoSizeComponentProps<T>, V : AutoSizeComponentState>(private val padding: Int) : RComponent<U, V>() {
    protected val divRef = createRef<HTMLDivElement>()
    private var handle: Int? = null

    fun style(builder: RDOMBuilder<*>) {
        builder.attrs.jsStyle {
            height = state.height
            state.margin?.let {
                margin = it
            }
        }
    }

    fun hide() {
        // Set current size so animation works
        setState {
            height = "${autoSize() + 10}px"
            margin = "0px"
        }

        handle = window.setTimeout({
            setState {
                height = "0px"
            }
        }, 10)
    }

    private fun autoSize() = divRef.current?.scrollHeight?.let { it + padding } ?: 0

    override fun componentDidMount() {
        setState {
            height = ""
            loaded = false
        }
    }

    override fun componentWillUnmount() {
        handle?.let { window.clearTimeout(it) }
    }

    override fun componentDidUpdate(prevProps: U, prevState: V, snapshot: Any) {
        if (state.loaded != true && props.obj != null) {
            setState {
                loaded = true
                height = "${autoSize()}px"
            }

            handle = window.setTimeout({
                setState {
                    height = "auto"
                }
            }, 200)
        } else if (state.loaded == true && props.obj == null) {
            setState {
                height = ""
                loaded = false
                margin = null
            }
        }
    }
}
