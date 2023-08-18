package io.beatmaps.api

import io.beatmaps.common.MapTag
import io.beatmaps.common.api.EMapState
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable data class SimpleMapInfoUpdate(val id: Int, val tags: List<MapTag>? = null)

@Serializable data class MapInfoUpdate(val id: Int, val name: String? = null, val description: String? = null, val tags: List<MapTag>? = null, val deleted: Boolean = false, val reason: String? = null)

@Serializable data class CurateMap(val id: Int, val curated: Boolean = false, val reason: String? = null)

@Serializable data class ValidateMap(val id: Int, val automapper: Boolean = false)

@Serializable data class StateUpdate(val hash: String, val state: EMapState, val mapId: Int, val reason: String? = null, val scheduleAt: Instant? = null)

@Serializable data class FeedbackUpdate(val hash: String, val feedback: String, val captcha: String? = null)
