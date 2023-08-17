@file:JvmName("CollaborationData")

package io.beatmaps.api

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmName

@Serializable
data class CollaborationRequestData(val mapId: Int, val collaboratorId: Int)

@Serializable
data class CollaborationResponseData(val collaborationId: Int, val accepted: Boolean)

@Serializable
data class CollaborationDetail(val mapId: Int, val collaborator: UserDetail, val accepted: Boolean)

typealias CollaborationRemoveData = CollaborationRequestData
