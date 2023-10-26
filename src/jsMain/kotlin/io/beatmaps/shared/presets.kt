package io.beatmaps.shared

import external.Moment
import kotlinx.html.js.onClickFunction
import react.Props
import react.dom.button
import react.dom.div
import react.fc

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

val presets = fc<PresetProps> { props ->
    div("presets") {
        presetsMap.forEach { preset ->
            button {
                attrs.onClickFunction = {
                    it.preventDefault()
                    props.callback(preset.value.startDate, preset.value.endDate)
                }
                +preset.key
            }
        }
    }
}
