package io.beatmaps.cloudflare

import io.beatmaps.common.client
import io.ktor.client.features.HttpRequestTimeoutException
import io.ktor.client.features.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface IKVStore {
    suspend fun getKeys(): List<String>
    suspend fun setValue(key: String, value: String)
    suspend fun setValues(kvs: List<KeyValue>)
}

private data class KeyResponse(val success: Boolean, val result: List<Key>)
private data class Key(val name: String)
data class KeyValue(val key: String, val value: String)

private fun requestCommon(builder: HttpRequestBuilder, authToken: String) {
    builder.header("Authorization", "Bearer $authToken")
    builder.timeout {
        socketTimeoutMillis = 5000
        requestTimeoutMillis = 20000
    }
}

private class KVStore(val worker: Worker, val namespaceId: String) : IKVStore {
    @Throws(HttpRequestTimeoutException::class)
    override suspend fun getKeys() = client.get<KeyResponse>("https://api.cloudflare.com/client/v4/accounts/${worker.accountId}/storage/kv/namespaces/$namespaceId/keys") {
        requestCommon(this, worker.authToken)
    }.result.map { it.name }

    @Throws(HttpRequestTimeoutException::class)
    override suspend fun setValue(key: String, value: String) {
        client.put<String>("https://api.cloudflare.com/client/v4/accounts/${worker.accountId}/storage/kv/namespaces/$namespaceId/values/$key") {
            requestCommon(this, worker.authToken)
            body = value
        }
    }

    @Throws(HttpRequestTimeoutException::class)
    override suspend fun setValues(kvs: List<KeyValue>) {
        client.put<String>("https://api.cloudflare.com/client/v4/accounts/${worker.accountId}/storage/kv/namespaces/$namespaceId/bulk") {
            requestCommon(this, worker.authToken)
            contentType(ContentType.Application.Json)
            body = kvs
        }
    }
}

class Worker(val accountId: String, val authToken: String) {
    fun getKVStore(namespaceId: String): IKVStore = KVStore(this, namespaceId)
}
