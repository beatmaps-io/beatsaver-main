import external.HCaptcha
import external.IHCaptcha
import io.beatmaps.captcha.CaptchaProps
import io.beatmaps.captcha.ICaptchaHandler
import js.objects.jso
import react.MutableRefObject
import react.fc
import react.useRef

class HCaptchaHandler(val ext: MutableRefObject<IHCaptcha>) : ICaptchaHandler {
    override fun execute() = ext.current?.execute(
        jso {
            async = true
        }
    )?.then { result -> result.response }

    override fun reset() = ext.current?.resetCaptcha()
}

val hcaptcha = fc<CaptchaProps>("HCaptchaWrapper") { props ->
    val ref = useRef<IHCaptcha>()
    HCaptcha.default {
        if (props.enabled) {
            attrs.sitekey = "b17e6408-276c-462c-bbd8-d72073b0599a"
        } else {
            attrs.sitekey = "30000000-ffff-ffff-ffff-000000000003"
        }
        attrs.size = "invisible"
        attrs.theme = "dark"
        this.ref = ref
    }
    props.captchaRef.current = HCaptchaHandler(ref)
}
