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
    val time: Instant,
    val collaborationId: Int?
)

@Serializable
data class UserAlertStats(
    val unread: Int,
    val read: Int,
    val byType: Map<EAlertType, Int>
) {
    companion object {
        fun fromParts(statParts: List<StatPart>) = UserAlertStats(
            statParts.filter { !it.isRead }.sumOf { it.count }.toInt(),
            statParts.filter { it.isRead }.sumOf { it.count }.toInt(),
            statParts.groupBy { it.type }.mapValues { it.value.sumOf { v -> v.count }.toInt() }
        )
    }
}

data class StatPart(val type: EAlertType, val isRead: Boolean, val count: Long)

@Serializable
data class AlertUpdate(
    val id: Int,
    val read: Boolean
)

@Serializable
data class AlertUpdateAll(
    val read: Boolean
)
