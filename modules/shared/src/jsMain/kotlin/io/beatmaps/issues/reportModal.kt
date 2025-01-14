package io.beatmaps.issues

import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.shared.form.errors
import org.w3c.dom.HTMLTextAreaElement
import react.MutableRefObject
import react.Props
import react.RefObject
import react.dom.p
import react.dom.textarea
import react.fc

external interface ReportModalProps : Props {
    var subject: String?
    var content: Boolean?
    var reasonRef: RefObject<HTMLTextAreaElement>?
    var captchaRef: MutableRefObject<ICaptchaHandler>
    var errorsRef: RefObject<List<String>>?
}

val reportModal = fc<ReportModalProps>("reportModal") { props ->
    p {
        +"Why are you reporting this ${if (props.content != false || props.subject == null) "content" else props.subject}? Please give as much detail as possible why you feel this ${props.subject ?: "item"} has violated our TOS:"
    }
    textarea(classes = "form-control") {
        ref = props.reasonRef
    }
    errors {
        attrs.errors = props.errorsRef?.current
    }
    captcha {
        attrs.captchaRef = props.captchaRef
        attrs.page = "report"
    }
}
