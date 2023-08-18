package io.beatmaps.api

import kotlinx.serialization.Serializable

@Serializable
data class FailedUploadResponse(val errors: List<String>, val success: Boolean = false)
