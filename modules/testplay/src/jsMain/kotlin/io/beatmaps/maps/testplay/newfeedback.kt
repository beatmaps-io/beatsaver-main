package io.beatmaps.maps.testplay

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.issues.newIssueComment
import kotlinx.datetime.internal.JSJoda.Instant
import react.fc
import react.useRef
import react.useState

val newFeedback = fc<NewFeedbackProps>("newFeedback") { props ->
    val (done, setDone) = useState(false)
    val (text, setText) = useState<String?>(null)
    val (time, setTime) = useState<String?>(null)
    val captchaRef = useRef<ICaptchaHandler>()

    if (done) {
        feedback {
            attrs.hash = props.hash
            attrs.name = ""
            attrs.feedback = text ?: ""
            attrs.time = time ?: ""
            attrs.isOwner = true
        }
    } else {
        newIssueComment {
            attrs.buttonText = "Leave feedback"
            attrs.saveCallback = { newText ->
                captchaRef.current?.execute()?.then {
                    Axios.post<ActionResponse>("${Config.apibase}/testplay/feedback", FeedbackUpdate(props.hash, newText, it), generateConfig<FeedbackUpdate, ActionResponse>()).then { res ->
                        setDone(true)
                        setTime(Instant.now().toString())
                        setText(newText)

                        res
                    }
                }?.then { it }
            }

            captcha {
                key = "captcha"
                attrs.captchaRef = captchaRef
                attrs.page = "timeline"
            }
        }
    }
}
