package external

import react.ComponentClass
import react.Props
import kotlin.js.Promise

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
