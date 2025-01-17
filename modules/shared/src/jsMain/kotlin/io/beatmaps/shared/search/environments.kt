package io.beatmaps.shared.search

import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.api.EBeatsaberEnvironment
import io.beatmaps.util.applyIf
import js.objects.jso
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.fc
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useState
import web.cssom.ClassName
import web.cssom.number
import web.html.InputType

external interface EnvironmentsProps : Props {
    var default: EnvironmentSet?
    var callback: ((Set<EBeatsaberEnvironment>) -> Unit)?
    var highlightOnEmpty: Boolean?
}

val environments = fc<EnvironmentsProps>("environments") { props ->
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

    useEffectOnceWithCleanup {
        document.addEventListener("keyup", handleShift)
        document.addEventListener("keydown", handleShift)
        onCleanup {
            document.removeEventListener("keyup", handleShift)
            document.removeEventListener("keydown", handleShift)
        }
    }

    div {
        attrs.className = ClassName("environments")
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
                    input {
                        attrs.type = InputType.checkbox
                        attrs.id = id
                        val envs = EBeatsaberEnvironment.entries.filter { e -> e.v3 == it.v3 }.toSet()

                        attrs.checked = selected.containsAll(envs)

                        attrs.onClick = { ev ->
                            if (ev.currentTarget.checked) {
                                updateSelected(selected + envs)
                            } else {
                                updateSelected(selected - envs)
                            }
                        }
                    }
                    label {
                        attrs.htmlFor = id
                        +it.category()
                    }
                }
            }

            div {
                attrs.className = ClassName("badge badge-${it.color()} me-2 mb-2")
                attrs.style = jso {
                    opacity = number(if (!highlightAll && !selected.contains(it)) 0.4 else 1.0)
                }

                attrs.onClick = { _ ->
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
