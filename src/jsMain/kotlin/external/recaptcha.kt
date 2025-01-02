package external

import io.beatmaps.configContext
import react.ComponentClass
import react.MutableRefObject
import react.Props
import react.RBuilder
import react.useRef
import kotlin.js.Promise

external interface IGoogleReCaptchaProps : Props {
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
    val default: ComponentClass<IGoogleReCaptchaProps>

    fun execute(): String
    override fun executeAsync(): Promise<String>
    override fun reset()
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

class ReCAPTCHAHandler(val ext: MutableRefObject<IReCAPTCHA>) : ICaptchaHandler {
    override fun execute() = ext.current?.executeAsync()
    override fun reset() = ext.current?.reset()
}

external interface IReCAPTCHA {
    fun executeAsync(): Promise<String>
    fun reset()
}

fun RBuilder.recaptcha(captchaRef: MutableRefObject<ICaptchaHandler>) {
    val ref = useRef<IReCAPTCHA>()
    configContext.Consumer { configData ->
        if (configData?.showCaptcha != false) {
            ReCAPTCHA.default {
                attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                attrs.size = "invisible"
                this.ref = ref
            }
            captchaRef.current = ReCAPTCHAHandler(ref)
        } else {
            captchaRef.current = FakeCaptchaHandler
        }
    }
}

// Easily switch between captcha implementations
fun RBuilder.captcha(captchaRef: MutableRefObject<ICaptchaHandler>) = turnstile(captchaRef)
