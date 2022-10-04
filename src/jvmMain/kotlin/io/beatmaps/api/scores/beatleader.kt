package io.beatmaps.api.scores

import io.beatmaps.api.LeaderboardData
import io.beatmaps.api.LeaderboardScore
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.ktor.client.call.body
import io.ktor.client.request.get

class BeatLeaderScores : RemoteScores {
    override suspend fun getLeaderboard(hash: String, diff: EDifficulty, mode: SSGameMode, page: Int) =
        ssTry {
            val context = "Standard" // Standard, Modifiers
            val scope = "Global" // Global, Friends, Country
            scoresClient.get(
                "https://api.beatleader.xyz/v3/scores/$hash/${diff.enumName()}/${mode.characteristic.enumName()}/$context/$scope/page?page=$page&count=12"
            ).body<BLPaged>()
        }.let {
            val firstScore = it?.data?.firstOrNull()
            // null -> false
            // 0.0 -> false
            // 1.0 -> true
            LeaderboardData(
                firstScore?.pp?.let { pp -> pp > 0.0 } == true,
                firstScore?.leaderboardId,
                it?.data?.map(BLLeaderboardScore::toLeaderboardScore) ?: listOf(),
                false,
                firstScore != null
            )
        }
}

data class BLPaged(val metadata: SSPagedMetadata, val data: List<BLLeaderboardScore>)
data class BLLeaderboardScore(
    val id: Long,
    val baseScore: Int,
    val modifiedScore: Int,
    val accuracy: Float,
    val playerId: String,
    val pp: Double,
    val bonusPp: Double,
    val rank: Int,
    val countryRank: Int,
    val replay: String,
    val modifiers: String,
    val badCuts: Int,
    val missedNotes: Int,
    val bombCuts: Int,
    val wallsHit: Int,
    val pauses: Int,
    val fullCombo: Boolean,
    val platform: String,
    val hmd: Int,
    val leaderboardId: String,
    val timeset: String,
    val timepost: Long,
    val replaysWatched: Int,
    val player: BLLeaderboardPlayer,
    val scoreImprovement: BLLeaderboardImprovement?,
    val rankVoting: Any?,
    val metadata: Any?
) {
    fun toLeaderboardScore() = LeaderboardScore(
        playerId.toLong(),
        player.name,
        rank,
        modifiedScore,
        pp,
        modifiers.split(',').filter { it.isNotEmpty() }
    )
}
data class BLLeaderboardPlayer(
    val id: String,
    val name: String,
    val platform: String,
    val avatar: String,
    val country: String,
    val pp: Double,
    val rank: Int,
    val countryRank: Int,
    val role: String,
    val socials: List<Any>,
    val patreonFeatures: Any?,
    val profileSettings: Any?,
    val clans: List<Any>
)
data class BLLeaderboardImprovement(
    val id: Long,
    val timeset: String,
    val score: Int,
    val accuracy: Float,
    val pp: Double,
    val bonusPp: Double,
    val rank: Int,
    val accRight: Float,
    val accLeft: Float,
    val averageRankedAccuracy: Float,
    val totalPp: Double,
    val totalRank: Int,
    val badCuts: Int,
    val missedNotes: Int,
    val bombCuts: Int,
    val wallsHit: Int,
    val pauses: Int
)