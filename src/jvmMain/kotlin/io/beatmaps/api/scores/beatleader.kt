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
            scoresClient.get(
                "https://api.beatleader.xyz/v5/scores/$hash/${diff.enumName()}/${mode.characteristic.enumName()}?page=$page&count=12"
            ).body<BLPaged>()
        }.let {
            LeaderboardData(
                it?.container?.ranked == true,
                it?.container?.leaderboardId,
                it?.data?.map(BLLeaderboardScore::toLeaderboardScore) ?: listOf(),
                false,
                it?.container?.leaderboardId != null
            )
        }
}

data class BLPaged(val metadata: SSPagedMetadata, val container: BLLeaderboardContainer, val data: List<BLLeaderboardScore>)
data class BLLeaderboardContainer(val leaderboardId: String?, val ranked: Boolean)
data class BLLeaderboardScore(
    val id: Long,
    val baseScore: Int,
    val modifiedScore: Int,
    val accuracy: Float,
    val pp: Double,
    val rank: Int,
    val modifiers: String,
    val leaderboardId: String?,
    val timeset: String,
    val timepost: Long,
    val player: String
) {
    fun toLeaderboardScore() = LeaderboardScore(
        0L,
        player,
        rank,
        modifiedScore,
        accuracy,
        pp,
        modifiers.split(',').filter { it.isNotEmpty() }
    )
}
