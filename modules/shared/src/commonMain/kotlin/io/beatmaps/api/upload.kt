package io.beatmaps.api

import io.beatmaps.common.beatsaber.BMPropertyInfo
import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(val errors: List<UploadValidationInfo> = listOf(), val success: Boolean = false, val mapId: String = "") {
    constructor(mapId: String) : this(success = true, mapId = mapId)
}

@Serializable
data class UploadValidationInfo(val property: List<BMPropertyInfo>, val message: String) {
    constructor(msg: String) : this(listOf(), msg)
}
