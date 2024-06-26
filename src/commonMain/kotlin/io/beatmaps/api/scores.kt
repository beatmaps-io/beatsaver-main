package io.beatmaps.api

import kotlinx.serialization.Serializable

enum class LeaderboardType(val url: String) {
    ScoreSaber("https://scoresaber.com/leaderboard/"),
    BeatLeader("https://www.beatleader.xyz/leaderboard/global/");

    companion object {
        private val map = entries.associateBy(LeaderboardType::name)
        fun fromName(name: String?) = map[name]
    }
}

@Serializable
data class LeaderboardData(val ranked: Boolean, val uid: String? = null, val scores: List<LeaderboardScore>, val mods: Boolean, val valid: Boolean) {
    companion object {
        val EMPTY = LeaderboardData(false, null, listOf(), false, valid = false)
    }
}

@Serializable
data class LeaderboardScore(
    val playerId: Long?,
    val name: String,
    val rank: Int,
    val score: Int,
    val accuracy: Float?,
    val pp: Double,
    val mods: List<String>
)
