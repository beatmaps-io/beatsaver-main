package io.beatmaps.captcha

import external.IReCAPTCHA
import external.ReCAPTCHA
import io.beatmaps.configContext
import react.MutableRefObject
import react.RBuilder
import react.fc
import react.useRef

class ReCAPTCHAHandler(val ext: MutableRefObject<IReCAPTCHA>) : ICaptchaHandler {
    override fun execute() = ext.current?.executeAsync()
    override fun reset() = ext.current?.reset()
}

val recaptcha = fc<CaptchaProps>("ReCAPTCHA") { props ->
    val ref = useRef<IReCAPTCHA>()
    configContext.Consumer { configData ->
        if (configData?.showCaptcha != false) {
            ReCAPTCHA.default {
                attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                attrs.size = "invisible"
                this.ref = ref
            }
            props.captchaRef.current = ReCAPTCHAHandler(ref)
        } else {
            props.captchaRef.current = FakeCaptchaHandler
        }
    }
}

// Easily switch between io.beatmaps.captcha.captcha implementations
fun RBuilder.captcha(captchaRef: MutableRefObject<ICaptchaHandler>) = recaptcha {
    attrs.captchaRef = captchaRef
}
