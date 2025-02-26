@file:UseSerializers(OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import io.beatmaps.api.scores.BeatLeaderScores
import io.beatmaps.api.scores.ScoreSaberScores
import io.beatmaps.api.scores.scoresClient
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.beatsaber.leaderboard.SSGameMode
import io.beatmaps.common.or
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.ktor.resources.Resource
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.UseSerializers

@Resource("/api/scores")
class ScoresApi {
    @Resource("/{key}/{page}")
    data class Leaderboard(
        val key: String,
        @ModelClass(Int::class)
        val page: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val difficulty: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val gameMode: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(LeaderboardType::class)
        val type: OptionalProperty<LeaderboardType>? = OptionalProperty.NotPresent,
        @Ignore
        val api: ScoresApi
    ) {
        init {
            requireParams(
                paramInfo(Leaderboard::page), paramInfo(Leaderboard::difficulty), paramInfo(Leaderboard::gameMode), paramInfo(Leaderboard::type)
            )
        }
    }
}

fun Route.scoresRoute() {
    val lookup = mapOf(
        LeaderboardType.ScoreSaber to ScoreSaberScores(scoresClient),
        LeaderboardType.BeatLeader to BeatLeaderScores(scoresClient)
    )

    getWithOptions<ScoresApi.Leaderboard> {
        val response = lookup[it.type.or(LeaderboardType.ScoreSaber)]?.getLeaderboard(
            it.key,
            EDifficulty.fromInt(it.difficulty.or(-1)) ?: EDifficulty.ExpertPlus,
            SSGameMode.fromInt(it.gameMode.or(-1)) ?: SSGameMode.SoloStandard,
            it.page.or(1)
        ) ?: throw UserApiException("Unknown leaderboard")

        call.respond(response)
    }
}
