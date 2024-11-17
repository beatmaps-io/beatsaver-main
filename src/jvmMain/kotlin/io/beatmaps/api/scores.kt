package io.beatmaps.api

import io.beatmaps.api.scores.BeatLeaderScores
import io.beatmaps.api.scores.ScoreSaberScores
import io.beatmaps.api.scores.scoresClient
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

@Location("/api/scores")
class ScoresApi {
    @Location("/{key}/{page}")
    data class Leaderboard(
        val key: String,
        val page: Int = 1,
        val difficulty: Int = -1,
        val gameMode: Int = -1,
        val type: LeaderboardType? = LeaderboardType.ScoreSaber,
        val api: ScoresApi
    )
}

fun Route.scoresRoute() {
    val lookup = mapOf(
        LeaderboardType.ScoreSaber to ScoreSaberScores(scoresClient),
        LeaderboardType.BeatLeader to BeatLeaderScores(scoresClient)
    )

    getWithOptions<ScoresApi.Leaderboard> {
        val response = lookup[it.type]?.getLeaderboard(
            it.key,
            EDifficulty.fromInt(it.difficulty) ?: EDifficulty.ExpertPlus,
            SSGameMode.fromInt(it.gameMode) ?: SSGameMode.SoloStandard,
            it.page
        ) ?: throw UserApiException("Unknown leaderboard")

        call.respond(response)
    }
}
