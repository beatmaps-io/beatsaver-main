package io.beatmaps.captcha

import external.IReCAPTCHA
import external.ReCAPTCHA
import react.MutableRefObject
import react.fc
import react.useRef

class ReCAPTCHAHandler(val ext: MutableRefObject<IReCAPTCHA>) : ICaptchaHandler {
    override fun execute() = ext.current?.executeAsync()
    override fun reset() = ext.current?.reset()
}

val recaptcha = fc<CaptchaProps>("ReCaptchaWrapper") { props ->
    val ref = useRef<IReCAPTCHA>()
    ReCAPTCHA.default {
        attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
        attrs.size = "invisible"
        this.ref = ref
    }
    props.captchaRef.current = ReCAPTCHAHandler(ref)
}
