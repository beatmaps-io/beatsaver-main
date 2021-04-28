package io.beatmaps.api

import kotlinx.serialization.Serializable

@Serializable
data class UserDetail(val id: Int, val name: String, val hash: String? = null, val testplay: Boolean? = null, val avatar: String, val stats: UserStats? = null) { companion object }
@Serializable
data class UserStats(val totalUpvotes: Int, val totalDownvotes: Int, val totalMaps: Int, val avgBpm: Float)
@Serializable
data class BeatsaverLink(val md: String, val linked: Boolean) { companion object }