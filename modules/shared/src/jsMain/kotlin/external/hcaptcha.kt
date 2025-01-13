package external

import react.ComponentClass
import react.ComponentModule
import react.Props
import kotlin.js.Promise

external interface IHCaptchaProps : Props {
    var sitekey: String
    var size: String?
    var theme: String?
    var tabindex: Int?
    var languageOverride: String?
    var reCaptchaCompat: Boolean?
    var id: String?
    var cleanup: Boolean?
    var loadAsync: Boolean?
    var onError: () -> Unit
    var onVerify: (String, String) -> Unit
    var onExpire: () -> Unit
    var onLoad: () -> Unit
    var onOpen: () -> Unit
    var onClose: () -> Unit
    var onChalExpired: () -> Unit
}

external interface HCaptchaResult {
    val response: String
    val key: String
}

external interface HCaptchaExecuteOptions {
    var async: Boolean
}

@JsModule("@hcaptcha/react-hcaptcha")
@JsNonModule
external object HCaptcha : IHCaptcha {
    override val default: ComponentClass<IHCaptchaProps>

    override fun execute(opt: HCaptchaExecuteOptions): Promise<HCaptchaResult>
    override fun getResponse(): String
    override fun getRespKey(): String
    override fun resetCaptcha()
}

external interface IHCaptcha : ComponentModule<IHCaptchaProps> {
    fun execute(opt: HCaptchaExecuteOptions): Promise<HCaptchaResult>
    fun getResponse(): String
    fun getRespKey(): String
    fun resetCaptcha()
}
