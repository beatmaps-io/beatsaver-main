package io.beatmaps.shared.search

import external.Moment
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.fc
import web.cssom.ClassName

data class PresetDateRange(val startDate: Moment?, val endDate: Moment?)

val presetsMap = mapOf(
    "Last 24h" to PresetDateRange(Moment().subtract(1, "day"), null),
    "Last week" to PresetDateRange(Moment().subtract(1, "week"), null),
    "Last month" to PresetDateRange(Moment().subtract(1, "month"), null),
    "Last 3 months" to PresetDateRange(Moment().subtract(3, "month"), null),
    "All" to PresetDateRange(null, null)
)

external interface PresetProps : Props {
    var callback: (Moment?, Moment?) -> Unit
}

val presets = fc<PresetProps>("presets") { props ->
    div {
        attrs.className = ClassName("presets")
        presetsMap.forEach { preset ->
            button {
                attrs.onClick = {
                    it.preventDefault()
                    props.callback(preset.value.startDate, preset.value.endDate)
                }
                +preset.key
            }
        }
    }
}
