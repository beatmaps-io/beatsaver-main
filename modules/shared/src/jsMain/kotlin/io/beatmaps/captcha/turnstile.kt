package io.beatmaps.captcha

import external.ITurnstile
import external.TurnStileRenderOptions
import external.Turnstile
import io.beatmaps.util.fcmemo
import react.RefObject
import react.dom.div
import react.dom.jsStyle
import react.useCallback
import react.useEffectOnceWithCleanup
import react.useMemo
import react.useRef
import react.useState
import web.timers.setTimeout
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

class TurnstileHandler(private val ext: RefObject<ITurnstile>, private val pRef: RefObject<() -> Unit>) : ICaptchaHandler {
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

val turnstile = fcmemo<CaptchaProps>("TurnstileWrapper") { props ->
    val ref = useRef<ITurnstile>()
    val pRef = useRef<() -> Unit>()
    val (popover, setPopover) = useState(false)

    useEffectOnceWithCleanup {
        onCleanup {
            // Pretend there was an error when unmounting
            pRef.current?.invoke()
        }
    }

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
            if (props.enabled) {
                attrs.siteKey = "0x4AAAAAAAk0Ed-ky7FsJ7Ur"
            } else {
                // attrs.siteKey = "1x00000000000000000000AA" // Always passes
                // attrs.siteKey = "2x00000000000000000000AB" // Always blocks
                attrs.siteKey = "3x00000000000000000000FF" // Forced interactive
            }
            this.ref = ref
            attrs.options = useMemo(props.page) {
                TurnStileRenderOptions(
                    appearance = "interaction-only",
                    execution = "execute",
                    action = props.page
                )
            }
            attrs.onError = useCallback {
                pRef.current?.invoke()
            }
            attrs.onBeforeInteractive = useCallback {
                setPopover(true)
            }
            attrs.onAfterInteractive = useCallback {
                setTimeout(1.seconds) {
                    setPopover(false)
                }
            }
        }
    }
    props.captchaRef.current = TurnstileHandler(ref, pRef)
}
