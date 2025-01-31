package io.beatmaps.unit

import io.beatmaps.api.scores.BLPaged
import io.beatmaps.api.scores.BeatLeaderScores
import io.beatmaps.api.scores.SSLeaderboardInfo
import io.beatmaps.api.scores.SSPaged
import io.beatmaps.api.scores.ScoreSaberScores
import io.beatmaps.common.beatsaber.leaderboard.SSGameMode
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.json
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.assertEquals

class LeaderboardTest : UnitTestBase() {
    @Test
    fun scoresaberTest() = runTest {
        val info = fixture<SSLeaderboardInfo>()
        val page = fixture<SSPaged>()

        val client = setupClient { req ->
            val response = when {
                req.url.encodedPath.endsWith("info") -> json.encodeToString(info)
                else -> json.encodeToString(page)
            }
            respond(
                content = ByteReadChannel(response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val scores = ScoreSaberScores(client)
        val data = scores.getLeaderboard("asd", EDifficulty.ExpertPlus, SSGameMode.SoloStandard, 1)

        assertEquals(page.scores.map { it.leaderboardPlayerInfo.name }, data.scores.map { it.name })
    }

    @Test
    fun beatleaderTest() = runTest {
        val page = fixture<BLPaged>()

        val client = setupClient { req ->
            respond(
                content = ByteReadChannel(json.encodeToString(page)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val scores = BeatLeaderScores(client)
        val data = scores.getLeaderboard("asd", EDifficulty.ExpertPlus, SSGameMode.SoloStandard, 1)

        assertEquals(page.data.map { it.player }, data.scores.map { it.name })
    }
}
