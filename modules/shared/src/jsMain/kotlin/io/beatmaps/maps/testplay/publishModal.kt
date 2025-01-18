package io.beatmaps.maps.testplay

import io.beatmaps.util.fcmemo
import io.beatmaps.util.toInstant
import kotlinx.datetime.Instant
import kotlinx.datetime.internal.JSJoda.DateTimeFormatter
import kotlinx.datetime.internal.JSJoda.LocalDateTime
import kotlinx.datetime.internal.JSJoda.ZoneId
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.p
import react.useState
import web.cssom.ClassName
import web.html.InputType

external interface PublishModalProps : Props {
    var callbackScheduleAt: (Instant?) -> Unit
    var notifyFollowersRef: RefObject<Boolean>
}

val publishModal = fcmemo<PublishModalProps>("publishModal") { props ->
    val (publishType, setPublishType) = useState(false)

    val format = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")

    p {
        +"This will make your map visible to everyone"
    }
    p {
        +"You should only publish maps that are completed, if you just want to test your map check out the guides here:"
        br {}
        a {
            href = "https://bsmg.wiki/mapping/#playtesting"
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
        className = ClassName("mb-3")
        div {
            className = ClassName("form-check check-border")
            label {
                className = ClassName("form-check-label")
                input {
                    type = InputType.radio
                    className = ClassName("form-check-input")
                    name = "publishType"
                    id = "publishTypeNow"
                    value = "now"
                    defaultChecked = true
                    onChange = {
                        props.callbackScheduleAt(null)
                        setPublishType(false)
                    }
                }
                +"Release immediately"
            }
        }

        div {
            className = ClassName("form-check check-border")
            label {
                className = ClassName("form-check-label")
                htmlFor = "publishTypeSchedule"
                input {
                    type = InputType.radio
                    className = ClassName("form-check-input")
                    name = "publishType"
                    id = "publishTypeSchedule"
                    value = "schedule"
                    onChange = {
                        setPublishType(true)
                    }
                }
                +"Schedule release"

                if (publishType) {
                    input {
                        type = InputType.datetimeLocal
                        className = ClassName("form-control m-2")
                        id = "scheduleAt"
                        val nowStr = LocalDateTime.now(ZoneId.SYSTEM).format(format)
                        defaultValue = nowStr
                        min = nowStr
                        onChange = {
                            val textVal = it.target.value
                            props.callbackScheduleAt(if (textVal.isEmpty()) null else textVal.toInstant())
                        }
                    }
                }
            }
        }
    }
    div {
        className = ClassName("form-check form-switch d-inline-block me-2")
        input {
            type = InputType.checkbox
            className = ClassName("form-check-input")
            defaultChecked = props.notifyFollowersRef.current == true
            id = "alertUpdate"
            onChange = {
                props.notifyFollowersRef.current = it.currentTarget.checked
            }
        }
        label {
            className = ClassName("form-check-label")
            htmlFor = "alertUpdate"
            +"Notify followers"
        }
    }
}
