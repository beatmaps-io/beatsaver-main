package io.beatmaps

import io.beatmaps.common.json
import react.ChildrenBuilder
import react.Context
import react.ProviderProps
import react.createContext
import web.dom.document

object Config {
    const val apibase = "/api"
    const val dateFormat = "YYYY-MM-DD"
}

val globalContext = createContext<UserData?>(null)
val configContext = createContext<ConfigData?>(null)

inline fun <reified T> ChildrenBuilder.provide(context: Context<T?>, id: String, crossinline block: ProviderProps<T?>.() -> Unit) {
    context.Provider {
        value = document.getElementById(id)?.let {
            json.decodeFromString<T>(it.textContent ?: "{}")
        }

        block(this)
    }
}
