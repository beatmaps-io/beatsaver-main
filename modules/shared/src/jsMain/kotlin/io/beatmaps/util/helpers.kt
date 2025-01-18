package io.beatmaps.util

import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.FormAction
import react.dom.html.HTMLAttributes
import react.dom.html.ReactHTML.form
import react.memo
import web.cssom.ClassName
import web.form.FormMethod
import web.storage.Storage
import kotlin.js.Promise

fun <T> Promise<T>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

fun <T> Promise<Promise<T>>.orCatch(block: (Throwable) -> T) =
    then({ it }, block)

inline fun <T> T.applyIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this

fun <T : Props> fcmemo(name: String, block: ChildrenBuilder.(props: T) -> Unit) = memo(FC(name, block))

fun ChildrenBuilder.form(classes: String, method: FormMethod, action: String, block: () -> Unit) {
    form {
        className = ClassName(classes)
        this.method = method
        this.action = action.unsafeCast<FormAction>()
        block()
    }
}

fun HTMLAttributes<*>.setData(key: String, value: Any) {
    asDynamic()["data-$key"] = value
}

operator fun Storage.get(key: String) = getItem(key)

operator fun Storage.set(key: String, value: String) {
    setItem(key, value)
}
