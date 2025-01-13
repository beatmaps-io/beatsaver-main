@file:JvmName("CollaborationData")

package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmName

@Serializable
data class CollaborationRequestData(val mapId: Int, val collaboratorId: Int)

@Serializable
data class CollaborationResponseData(val collaborationId: Int, val accepted: Boolean)

@Serializable
data class CollaborationDetail(val id: Int, val mapId: Int, val collaborator: UserDetail?, val map: MapDetail?, val requestedAt: Instant, val accepted: Boolean)

typealias CollaborationRemoveData = CollaborationRequestData
