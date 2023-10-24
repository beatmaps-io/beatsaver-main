package io.beatmaps.api

import io.beatmaps.common.beatsaber.BMPropertyInfo
import kotlinx.serialization.Serializable

@Serializable
data class FailedUploadResponse(val errors: List<UploadValidationInfo>, val success: Boolean = false)

@Serializable
data class UploadValidationInfo(val property: List<BMPropertyInfo>, val message: String)
