package io.beatmaps.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EDifficulty
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route

val tagsRegex = Regex("(<([^>]+)>)")
val scoresClient = HttpClient(Apache) {
    install(HttpCookies) {
        storage = AcceptAllCookiesStorage()
    }
}

data class SSLeaderboardScoreData(val ranked: String?, val uid: Int, val scores: List<SSLeaderboardScore>, val playerScore: Int) {
    fun isValid() = ranked == null || uid > 0
    fun toLeaderboardData() = LeaderboardData(ranked != null && ranked.startsWith("Ranked"), uid,
        scores.map(SSLeaderboardScore::toLeaderboardScore).filter { it.playerId > 9000 },
        ranked != null && !ranked.contains("modifiers disabled"), isValid())
}
data class SSLeaderboardScore(val playerId: Long, val name: String, val rank: Int, val score: Int, val pp: Double, val mods: String, val replay: Boolean) {
    fun toLeaderboardScore() = LeaderboardScore(playerId, name.replace(tagsRegex, ""), rank, score, pp, mods.split(',').filter { it.isNotEmpty() })
}

@Location("/api/scores") class ScoresApi {
    @Location("/{key}/{page?}") data class Leaderboard(val key: String, val page: Int = 1, val difficulty: Int = -1, val gameMode: Int = -1, val api: ScoresApi)
}

fun Route.scoresRoute() {
    options<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", "https://beatmaps.io")
    }

    get<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(getScores(
            it.key,
            EDifficulty.fromInt(it.difficulty) ?: EDifficulty.ExpertPlus,
            SSGameMode.fromInt(it.gameMode) ?: SSGameMode.SoloStandard,
            it.page
        ))
    }
}

suspend fun getScores(levelId: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1, retry: Boolean = true): LeaderboardData =
    scoresClient.submitForm<String>("https://scoresaber.com/game/scores-n.php", Parameters.build {
        append("levelId", levelId)
        append("difficulty", diff.idx.toString())
        append("gameMode", mode.toString())
        append("page", page.toString())
    }, true)
        .run { jacksonObjectMapper().readValue<SSLeaderboardScoreData>(this) }
        .run {
            if (!isValid() && retry) {
                // Exchange unlocks the scores endpoint
                scoresClient.submitForm<String>("https://scoresaber.com/game/exchange.php", Parameters.build {
                    append("playerid", "0")
                })

                getScores(levelId, diff, mode, page, false)
            } else {
                toLeaderboardData()
            }
        }