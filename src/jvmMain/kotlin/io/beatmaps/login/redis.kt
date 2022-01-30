@file:Suppress("EXPERIMENTAL_API_USAGE")

package io.beatmaps.login

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.sessions.SessionStorage
import io.ktor.sessions.SessionStorageMemory
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.reader
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import kotlin.coroutines.coroutineContext

val redisHost = System.getenv("REDIS_HOST") ?: ""
val redisPort = System.getenv("REDIS_PORT") ?: "6379"
private val logger = Logger.getLogger("bmio.Redis")

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
        cookie<Session>("BMSESSIONID", sessionStorage) {
            cookie.extensions["SameSite"] = "lax"
            cookie.domain = cookieDomain
        }
    }
}

class RedisSessionStorage(private val redisClient: RedisCoroutinesCommands<String, String>) : SimplifiedSessionStorage() {
    override suspend fun read(id: String): ByteArray? {
        val result = redisClient.get(id)
        return result?.toByteArray()
    }

    override suspend fun write(id: String, data: ByteArray?) {
        if (data == null) {
            redisClient.del(id)
        } else {
            redisClient.set(id, String(data), SetArgs.Builder.ex(7 * 24 * 3600L))
        }
    }
}

abstract class SimplifiedSessionStorage : SessionStorage {
    abstract suspend fun read(id: String): ByteArray?
    abstract suspend fun write(id: String, data: ByteArray?): Unit

    override suspend fun invalidate(id: String) {
        write(id, null)
    }

    override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
        val data = read(id) ?: throw NoSuchElementException("Session $id not found")
        return consumer(ByteReadChannel(data))
    }

    override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
        return provider(
            CoroutineScope(Dispatchers.IO).reader(coroutineContext, autoFlush = true) {
                write(id, channel.readAvailable())
            }.channel
        )
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
