package external

import io.beatmaps.configContext
import react.ComponentClass
import react.MutableRefObject
import react.Props
import react.dom.div
import react.dom.jsStyle
import react.fc
import react.useEffectOnce
import react.useRef
import react.useState
import web.timers.setTimeout
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

external interface ITurnstileProps : Props {
    var siteKey: String
    var `as`: String?
    var options: ITurnstileRenderOptions
    var onWidgetLoad: (String) -> Unit
    var onSuccess: (String) -> Unit
    var onExpire: () -> Unit
    var onError: () -> Unit
    var onBeforeInteractive: () -> Unit
    var onAfterInteractive: () -> Unit
}

external interface ITurnstileRenderOptions {
    var action: String?
    var cData: String?
    var theme: String?
    var language: String?
    var tabIndex: Int?
    var responseField: Boolean?
    var responseFieldName: String?
    var size: String?
    var retry: String?
    var retryInterval: Int?
    var refreshExpired: String?
    var refreshTimeout: String?
    var execution: String?
    var appearance: String?
}

class TurnStileRenderOptions(
    override var action: String? = undefined,
    override var cData: String? = undefined,
    override var theme: String? = undefined,
    override var language: String? = undefined,
    override var tabIndex: Int? = undefined,
    override var responseField: Boolean? = undefined,
    override var responseFieldName: String? = undefined,
    override var size: String? = undefined,
    override var retry: String? = undefined,
    override var retryInterval: Int? = undefined,
    override var refreshExpired: String? = undefined,
    override var refreshTimeout: String? = undefined,
    override var execution: String? = undefined,
    override var appearance: String? = undefined
) : ITurnstileRenderOptions

@JsModule("@marsidev/react-turnstile")
@JsNonModule
external object Turnstile : ITurnstile {
    val Turnstile: ComponentClass<ITurnstileProps>

    override fun getResponse(): String
    override fun getResponsePromise(): Promise<String>
    override fun execute()
    override fun reset()
    override fun remove()
    override fun render()
    override fun isExpired(): Boolean
}

external interface ITurnstile {
    fun getResponse(): String
    fun getResponsePromise(): Promise<String>
    fun execute()
    fun reset()
    fun remove()
    fun render()
    fun isExpired(): Boolean
}

class TurnstileHandler(private val ext: MutableRefObject<ITurnstile>, private val pRef: MutableRefObject<() -> Unit>) : ICaptchaHandler {
    override fun execute() =
        ext.current?.let { ext ->
            Promise { resolve, reject ->
                pRef.current = {
                    reject(Exception("Turnstile error"))
                    reset()
                }

                ext.execute()

                ext.getResponsePromise().then { str ->
                    resolve(str)
                }.catch { e ->
                    reject(e)
                }
            }
        }
    override fun reset() = ext.current?.reset()
}

val turnstile = fc<CaptchaProps> { props ->
    val ref = useRef<ITurnstile>()
    val pRef = useRef<() -> Unit>()
    val (popover, setPopover) = useState(false)

    useEffectOnce {
        cleanup {
            // Pretend there was an error when unmounting
            pRef.current?.invoke()
        }
    }

    configContext.Consumer { configData ->
        div {
            attrs.jsStyle {
                if (popover) {
                    background = "rgba(0,0,0,0.5)"
                    position = "absolute"
                    top = "0"
                    bottom = "0"
                    left = "0"
                    right = "0"
                    display = "flex"
                    zIndex = "10000"
                } else {
                    display = "none"
                }
            }
            Turnstile.Turnstile {
                if (configData?.showCaptcha != false) {
                    attrs.siteKey = "0x4AAAAAAAk0Ed-ky7FsJ7Ur"
                } else {
                    // attrs.siteKey = "1x00000000000000000000AA" // Always passes
                    attrs.siteKey = "2x00000000000000000000AB" // Always blocks
                    // attrs.siteKey = "3x00000000000000000000FF" // Forced interactive
                }
                this.ref = ref
                attrs.options = TurnStileRenderOptions(
                    appearance = "interaction-only",
                    execution = "execute"
                )
                attrs.onError = {
                    pRef.current?.invoke()
                }
                attrs.onBeforeInteractive = {
                    setPopover(true)
                }
                attrs.onAfterInteractive = {
                    setTimeout(1.seconds) {
                        setPopover(false)
                    }
                }
            }
        }
        props.captchaRef.current = TurnstileHandler(ref, pRef)
    }
}
