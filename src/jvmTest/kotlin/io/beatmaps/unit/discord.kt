package io.beatmaps.unit

import io.beatmaps.api.ReviewDetail
import io.beatmaps.common.json
import io.beatmaps.login.DiscordHelper
import io.beatmaps.login.DiscordUserInfo
import io.beatmaps.util.DiscordWebhookHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DiscordTest : UnitTestBase() {
    @Test
    fun webhookTest() = runTest {
        val review = fixture<ReviewDetail>()

        val client = setupClient {
            respond(
                content = ByteReadChannel("""{}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val url = "https://discord.com/api/webhooks/1234567/fake-webhook"
        val webhookClient = DiscordWebhookHandler(client, url)

        webhookClient.post(review)
    }

    @Test
    fun discordInfoTest() = runTest {
        val discordInfo = fixture<DiscordUserInfo>()

        val client = setupClient {
            respond(
                content = ByteReadChannel(json.encodeToString(discordInfo)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val discordHelper = DiscordHelper(client)

        val info = discordHelper.getDiscordData("TOKEN")
        assertEquals(discordInfo, info)
    }

    @Test
    fun discordAvatarTest() = runTest {
        val client = setupClient {
            respond(
                // Could actually pass an image here?
                content = ByteReadChannel("""a"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val discordHelper = DiscordHelper(client)

        val bytes = discordHelper.getDiscordAvatar("/asd.jpg", 1234567890)
        assertNotEquals(0, bytes.size)
    }
}
