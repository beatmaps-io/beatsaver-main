@file:Suppress("EXPERIMENTAL_API_USAGE")

package io.beatmaps.login

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.SessionStorage
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.utils.io.ByteReadChannel
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import java.io.ByteArrayOutputStream
import java.util.logging.Logger

val redisHost = System.getenv("REDIS_HOST") ?: ""
val redisPort = System.getenv("REDIS_PORT") ?: "6379"
private val logger = Logger.getLogger("bmio.Redis")

val cookieName = System.getenv("COOKIE_NAME") ?: "BMSESSIONID"
val cookieDomain = System.getenv("COOKIE_DOMAIN") ?: null

fun Application.installSessions() {
    val sessionStorage = if (redisHost.isNotEmpty()) {
        val redisClient: RedisClient = RedisClient.create("redis://$redisHost:$redisPort")
        val connection = redisClient.connect().coroutines()
        RedisSessionStorage(connection)
    } else {
        logger.warning("Using in memory session storage")
        SessionStorageMemory()
    }

    install(Sessions) {
        cookie<Session>(cookieName, sessionStorage) {
            cookie.extensions["SameSite"] = "lax"
            cookie.domain = cookieDomain
        }
    }
}

class RedisSessionStorage(private val redisClient: RedisCoroutinesCommands<String, String>) : SessionStorage {
    override suspend fun read(id: String) = redisClient.get(id) ?: throw NoSuchElementException()

    override suspend fun write(id: String, value: String) {
        redisClient.set(id, value, SetArgs.Builder.ex(7 * 24 * 3600L))
    }

    override suspend fun invalidate(id: String) {
        redisClient.del(id)
    }
}

suspend fun ByteReadChannel.readAvailable(): ByteArray {
    val data = ByteArrayOutputStream()
    val temp = ByteArray(1024)
    while (!isClosedForRead) {
        val read = readAvailable(temp, 0, 1024)
        if (read <= 0) break
        data.write(temp, 0, read)
    }
    return data.toByteArray()
}
