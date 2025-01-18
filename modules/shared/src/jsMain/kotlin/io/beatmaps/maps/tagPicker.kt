package io.beatmaps.maps

import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.util.fcmemo
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import web.cssom.ClassName

fun interface TagPickerHeadingRenderer {
    fun ChildrenBuilder.invoke(info: Map<MapTagType, Int>)
}

external interface TagPickerProps : Props {
    var classes: String?
    var tags: Set<MapTag>?
    var tagUpdateCallback: ((Set<MapTag>) -> Unit)?
    var renderHeading: TagPickerHeadingRenderer?
}

val tagPicker = fcmemo<TagPickerProps>("tagPicker") { props ->
    val tags = props.tags

    div {
        className = ClassName("tags " + (props.classes ?: ""))
        fun renderTag(it: MapTag) {
            mapTag {
                selected = tags?.contains(it) == true
                tag = it
                onClick = { _ ->
                    val shouldAdd = tags == null || (!tags.contains(it) && tags.count { o -> o.type == it.type } < MapTag.maxPerType.getValue(it.type))

                    with(tags ?: setOf()) {
                        if (shouldAdd) {
                            plus(it)
                        } else {
                            minus(it)
                        }
                    }.also {
                        props.tagUpdateCallback?.invoke(it)
                    }
                }
            }
        }

        val byType = (tags?.groupBy { it.type } ?: mapOf()).mapValues {
            it.value.size
        }.withDefault { 0 }

        props.renderHeading?.let { rh ->
            with(rh) {
                this@div.invoke(byType)
            }
        } ?: run {
            h4 {
                val allocationInfo = MapTag.maxPerType.map { "${byType.getValue(it.key)}/${it.value} ${it.key.name}" }.joinToString(", ")
                +"Tags ($allocationInfo):"
            }
        }

        tags?.sortedBy { it.type.ordinal }?.forEach(::renderTag)

        val set = tags ?: setOf()
        MapTag.sorted.minus(set).fold(MapTagType.None) { prev, it ->
            if (it.type != prev) {
                div {
                    className = ClassName("break")
                }
            }

            if (byType.getValue(it.type) < MapTag.maxPerType.getValue(it.type)) {
                renderTag(it)
            }
            it.type
        }
    }
}
