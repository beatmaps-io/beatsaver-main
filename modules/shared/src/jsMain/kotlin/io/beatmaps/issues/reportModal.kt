package io.beatmaps.issues

import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.shared.form.errors
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.textarea
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface ReportModalProps : Props {
    var subject: String?
    var content: Boolean?
    var reasonRef: RefObject<HTMLTextAreaElement>?
    var captchaRef: RefObject<ICaptchaHandler>
    var errorsRef: RefObject<List<String>>?
}

val reportModal = fcmemo<ReportModalProps>("reportModal") { props ->
    p {
        +"Why are you reporting this ${if (props.content != false || props.subject == null) "content" else props.subject}? Please give as much detail as possible why you feel this ${props.subject ?: "item"} has violated our TOS:"
    }
    textarea {
        className = ClassName("form-control")
        ref = props.reasonRef
    }
    errors {
        errors = props.errorsRef?.current
    }
    captcha {
        key = "captcha"
        captchaRef = props.captchaRef
        page = "report"
    }
}
