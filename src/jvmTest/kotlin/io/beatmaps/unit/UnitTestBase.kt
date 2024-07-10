package io.beatmaps.unit

import com.appmattus.kotlinfixture.decorator.nullability.NeverNullStrategy
import com.appmattus.kotlinfixture.decorator.nullability.nullabilityStrategy
import com.appmattus.kotlinfixture.kotlinFixture
import io.beatmaps.api.scores.SSLeaderboardPlayer
import io.beatmaps.common.jsonIgnoreUnknown
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.serialization.kotlinx.json.json

abstract class UnitTestBase {
    protected fun setupClient(block: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        val mockEngine = MockEngine(block)

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(jsonIgnoreUnknown)
            }
            install(HttpTimeout)
        }
    }

    companion object {
        val fixture = kotlinFixture {
            nullabilityStrategy(NeverNullStrategy)
            property(SSLeaderboardPlayer::id) { (random.nextInt() and Int.MAX_VALUE).toString() }
        }
    }
}
