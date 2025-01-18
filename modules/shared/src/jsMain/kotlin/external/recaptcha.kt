package external

import react.ComponentModule
import react.ComponentType
import react.PropsWithRef
import kotlin.js.Promise

external interface IGoogleReCaptchaProps : PropsWithRef<ReCAPTCHA> {
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
external object ReCAPTCHA : IReCAPTCHA {
    override val default: ComponentType<IGoogleReCaptchaProps>

    fun execute(): String
    override fun executeAsync(): Promise<String>
    override fun reset()
}

external interface IReCAPTCHA : ComponentModule<IGoogleReCaptchaProps> {
    fun executeAsync(): Promise<String>
    fun reset()
}
