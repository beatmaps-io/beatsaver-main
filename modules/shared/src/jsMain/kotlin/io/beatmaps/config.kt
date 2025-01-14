package io.beatmaps

import io.beatmaps.common.json
import kotlinx.browser.document
import kotlinx.serialization.Serializable
import react.Context
import react.ProviderProps
import react.RBuilder
import react.RElementBuilder
import react.createContext

object Config {
    const val apibase = "/api"
    const val dateFormat = "YYYY-MM-DD"
}

@Serializable
data class UserData(
    val userId: Int = 0,
    val admin: Boolean = false,
    val curator: Boolean = false,
    val suspended: Boolean = false
)

val globalContext = createContext<UserData?>(null)

@Serializable
data class ConfigData(
    // Safe because if captchas are bypassed the backend will still reject requests
    val showCaptcha: Boolean = true,
    val v2Search: Boolean = false,
    val captchaProvider: String = "Fake"
)

val configContext = createContext<ConfigData?>(null)

inline fun <reified T> RBuilder.provide(context: Context<T?>, id: String, crossinline block: RElementBuilder<ProviderProps<T?>>.() -> Unit) {
    context.Provider {
        attrs.value = document.getElementById(id)?.let {
            json.decodeFromString<T>(it.textContent ?: "{}")
        }

        block(this)
    }
}
