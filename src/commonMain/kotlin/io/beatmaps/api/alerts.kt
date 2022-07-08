// ktlint-disable filename
package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserAlert(
    val head: String,
    val body: String,
    val type: EAlertType,
    val time: Instant
)
