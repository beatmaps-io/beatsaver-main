package io.beatmaps.api

import kotlinx.serialization.Serializable

sealed class ApiException(protected val error: String) : Exception(error) {
    abstract fun toResponse(): ActionResponse
}

class ServerApiException(error: String) : ApiException(error) {
    override fun toResponse() = ActionResponse.error(error)
}

class UserApiException(error: String) : ApiException(error) {
    override fun toResponse() = ActionResponse.error(error)
}

interface IActionResponse {
    val success: Boolean
    val errors: List<String>
}

@Serializable
data class ActionResponse(override val success: Boolean, override val errors: List<String> = listOf()) : IActionResponse {
    companion object {
        fun success() = ActionResponse(true)
        // fun <T> success(data: T) = ActionResponse(true, data)
        fun error(vararg errors: String): ActionResponse = error(errors.toList())
        private fun error(errors: List<String> = listOf()) = ActionResponse(false, errors = errors)
    }
}
