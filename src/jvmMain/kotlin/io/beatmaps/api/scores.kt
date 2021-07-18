package io.beatmaps.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.common.Config
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.jackson
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.datetime.Instant

val tagsRegex = Regex("(<([^>]+)>)")
val scoresCookies = AcceptAllCookiesStorage()
val scoresClient = HttpClient(Apache) {
    install(HttpCookies) {
        storage = scoresCookies
    }
}

data class SSLeaderboardScoreData(val ranked: String?, val uid: Int, val scores: List<SSLeaderboardScore>, val playerScore: Int) {
    fun isValid() = ranked == null || uid > 0
    fun toLeaderboardData() = LeaderboardData(ranked != null && ranked.startsWith("Ranked"), uid,
        scores.map(SSLeaderboardScore::toLeaderboardScore).filter { it.playerId > 9000 },
        ranked != null && !ranked.contains("modifiers disabled"), isValid())
}
data class SSLeaderboardScore(val playerId: Long, val name: String, val rank: Int, val score: Int, val pp: Double, val weight: Double, val mods: String, val badCuts: Int,
                              val missedNotes: Int, val maxCombo: Int, val fullCombo: Int, val hmd: Int, val timeset: String, val country: String, val replay: Boolean) {
    fun toLeaderboardScore() = LeaderboardScore(playerId, name.replace(tagsRegex, ""), rank, score, pp, mods.split(',').filter { it.isNotEmpty() })
}

@Location("/api/scores") class ScoresApi {
    @Location("/{key}/{page}") data class Leaderboard(val key: String, val page: Int = 1, val difficulty: Int = -1, val gameMode: Int = -1, val api: ScoresApi)
}

fun Route.scoresRoute() {
    options<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", Config.basename)
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

val ssPHPSESSID = System.getenv("SSPHPSESSID") ?: ""
suspend fun getScores(levelId: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1, retry: Boolean = true): LeaderboardData =
    scoresClient.submitForm<String>("https://scoresaber.com/game/scores-pc.php", Parameters.build {
        append("levelId", levelId)
        append("difficulty", diff.idx.toString())
        append("gameMode", mode.toString())
        append("page", page.toString())
    }, true)
        .run { jackson.readValue<SSLeaderboardScoreData>(this) }
        .run {
            if (!isValid() && retry) {
                // Exchange unlocks the scores endpoint
                /*val res = scoresClient.submitForm<String>("https://scoresaber.com/game/exchange.php", Parameters.build {
                    append("playerid", "76561198006824937")
                })
                println(res)*/
                scoresCookies.addCookie(Url("https://scoresaber.com"), Cookie("PHPSESSID", ssPHPSESSID))

                getScores(levelId, diff, mode, page, false)
            } else {
                toLeaderboardData()
            }
        }