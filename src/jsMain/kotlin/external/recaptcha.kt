package external

import react.RClass
import react.RProps
import kotlin.js.Promise

external interface IGoogleReCaptchaProps: RProps {
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
}