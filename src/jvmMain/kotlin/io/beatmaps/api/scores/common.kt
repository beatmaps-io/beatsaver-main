package io.beatmaps.api.scores

import io.beatmaps.api.LeaderboardData
import io.beatmaps.common.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.jackson
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.jackson.JacksonConverter
import java.net.URISyntaxException

val scoresCookies = AcceptAllCookiesStorage()
val scoresClient = HttpClient(Apache) {
    expectSuccess = true

    install(HttpCookies) {
        storage = scoresCookies
    }
    install(HttpTimeout) {
        socketTimeoutMillis = 5000
        requestTimeoutMillis = 20000
    }
    install(ContentNegotiation) {
        val converter = JacksonConverter(jackson)
        register(ContentType.Application.Json, converter)
    }
}

class ScoreSaberServerException(val originalException: ServerResponseException) : RuntimeException()

suspend fun <T> ssTry(block: suspend () -> T) =
    try {
        block()
    } catch (e: ClientRequestException) {
        null // 4xx response
    } catch (e: URISyntaxException) {
        null // Bad characters in hash, likely only locally
    } catch (e: ServerResponseException) {
        throw ScoreSaberServerException(e) // 5xx response
    } catch (e: JsonConvertException) {
        e.printStackTrace()
        null // Bad json, scoresaber schema changed?
    }

interface RemoteScores {
    suspend fun getLeaderboard(hash: String, diff: EDifficulty = EDifficulty.ExpertPlus, mode: SSGameMode = SSGameMode.SoloStandard, page: Int = 1): LeaderboardData
}
