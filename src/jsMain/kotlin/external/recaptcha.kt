package external

import react.RBuilder
import react.RClass
import react.RProps
import react.RReadableRef
import kotlin.js.Promise

external interface IGoogleReCaptchaProps : RProps {
    var sitekey: String
    var onChange: (Any) -> Unit
    var theme: String?
    var type: String?
    var tabindex: Int?
    var onExpired: () -> Unit
    var onErrored: () -> Unit
    var stoken: String?
    var hl: String?
    var size: String?
    var badge: String?
}

@JsModule("react-google-recaptcha")
@JsNonModule
external object ReCAPTCHA {
    val default: RClass<IGoogleReCaptchaProps>

    fun execute(): String
    fun executeAsync(): Promise<String>
    fun reset()
}

fun RBuilder.recaptcha(captchaRef: RReadableRef<ReCAPTCHA>) {
    ReCAPTCHA.default {
        attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
        attrs.size = "invisible"
        ref = captchaRef
    }
}
