package io.beatmaps.api

import io.beatmaps.common.api.EAlertType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserAlert(
    val id: Int? = null,
    val head: String,
    val body: String,
    val type: EAlertType,
    val time: Instant,
    val collaborationId: Int? = null
)

@Serializable
data class UserAlertStats(
    val unread: Int,
    val read: Int,
    val byType: Map<EAlertType, Int>,
    val curationAlerts: Boolean,
    val reviewAlerts: Boolean
) {
    companion object {
        fun fromParts(statParts: List<StatPart>) = UserAlertStats(
            statParts.filter { !it.isRead }.sumOf { it.count }.toInt(),
            statParts.filter { it.isRead }.sumOf { it.count }.toInt(),
            statParts.groupBy { it.type }.mapValues { it.value.sumOf { v -> v.count }.toInt() },
            curationAlerts = false, reviewAlerts = false
        )
    }
}

data class StatPart(val type: EAlertType, val isRead: Boolean, val count: Long)

@Serializable
data class AlertUpdate(val id: Int, val read: Boolean)

@Serializable
data class AlertOptionsRequest(val curationAlerts: Boolean, val reviewAlerts: Boolean)

@Serializable
data class AlertUpdateAll(val read: Boolean)
