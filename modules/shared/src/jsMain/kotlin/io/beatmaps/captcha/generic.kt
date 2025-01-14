package io.beatmaps.captcha

import io.beatmaps.configContext
import react.MutableRefObject
import react.Props
import react.fc
import kotlin.js.Promise

external interface CaptchaProps : Props {
    var captchaRef: MutableRefObject<ICaptchaHandler>
    var enabled: Boolean
    var page: String
}

interface ICaptchaHandler {
    fun execute(): Promise<String>?
    fun reset(): Unit?
}

object FakeCaptchaHandler : ICaptchaHandler {
    override fun execute() =
        Promise.resolve("")
    override fun reset() = Unit
}

val captcha = fc<CaptchaProps>("CaptchaWrapper") { props ->
    configContext.Consumer { configData ->
        when (configData?.captchaProvider) {
            "ReCaptcha" -> recaptcha {
                attrs.captchaRef = props.captchaRef
                attrs.enabled = configData.showCaptcha
                attrs.page = props.page
            }
            "Turnstile" -> turnstile {
                attrs.captchaRef = props.captchaRef
                attrs.enabled = configData.showCaptcha
                attrs.page = props.page
            }
            else -> {
                props.captchaRef.current = FakeCaptchaHandler
            }
        }
    }
}
