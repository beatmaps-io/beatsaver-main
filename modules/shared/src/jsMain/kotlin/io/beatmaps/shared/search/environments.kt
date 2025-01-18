package io.beatmaps.shared.search

import io.beatmaps.common.EnvironmentSet
import io.beatmaps.common.api.EBeatsaberEnvironment
import io.beatmaps.util.applyIf
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useState
import web.cssom.ClassName
import web.cssom.number
import web.dom.document
import web.events.addEventListener
import web.events.removeEventListener
import web.html.InputType
import web.uievents.KeyboardEvent
import web.window.window

external interface EnvironmentsProps : Props {
    var default: EnvironmentSet?
    var callback: ((Set<EBeatsaberEnvironment>) -> Unit)?
    var highlightOnEmpty: Boolean?
}

val environments = fcmemo<EnvironmentsProps>("environments") { props ->
    val (selected, setSelected) = useState<EnvironmentSet>(emptySet())
    val (shiftHeld, setShiftHeld) = useState(false)

    val handleShift = { ke: KeyboardEvent ->
        if (!ke.repeat) setShiftHeld(ke.shiftKey)
    }

    useEffect(props.default) {
        props.default?.let { setSelected(it) }
    }

    fun updateSelected(newSelected: EnvironmentSet) {
        setSelected(newSelected)
        props.callback?.invoke(newSelected)
    }

    useEffectOnceWithCleanup {
        document.addEventListener(KeyboardEvent.KEY_UP, handleShift)
        document.addEventListener(KeyboardEvent.KEY_DOWN, handleShift)
        onCleanup {
            document.removeEventListener(KeyboardEvent.KEY_UP, handleShift)
            document.removeEventListener(KeyboardEvent.KEY_DOWN, handleShift)
        }
    }

    div {
        className = ClassName("environments")
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
                        type = InputType.checkbox
                        this.id = id
                        val envs = EBeatsaberEnvironment.entries.filter { e -> e.v3 == it.v3 }.toSet()

                        checked = selected.containsAll(envs)

                        onChange = { ev ->
                            if (ev.currentTarget.checked) {
                                updateSelected(selected + envs)
                            } else {
                                updateSelected(selected - envs)
                            }
                        }
                    }
                    label {
                        htmlFor = id
                        +it.category()
                    }
                }
            }

            div {
                className = ClassName("badge badge-${it.color()} me-2 mb-2")
                style = jso {
                    opacity = number(if (!highlightAll && !selected.contains(it)) 0.4 else 1.0)
                }

                onClick = { _ ->
                    val shouldAdd = !selected.contains(it)

                    val newSelected = (if (shiftHeld) selected else emptySet())
                        .applyIf(shouldAdd) {
                            plus(it)
                        }.applyIf(!shouldAdd) {
                            minus(it)
                        }

                    updateSelected(newSelected)
                    window.getSelection()?.removeAllRanges()
                }

                span {
                    +it.human()
                }
            }

            it.v3
        }
    }
}
