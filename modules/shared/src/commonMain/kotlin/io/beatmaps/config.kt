package io.beatmaps

import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val userId: Int = 0,
    val admin: Boolean = false,
    val curator: Boolean = false,
    val suspended: Boolean = false,
    val blurnsfw: Boolean = true
)

@Serializable
data class ConfigData(
    // Safe because if captchas are bypassed the backend will still reject requests
    val showCaptcha: Boolean = true,
    val v2Search: Boolean = false,
    val captchaProvider: String = "Fake"
)
