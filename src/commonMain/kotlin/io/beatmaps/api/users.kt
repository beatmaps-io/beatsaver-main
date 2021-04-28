package io.beatmaps.api

import kotlinx.serialization.Serializable

@Serializable
data class UserDetail(val id: Int, val name: String, val hash: String? = null, val testplay: Boolean? = null, val avatar: String, val stats: UserStats? = null) { companion object }
@Serializable
data class UserStats(val totalUpvotes: Int, val totalDownvotes: Int, val totalMaps: Int, val avgBpm: Float, val avgScore: Float, val avgDuration: Float, val diffStats: UserDiffStats? = null)
@Serializable
data class UserDiffStats(val total: Int, val easy: Int, val normal: Int, val hard: Int, val expert: Int, val expertPlus: Int)
@Serializable
data class BeatsaverLink(val md: String, val linked: Boolean) { companion object }