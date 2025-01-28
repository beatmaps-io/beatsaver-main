package io.beatmaps.maps

import io.beatmaps.api.MapDifficulty
import io.beatmaps.api.MapVersion
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface MapRequirementsProps : Props {
    var margins: String?
    var version: MapVersion
}

val mapRequirements = fcmemo<MapRequirementsProps>("mapRequirements") { props ->
    val margins = props.margins ?: "me-2 mb-2"

    val requirementConditions = mapOf<String, (MapDifficulty) -> Boolean>(
        "Noodle Extensions" to { it.ne },
        "Mapping Extensions" to { it.me },
        "Chroma" to { it.chroma },
        "Cinema" to { it.cinema },
        "Vivify" to { it.vivify }
    )

    val requirements = requirementConditions.mapNotNull { (name, condition) ->
        if (props.version.diffs.any(condition)) name else null
    }

    requirements.forEach { requirement ->
        div {
            className = ClassName("badge badge-warning $margins")
            span {
                title = requirement
                +requirement
            }
        }
    }
}
