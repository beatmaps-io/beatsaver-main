package io.beatmaps.maps.testplay

import external.Axios
import external.generateConfig
import io.beatmaps.Config
import io.beatmaps.api.ActionResponse
import io.beatmaps.api.FeedbackUpdate
import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.issues.newIssueComment
import io.beatmaps.util.fcmemo
import kotlinx.datetime.internal.JSJoda.Instant
import react.useRef
import react.useState

val newFeedback = fcmemo<NewFeedbackProps>("newFeedback") { props ->
    val (done, setDone) = useState(false)
    val (text, setText) = useState<String?>(null)
    val (time, setTime) = useState<String?>(null)
    val captchaRef = useRef<ICaptchaHandler>()

    if (done) {
        feedback {
            hash = props.hash
            name = ""
            feedback = text ?: ""
            this.time = time ?: ""
            isOwner = true
        }
    } else {
        newIssueComment {
            buttonText = "Leave feedback"
            saveCallback = { newText ->
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
                this.captchaRef = captchaRef
                page = "timeline"
            }
        }
    }
}
