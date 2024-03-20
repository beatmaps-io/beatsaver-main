package io.beatmaps.maps

import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import kotlinx.html.title
import react.Props
import react.dom.div
import react.dom.span
import react.fc

external interface MapRequirementsProps : Props {
    var margins: String?
    var version: MapVersion
}

val mapRequirements = fc<MapRequirementsProps> { props ->
    val margins = props.margins ?: "me-2 mb-2"

    val requirementConditions = mapOf<String, (MapDifficulty) -> Boolean>(
        "Noodle Extensions" to { it.ne },
        "Mapping Extensions" to { it.me },
        "Chroma" to { it.chroma },
        "Cinema" to { it.cinema }
    )

    val requirements = requirementConditions.mapNotNull { (name, condition) ->
        if (props.version.diffs.any(condition)) name else null
    }

    requirements.forEach { requirement ->
        div("badge badge-warning $margins") {
            span {
                attrs.title = requirement
                +requirement
            }
        }
    }
}
