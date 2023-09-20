package io.beatmaps.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)
class ApiException(private val error: String) : Exception(error) {
    fun toResponse() = ErrorResponse(error)
}
