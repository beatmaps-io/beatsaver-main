package external

import io.beatmaps.configContext
import js.objects.jso
import react.ComponentClass
import react.MutableRefObject
import react.Props
import react.fc
import react.useRef
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
    val default: ComponentClass<IHCaptchaProps>

    override fun execute(opt: HCaptchaExecuteOptions): Promise<HCaptchaResult>
    override fun getResponse(): String
    override fun getRespKey(): String
    override fun resetCaptcha()
}

external interface IHCaptcha {
    fun execute(opt: HCaptchaExecuteOptions): Promise<HCaptchaResult>
    fun getResponse(): String
    fun getRespKey(): String
    fun resetCaptcha()
}

class HCaptchaHandler(val ext: MutableRefObject<IHCaptcha>) : ICaptchaHandler {
    override fun execute() = ext.current?.execute(
        jso {
            async = true
        }
    )?.then { result -> result.response }

    override fun reset() = ext.current?.resetCaptcha()
}

val hcaptcha = fc<CaptchaProps> { props ->
    val ref = useRef<IHCaptcha>()
    configContext.Consumer { configData ->
        if (configData?.showCaptcha != false) {
            HCaptcha.default {
                attrs.sitekey = "b17e6408-276c-462c-bbd8-d72073b0599a"
                // attrs.sitekey = "30000000-ffff-ffff-ffff-000000000003"
                attrs.size = "invisible"
                attrs.theme = "dark"
                this.ref = ref
            }
            props.captchaRef.current = HCaptchaHandler(ref)
        } else {
            props.captchaRef.current = FakeCaptchaHandler
        }
    }
}
