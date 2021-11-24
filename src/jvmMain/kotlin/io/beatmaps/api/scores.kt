package io.beatmaps.api

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.readValue
import io.beatmaps.common.Config
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.jackson
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.cookies.AcceptAllCookiesStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant

val tagsRegex = Regex("(<([^>]+)>)")
val scoresCookies = AcceptAllCookiesStorage()
val scoresClient = HttpClient(Apache) {
    install(HttpCookies) {
        storage = scoresCookies
    }
}

data class SSLeaderboardPlayer(
    val id: String,
    val name: String,
    val profilePicture: String,
    val country: String,
    val permissions: Int,
    val badges: String,
    val role: String?
)

data class SSLeaderboardInfo(
    val id: Int,
    val songHash: String,
    val songName: String,
    val songSubName: String,
    val songAuthorName: String,
    val levelAuthorName: String,
    val difficulty: SSLeaderboardInfoDiff,
    val maxScore: Int,
    val createdDate: Instant,
    val rankedDate: Instant?,
    val qualifiedDate: Instant?,
    val lovedDate: Instant?,
    val ranked: Boolean,
    val qualified: Boolean,
    val loved: Boolean,
    val maxPP: Int,
    val stars: Float,
    val plays: Int,
    val dailyPlays: Int,
    val positiveModifiers: Boolean,
    val playerScore: SSLeaderboardScore?,
    val difficulties: List<SSLeaderboardInfoDiff>
) {
    fun toLeaderboardData(scores: List<SSLeaderboardScore>) = LeaderboardData(
        ranked, id,
        scores.map(SSLeaderboardScore::toLeaderboardScore).filter { it.playerId > 9000 },
        positiveModifiers, true
    )
}

data class SSLeaderboardInfoDiff(val leaderboardId: Int, val difficulty: Int, val gameMode: String, val difficultyRaw: String)
data class SSLeaderboardScore(
    val id: Long,
    val leaderboardPlayerInfo: SSLeaderboardPlayer,
    val rank: Int,
    val baseScore: Int,
    val modifiedScore: Int,
    val pp: Double,
    val weight: Double,
    val modifiers: String,
    val multiplier: Float,
    val badCuts: Int,
    val missedNotes: Int,
    val maxCombo: Int,
    val fullCombo: Boolean,
    val hmd: Int,
    val timeSet: String,
    val hasReplay: Boolean
) {
    fun toLeaderboardScore() = LeaderboardScore(leaderboardPlayerInfo.id.toLong(), leaderboardPlayerInfo.name.replace(tagsRegex, ""), rank, modifiedScore, pp, modifiers.split(',').filter { it.isNotEmpty() })
}

@Location("/api/scores") class ScoresApi {
    @Location("/{key}/{page}") data class Leaderboard(val key: String, val page: Int = 1, val difficulty: Int = -1, val gameMode: Int = -1, val api: ScoresApi)
}

fun Route.scoresRoute() {
    options<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", Config.basename)
        call.respond(HttpStatusCode.OK)
    }

    get<ScoresApi.Leaderboard> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(
            getLeaderboard(
                it.key,
                EDifficulty.fromInt(it.difficulty) ?: EDifficulty.ExpertPlus,
                SSGameMode.fromInt(it.gameMode) ?: SSGameMode.SoloStandard,
                it.page
            )
        )
    }
}

suspend fun getLeaderboardInfo(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard) =
    try {
        scoresClient.get<String>(
            "https://scoresaber.com/api/leaderboard/by-hash/$hash/info"
        ) {
            parameter("difficulty", diff.idx)
            parameter("gameMode", mode)
        }.run {
            jackson.readValue<SSLeaderboardInfo>(this)
        }
    } catch (e: ClientRequestException) {
        null
    } catch (e: JacksonException) {
        e.printStackTrace()
        null
    }

suspend fun getScores(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1) =
    try {
        scoresClient.get<String>(
            "https://scoresaber.com/api/leaderboard/by-hash/$hash/scores"
        ) {
            parameter("difficulty", diff.idx)
            parameter("gameMode", mode)
            parameter("page", page)
        }.run {
            jackson.readValue<List<SSLeaderboardScore>>(this)
        }
    } catch (e: ClientRequestException) {
        null
    } catch (e: JacksonException) {
        e.printStackTrace()
        null
    }

suspend fun getLeaderboard(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1) =
    coroutineScope {
        val leaderboard = async {
            getLeaderboardInfo(hash, diff, mode)
        }
        val scores = async {
            getScores(hash, diff, mode, page)
        }

        val (al, ascores) = leaderboard.await() to scores.await()
        if (al != null && ascores != null) {
            al.toLeaderboardData(ascores)
        } else {
            LeaderboardData.EMPTY
        }
    }
