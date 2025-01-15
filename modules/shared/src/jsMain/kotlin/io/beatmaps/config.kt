package io.beatmaps

import io.beatmaps.common.json
import kotlinx.browser.document
import react.Context
import react.ProviderProps
import react.RBuilder
import react.RElementBuilder
import react.createContext

object Config {
    const val apibase = "/api"
    const val dateFormat = "YYYY-MM-DD"
}

val globalContext = createContext<UserData?>(null)
val configContext = createContext<ConfigData?>(null)

inline fun <reified T> RBuilder.provide(context: Context<T?>, id: String, crossinline block: RElementBuilder<ProviderProps<T?>>.() -> Unit) {
    context.Provider {
        attrs.value = document.getElementById(id)?.let {
            json.decodeFromString<T>(it.textContent ?: "{}")
        }

        block(this)
    }
}
