package external

import io.beatmaps.configContext
import react.ComponentClass
import react.MutableRefObject
import react.Props
import react.RBuilder
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

external interface IReCAPTCHA {
    fun executeAsync(): Promise<String>
    fun reset()
}

object FakeReCaptcha : IReCAPTCHA {
    override fun executeAsync() =
        Promise.resolve("")

    override fun reset() = Unit
}

fun RBuilder.recaptcha(captchaRef: MutableRefObject<IReCAPTCHA>) {
    configContext.Consumer { configData ->
        if (configData?.showCaptcha != false) {
            ReCAPTCHA.default {
                attrs.sitekey = "6LdMpxUaAAAAAA6a3Fb2BOLQk9KO8wCSZ-a_YIaH"
                attrs.size = "invisible"
                ref = captchaRef
            }
        } else {
            captchaRef.current = FakeReCaptcha
        }
    }
}
