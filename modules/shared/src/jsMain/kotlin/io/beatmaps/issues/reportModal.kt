package io.beatmaps.issues

import io.beatmaps.captcha.ICaptchaHandler
import io.beatmaps.captcha.captcha
import io.beatmaps.shared.form.errors
import react.Props
import react.RefObject
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.textarea
import react.fc
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

external interface ReportModalProps : Props {
    var subject: String?
    var content: Boolean?
    var reasonRef: RefObject<HTMLTextAreaElement>?
    var captchaRef: RefObject<ICaptchaHandler>
    var errorsRef: RefObject<List<String>>?
}

val reportModal = fc<ReportModalProps>("reportModal") { props ->
    p {
        +"Why are you reporting this ${if (props.content != false || props.subject == null) "content" else props.subject}? Please give as much detail as possible why you feel this ${props.subject ?: "item"} has violated our TOS:"
    }
    textarea {
        attrs.className = ClassName("form-control")
        ref = props.reasonRef
    }
    errors {
        attrs.errors = props.errorsRef?.current
    }
    captcha {
        key = "captcha"
        attrs.captchaRef = props.captchaRef
        attrs.page = "report"
    }
}
