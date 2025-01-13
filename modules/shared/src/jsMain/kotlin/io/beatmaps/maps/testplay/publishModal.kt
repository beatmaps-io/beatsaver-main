package io.beatmaps.maps.testplay

import external.reactFor
import io.beatmaps.util.toInstant
import kotlinx.datetime.Instant
import kotlinx.datetime.internal.JSJoda.DateTimeFormatter
import kotlinx.datetime.internal.JSJoda.LocalDateTime
import kotlinx.datetime.internal.JSJoda.ZoneId
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import react.MutableRefObject
import react.Props
import react.dom.a
import react.dom.br
import react.dom.defaultValue
import react.dom.div
import react.dom.input
import react.dom.label
import react.dom.p
import react.fc
import react.useState

external interface PublishModalProps : Props {
    var callbackScheduleAt: (Instant?) -> Unit
    var notifyFollowersRef: MutableRefObject<Boolean>
}

val publishModal = fc<PublishModalProps>("publishModal") { props ->
    val (publishType, setPublishType) = useState(false)

    val format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")

    p {
        +"This will make your map visible to everyone"
    }
    p {
        +"You should only publish maps that are completed, if you just want to test your map check out the guides here:"
        br {}
        a("https://bsmg.wiki/mapping/#playtesting") {
            +"https://bsmg.wiki/mapping/#playtesting"
        }
    }
    p {
        +"You should also consider getting your map playtested by other mappers for feedback first"
    }
    p {
        +"Uploading new versions later will cause leaderboards for your map to be reset"
    }
    div("mb-3") {
        div("form-check check-border") {
            label("form-check-label") {
                input(InputType.radio, classes = "form-check-input") {
                    attrs.name = "publishType"
                    attrs.id = "publishTypeNow"
                    attrs.value = "now"
                    attrs.defaultChecked = true
                    attrs.onChangeFunction = {
                        props.callbackScheduleAt(null)
                        setPublishType(false)
                    }
                }
                +"Release immediately"
            }
        }

        div("form-check check-border") {
            label("form-check-label") {
                attrs.reactFor = "publishTypeSchedule"
                input(InputType.radio, classes = "form-check-input") {
                    attrs.name = "publishType"
                    attrs.id = "publishTypeSchedule"
                    attrs.value = "schedule"
                    attrs.onChangeFunction = {
                        setPublishType(true)
                    }
                }
                +"Schedule release"

                if (publishType) {
                    input(InputType.dateTimeLocal, classes = "form-control m-2") {
                        attrs.id = "scheduleAt"
                        val nowStr = LocalDateTime.now(ZoneId.SYSTEM).format(format)
                        attrs.defaultValue = nowStr
                        attrs.min = nowStr
                        attrs.onChangeFunction = {
                            val textVal = (it.target as HTMLInputElement).value
                            props.callbackScheduleAt(if (textVal.isEmpty()) null else textVal.toInstant())
                        }
                    }
                }
            }
        }
    }
    div("form-check form-switch d-inline-block me-2") {
        input(InputType.checkBox, classes = "form-check-input") {
            attrs.defaultChecked = props.notifyFollowersRef.current == true
            attrs.id = "alertUpdate"
            attrs.onChangeFunction = {
                props.notifyFollowersRef.current = (it.currentTarget as HTMLInputElement).checked
            }
        }
        label("form-check-label") {
            attrs.reactFor = "alertUpdate"
            +"Notify followers"
        }
    }
}
