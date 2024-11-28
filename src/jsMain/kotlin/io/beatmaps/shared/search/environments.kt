package io.beatmaps.shared.search

import external.reactFor
import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.api.EBeatsaberEnvironment
import io.beatmaps.util.applyIf
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.Props
import react.dom.div
import react.dom.h4
import react.dom.h5
import react.dom.input
import react.dom.jsStyle
import react.dom.label
import react.dom.span
import react.fc
import react.useEffect
import react.useEffectOnce
import react.useState
import web.html.HTMLInputElement

external interface EnvironmentsProps : Props {
    var default: EnvironmentSet?
    var callback: ((Set<EBeatsaberEnvironment>) -> Unit)?
    var highlightOnEmpty: Boolean?
}

val environments = fc<EnvironmentsProps> { props ->
    val (selected, setSelected) = useState<EnvironmentSet>(emptySet())
    val (shiftHeld, setShiftHeld) = useState(false)

    val handleShift = { it: Event ->
        val ke = (it as? KeyboardEvent)
        if (ke?.repeat == false) {
            setShiftHeld(ke.shiftKey)
        }
    }

    useEffect(props.default) {
        props.default?.let { setSelected(it) }
    }

    fun updateSelected(newSelected: EnvironmentSet) {
        setSelected(newSelected)
        props.callback?.invoke(newSelected)
    }

    useEffectOnce {
        document.addEventListener("keyup", handleShift)
        document.addEventListener("keydown", handleShift)
        cleanup {
            document.removeEventListener("keyup", handleShift)
            document.removeEventListener("keydown", handleShift)
        }
    }

    div("environments") {
        h4 {
            +"Environments"
        }

        val highlightAll = props.highlightOnEmpty == true && selected.isEmpty()

        val sortedEntries = EBeatsaberEnvironment.entries
            .filter { it.filterable }
            .sortedBy { it.rotation }
            .sortedBy { it.v3 }

        sortedEntries.fold(null as Boolean?) { prev, it ->
            if (it.v3 != prev) {
                h5 {
                    val id = "env-cat-${it.category().lowercase()}"
                    input(InputType.checkBox) {
                        attrs.id = id
                        val envs = EBeatsaberEnvironment.entries.filter { e -> e.v3 == it.v3 }.toSet()

                        attrs.checked = selected.containsAll(envs)

                        attrs.onClickFunction = { ev: Event ->
                            if ((ev.target as? HTMLInputElement?)?.checked == true) {
                                updateSelected(selected + envs)
                            } else {
                                updateSelected(selected - envs)
                            }
                        }
                    }
                    label {
                        attrs.reactFor = id
                        +it.category()
                    }
                }
            }

            div("badge badge-${it.color()} me-2 mb-2") {
                attrs.jsStyle {
                    opacity = if (!highlightAll && !selected.contains(it)) 0.4 else 1
                }

                attrs.onClickFunction = { _ ->
                    val shouldAdd = !selected.contains(it)

                    val newSelected = (if (shiftHeld) selected else emptySet())
                        .applyIf(shouldAdd) {
                            plus(it)
                        }.applyIf(!shouldAdd) {
                            minus(it)
                        }

                    updateSelected(newSelected)
                    window.asDynamic().getSelection().removeAllRanges()
                    Unit
                }

                span {
                    +it.human()
                }
            }

            it.v3
        }
    }
}
