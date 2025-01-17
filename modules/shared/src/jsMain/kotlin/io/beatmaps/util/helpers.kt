package io.beatmaps.util

import react.Props
import react.RBuilder
import react.RElementBuilder
import react.dom.FormAction
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML
import react.fc
import react.memo
import web.cssom.ClassName
import web.form.FormMethod
import kotlin.js.Promise

fun <T> Promise<T>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

fun <T> Promise<Promise<T>>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this

fun <T : Props> fcmemo(name: String, block: RBuilder.(props: T) -> Unit) = memo(fc(name, block))

fun RBuilder.form(classes: String, method: FormMethod, action: String, block: RElementBuilder<*>.() -> Unit) {
    ReactHTML.form {
        attrs.className = ClassName(classes)
        attrs.method = method
        attrs.action = action.unsafeCast<FormAction>()
        block()
    }
}

fun HTMLAttributes<*>.setData(key: String, value: Any) {
    asDynamic()["data-$key"] = value
}
