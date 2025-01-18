package io.beatmaps.shared.search

import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagSet
import io.beatmaps.common.MapTagType
import io.beatmaps.maps.mapTag
import io.beatmaps.util.applyIf
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.useEffect
import react.useEffectOnceWithCleanup
import react.useState
import web.cssom.ClassName
import web.dom.document
import web.events.addEventListener
import web.events.removeEventListener
import web.uievents.KeyboardEvent
import web.window.window

external interface TagsProps : Props {
    var default: MapTagSet?
    var callback: ((MapTagSet) -> Unit)?
    var highlightOnEmpty: Boolean?
}

val tags = fcmemo<TagsProps>("tags") { props ->
    val (selected, setSelected) = useState<MapTagSet>(emptyMap())
    val (altHeld, setAltHeld) = useState(false)
    val (shiftHeld, setShiftHeld) = useState(false)

    val handleShift = { ke: KeyboardEvent ->
        if (!ke.repeat) {
            setShiftHeld(ke.shiftKey)
            setAltHeld(ke.altKey)
        }
    }

    useEffect(props.default) {
        props.default?.let { setSelected(it) }
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
        className = ClassName("tags")
        h4 {
            +"Tags"
        }

        val highlightAll = props.highlightOnEmpty == true && selected.all { it.value.isEmpty() }
        MapTag.sorted.fold(MapTagType.None) { prev, it ->
            if (it.type != prev) {
                div {
                    className = ClassName("break")
                }
            }

            if (it.type != MapTagType.None) {
                mapTag {
                    this.selected = selected.any { x -> x.value.contains(it) } || highlightAll
                    excluded = selected[false]?.contains(it) == true
                    tag = it

                    onClick = { _ ->
                        val t = selected[!altHeld] ?: setOf()

                        val shouldAdd = !t.contains(it)

                        val newTags = t.applyIf(!shiftHeld) {
                            filterTo(hashSetOf()) { o -> o.type != it.type }
                        }.applyIf(shouldAdd) {
                            plus(it)
                        }.applyIf(shiftHeld && !shouldAdd) {
                            minus(it)
                        }

                        val newSelected = mapOf(
                            !altHeld to newTags,
                            altHeld to (selected[altHeld]?.let { x -> x - it } ?: setOf())
                        )

                        setSelected(newSelected)
                        props.callback?.invoke(newSelected)
                        window.getSelection()?.removeAllRanges()
                    }
                }
            }
            it.type
        }
    }
}
