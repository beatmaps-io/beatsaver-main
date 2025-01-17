package io.beatmaps.captcha

import io.beatmaps.configContext
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.useContext
import kotlin.js.Promise

external interface CaptchaProps : Props {
    var captchaRef: RefObject<ICaptchaHandler>
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

val captcha = fcmemo<CaptchaProps>("CaptchaWrapper") { props ->
    val configData = useContext(configContext)

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
