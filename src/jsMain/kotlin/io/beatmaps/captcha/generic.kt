package io.beatmaps.captcha

import react.MutableRefObject
import react.Props
import kotlin.js.Promise

external interface CaptchaProps : Props {
    var captchaRef: MutableRefObject<ICaptchaHandler>
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
