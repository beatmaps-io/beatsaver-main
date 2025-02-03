package io.beatmaps.captcha

import external.ITurnstile
import external.TurnStileRenderOptions
import external.Turnstile
import io.beatmaps.util.fcmemo
import js.objects.jso
import react.RefObject
import react.dom.html.ReactHTML.div
import react.useCallback
import react.useEffectOnceWithCleanup
import react.useMemo
import react.useRef
import react.useState
import web.cssom.Display
import web.cssom.None
import web.cssom.Position
import web.cssom.integer
import web.cssom.px
import web.cssom.rgb
import web.timers.setTimeout
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

class TurnstileHandler(private val ext: RefObject<ITurnstile>, private val pRef: RefObject<(Int) -> Unit>) : ICaptchaHandler {
    override fun execute() =
        ext.current?.let { ext ->
            Promise { resolve, reject ->
                pRef.current = {
                    reject(Exception("Turnstile error [$it]"))
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

val turnstile = fcmemo<CaptchaProps>("TurnstileWrapper") { props ->
    val ref = useRef<ITurnstile>()
    val pRef = useRef<(Int) -> Unit>()
    val (popover, setPopover) = useState(false)

    useEffectOnceWithCleanup {
        onCleanup {
            // Pretend there was an error when unmounting
            pRef.current?.invoke(0)
        }
    }

    div {
        style = jso {
            if (popover) {
                background = rgb(0, 0, 0, 0.5)
                position = Position.absolute
                top = 0.px
                bottom = 0.px
                left = 0.px
                right = 0.px
                display = Display.flex
                zIndex = integer(10000)
            } else {
                display = None.none
            }
        }
        Turnstile.Turnstile {
            if (props.enabled) {
                siteKey = "0x4AAAAAAAk0Ed-ky7FsJ7Ur"
            } else {
                // siteKey = "1x00000000000000000000AA" // Always passes
                // siteKey = "2x00000000000000000000AB" // Always blocks
                siteKey = "3x00000000000000000000FF" // Forced interactive
            }
            this.ref = ref
            options = useMemo(props.page) {
                TurnStileRenderOptions(
                    appearance = "interaction-only",
                    execution = "execute",
                    action = props.page
                )
            }
            onError = useCallback {
                pRef.current?.invoke(it) != null
            }
            onBeforeInteractive = useCallback {
                setPopover(true)
            }
            onAfterInteractive = useCallback {
                setTimeout(1.seconds) {
                    setPopover(false)
                }
            }
        }
    }
    props.captchaRef.current = TurnstileHandler(ref, pRef)
}
