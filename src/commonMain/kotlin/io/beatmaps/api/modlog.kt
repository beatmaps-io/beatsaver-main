// ktlint-disable filename
package io.beatmaps.api

import io.beatmaps.common.ModLogOpType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ModLogEntry(
    val moderator: String,
    val user: String,
    val map: MapDetail?,
    val type: ModLogOpType,
    val time: Instant
)
