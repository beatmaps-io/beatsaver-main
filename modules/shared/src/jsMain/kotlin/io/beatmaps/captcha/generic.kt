package io.beatmaps.captcha

import io.beatmaps.configContext
import io.beatmaps.util.fcmemo
import react.Props
import react.RefObject
import react.use
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
    val configData = use(configContext)

    when (configData?.captchaProvider) {
        "ReCaptcha" -> recaptcha {
            captchaRef = props.captchaRef
            enabled = configData.showCaptcha
            page = props.page
        }
        "Turnstile" -> turnstile {
            captchaRef = props.captchaRef
            enabled = configData.showCaptcha
            page = props.page
        }
        else -> {
            props.captchaRef.current = FakeCaptchaHandler
        }
    }
}
