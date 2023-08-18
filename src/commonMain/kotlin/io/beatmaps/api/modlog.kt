package io.beatmaps.api

import io.beatmaps.common.IModLogOpAction
import io.beatmaps.common.ModLogOpType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ModLogEntry(
    val moderator: UserDetail,
    val user: UserDetail,
    val map: ModLogMapDetail?,
    val type: ModLogOpType,
    val time: Instant,
    val action: IModLogOpAction
)

@Serializable
data class ModLogMapDetail(
    val id: String,
    val name: String
)
