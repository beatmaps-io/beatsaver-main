package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserAlert(
    val id: Int,
    val head: String,
    val body: String,
    val type: EAlertType,
    val time: Instant
)

@Serializable
data class AlertUpdate(
    val id: Int,
    val read: Boolean
)
