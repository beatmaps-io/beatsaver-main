package io.beatmaps.captcha

import external.IReCAPTCHA
import external.ReCAPTCHA
import io.beatmaps.util.fcmemo
import react.RefObject
import react.useRef

class ReCAPTCHAHandler(val ext: RefObject<IReCAPTCHA>) : ICaptchaHandler {
    override fun execute() = ext.current?.executeAsync()
    override fun reset() = ext.current?.reset()
}

val recaptcha = fcmemo<CaptchaProps>("ReCaptchaWrapper") { props ->
    val ref = useRef<IReCAPTCHA>()
    ReCAPTCHA.default {
        sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
        size = "invisible"
        this.ref = ref
    }
    props.captchaRef.current = ReCAPTCHAHandler(ref)
}
