package io.beatmaps.maps

import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagType
import io.beatmaps.util.fcmemo
import react.Props
import react.RBuilder
import react.dom.div
import react.dom.h4

fun interface TagPickerHeadingRenderer {
    fun RBuilder.invoke(info: Map<MapTagType, Int>)
}

external interface TagPickerProps : Props {
    var classes: String?
    var tags: Set<MapTag>?
    var tagUpdateCallback: ((Set<MapTag>) -> Unit)?
    var renderHeading: TagPickerHeadingRenderer?
}

val tagPicker = fcmemo<TagPickerProps>("tagPicker") { props ->
    val tags = props.tags

    div("tags " + (props.classes ?: "")) {
        fun renderTag(it: MapTag) {
            mapTag {
                attrs.selected = tags?.contains(it) == true
                attrs.tag = it
                attrs.onClick = { _ ->
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
            if (it.type != prev) div("break") {}

            if (byType.getValue(it.type) < MapTag.maxPerType.getValue(it.type)) {
                renderTag(it)
            }
            it.type
        }
    }
}
