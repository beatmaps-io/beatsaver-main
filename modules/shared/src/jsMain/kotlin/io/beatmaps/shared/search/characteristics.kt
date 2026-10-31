package io.beatmaps.shared.search

import io.beatmaps.common.CharacteristicSet
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.util.applyIf
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useRef
import react.useState
import web.cssom.ClassName
import web.cssom.number
import web.dom.document
import web.events.addEventListener
import web.events.removeEventListener
import web.html.InputType
import web.uievents.KeyboardEvent

external interface CharacteristicsProps : Props {
    var default: CharacteristicSet?
    var callback: ((CharacteristicSet) -> Unit)?
    var highlightOnEmpty: Boolean?
}

val characteristics = fcmemo<CharacteristicsProps>("characteristics") { props ->
    val (selected, setSelected) = useState<CharacteristicSet>(emptySet())
    val shiftHeld = useRef(false)

    val handleShift = { ke: KeyboardEvent ->
        if (!ke.repeat) shiftHeld.current = ke.shiftKey
    }

    useEffect(props.default) {
        props.default?.let { setSelected(it) }
    }

    fun updateSelected(newSelected: CharacteristicSet) {
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
        className = ClassName("characteristics")

        h5 {
            val id = "characteristics"
            input {
                type = InputType.checkbox
                this.id = id
                val chars = ECharacteristic.entries.toSet()

                checked = selected.containsAll(chars)

                onChange = { ev ->
                    if (ev.currentTarget.checked) {
                        updateSelected(selected + chars)
                    } else {
                        updateSelected(selected - chars)
                    }
                }
            }
            label {
                htmlFor = id
                +"Characteristics"
            }
        }

        val highlightAll = props.highlightOnEmpty == true && selected.isEmpty()
        ECharacteristic.entries.forEach { it ->
            div {
                className = ClassName("badge badge-${it.color} me-2 mb-2")
                style = jso {
                    opacity = number(if (!highlightAll && !selected.contains(it)) 0.4 else 1.0)
                }

                onClick = { _ ->
                    val shouldAdd = !selected.contains(it)

                    val newSelected = (if (shiftHeld.current == true) selected else emptySet())
                        .applyIf(shouldAdd) {
                            plus(it)
                        }.applyIf(!shouldAdd) {
                            minus(it)
                        }

                    updateSelected(newSelected)
                }

                span {
                    +it.human()
                }
            }
        }
    }
}
