package io.beatmaps.unit

import io.beatmaps.cloudflare.KeyResponse
import io.beatmaps.cloudflare.KeyValue
import io.beatmaps.cloudflare.Worker
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

class CloudflareTest : UnitTestBase() {
    @Test
    fun getKeysTest() = runTest {
        val keyResponse = fixture<KeyResponse>()

        val client = setupClient {
            respond(
                content = ByteReadChannel(json.encodeToString(keyResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val worker = Worker(client, "a123", "fake-token")
        val store = worker.getKVStore("beatsaver")

        val result = store.getKeys()
        assertEquals(keyResponse.result.map { it.name }, result)
    }

    @Test
    fun setValueTest() = runTest {
        val client = setupClient {
            respond(
                content = ByteReadChannel(""""""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val worker = Worker(client, "a123", "fake-token")
        val store = worker.getKVStore("beatsaver")

        store.setValue("a", "b")
    }

    @Test
    fun setValuesTest() = runTest {
        val values = fixture<List<KeyValue>>()

        val client = setupClient {
            respond(
                content = ByteReadChannel(""""""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val worker = Worker(client, "a123", "fake-token")
        val store = worker.getKVStore("beatsaver")

        store.setValues(values)
    }
}
