package io.beatmaps.maps.testplay

import io.beatmaps.util.toInstant
import kotlinx.datetime.Instant
import kotlinx.datetime.internal.JSJoda.DateTimeFormatter
import kotlinx.datetime.internal.JSJoda.LocalDateTime
import kotlinx.datetime.internal.JSJoda.ZoneId
import org.w3c.dom.HTMLInputElement
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.fc
import react.useState
import web.cssom.ClassName
import web.html.InputType

external interface PublishModalProps : Props {
    var callbackScheduleAt: (Instant?) -> Unit
    var notifyFollowersRef: RefObject<Boolean>
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
        a {
            attrs.href = "https://bsmg.wiki/mapping/#playtesting"
            +"https://bsmg.wiki/mapping/#playtesting"
        }
    }
    p {
        +"You should also consider getting your map playtested by other mappers for feedback first"
    }
    p {
        +"Uploading new versions later will cause leaderboards for your map to be reset"
    }
    div {
        attrs.className = ClassName("mb-3")
        div {
            attrs.className = ClassName("form-check check-border")
            label {
                attrs.className = ClassName("form-check-label")
                input {
                    attrs.type = InputType.radio
                    attrs.className = ClassName("form-check-input")
                    attrs.name = "publishType"
                    attrs.id = "publishTypeNow"
                    attrs.value = "now"
                    attrs.defaultChecked = true
                    attrs.onChange = {
                        props.callbackScheduleAt(null)
                        setPublishType(false)
                    }
                }
                +"Release immediately"
            }
        }

        div {
            attrs.className = ClassName("form-check check-border")
            label {
                attrs.className = ClassName("form-check-label")
                attrs.htmlFor = "publishTypeSchedule"
                input {
                    attrs.type = InputType.radio
                    attrs.className = ClassName("form-check-input")
                    attrs.name = "publishType"
                    attrs.id = "publishTypeSchedule"
                    attrs.value = "schedule"
                    attrs.onChange = {
                        setPublishType(true)
                    }
                }
                +"Schedule release"

                if (publishType) {
                    input {
                        attrs.type = InputType.datetimeLocal
                        attrs.className = ClassName("form-control m-2")
                        attrs.id = "scheduleAt"
                        val nowStr = LocalDateTime.now(ZoneId.SYSTEM).format(format)
                        attrs.defaultValue = nowStr
                        attrs.min = nowStr
                        attrs.onChange = {
                            val textVal = it.target.value
                            props.callbackScheduleAt(if (textVal.isEmpty()) null else textVal.toInstant())
                        }
                    }
                }
            }
        }
    }
    div {
        attrs.className = ClassName("form-check form-switch d-inline-block me-2")
        input {
            attrs.type = InputType.checkbox
            attrs.className = ClassName("form-check-input")
            attrs.defaultChecked = props.notifyFollowersRef.current == true
            attrs.id = "alertUpdate"
            attrs.onChange = {
                props.notifyFollowersRef.current = (it.currentTarget as HTMLInputElement).checked
            }
        }
        label {
            attrs.className = ClassName("form-check-label")
            attrs.htmlFor = "alertUpdate"
            +"Notify followers"
        }
    }
}
