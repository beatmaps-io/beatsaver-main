package io.beatmaps.api

import io.beatmaps.api.scores.BeatLeaderScores
import io.beatmaps.api.scores.ScoreSaberScores
import io.beatmaps.common.Config
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

@Location("/api/scores") class ScoresApi {
    @Location("/{key}/{page}") data class Leaderboard(
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
        LeaderboardType.ScoreSaber to ScoreSaberScores(),
        LeaderboardType.BeatLeader to BeatLeaderScores()
    )

    options<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", Config.siteBase())
        call.respond(HttpStatusCode.OK)
    }

    get<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(
            lookup[it.type]!!.getLeaderboard(
                it.key,
                EDifficulty.fromInt(it.difficulty) ?: EDifficulty.ExpertPlus,
                SSGameMode.fromInt(it.gameMode) ?: SSGameMode.SoloStandard,
                it.page
            )
        )
    }
}
