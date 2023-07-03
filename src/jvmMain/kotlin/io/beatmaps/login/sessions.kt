package io.beatmaps.login

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.ReplaceOptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.CookieConfiguration
import io.ktor.server.sessions.SessionProvider
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.SessionTrackerById
import io.ktor.server.sessions.SessionTransportCookie
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.sessions.generateSessionId
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.KMongo
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import java.util.logging.Logger
import kotlin.reflect.typeOf
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val redisHost = System.getenv("REDIS_HOST") ?: ""
val redisPort = System.getenv("REDIS_PORT") ?: "6379"

val mongoHost = System.getenv("MONGO_HOST") ?: ""
val mongoPort = System.getenv("MONGO_PORT") ?: "27017"
val mongoDb = System.getenv("MONGO_DB") ?: "beatmaps"
val mongoAuthDb = System.getenv("MONGO_AUTH_DB") ?: mongoDb
val mongoUser = System.getenv("MONGO_USER") ?: "beatmaps"
val mongoPass = System.getenv("MONGO_PASSWORD") ?: "insecure-password"

private val logger = Logger.getLogger("bmio.Redis")

val cookieName = System.getenv("COOKIE_NAME") ?: "BMSESSIONID"
val cookieDomain = System.getenv("COOKIE_DOMAIN") ?: null

@Serializable
data class MongoSession(val _id: String, val session: Session, @Contextual val expireAt: Instant)

fun Application.installSessions() {
    val sessionStorage = try {
        if (mongoHost.isEmpty()) throw Exception("Mongo not configured")

        val mongoClient = KMongo.createClient(
            "mongodb://$mongoUser:$mongoPass@$mongoHost:$mongoPort/$mongoAuthDb?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000"
        )
        val database = mongoClient.getDatabase(mongoDb)
        val sessions = database.getCollection<MongoSession>("sessions")

        val currentCount = sessions.countDocuments(EMPTY_BSON)
        logger.info("Using mongodb session storage ($currentCount)")

        MongoSessionStorage(sessions)
    } catch (e: Exception) {
        logger.warning("Using in memory session storage")
        SessionStorageMemory()
    }

    if (redisHost.isNotEmpty() && sessionStorage is MongoSessionStorage) {
        val redisClient: RedisClient = RedisClient.create("redis://$redisHost:$redisPort")
        val connection = redisClient.connect().coroutines()

        launch {
            val oldSerial = defaultSessionSerializer<Session>(typeOf<Session>())
            connection.keys("*").map {
                Triple(it, connection.get(it), connection.ttl(it))
            }.filter {
                it.second != null && it.third != null
            }.map {
                Triple(it.first, oldSerial.deserialize(it.second!!), it.third!!)
            }.map {
                sessionStorage.writeLocal(it.first, it.second, it.third)
                connection.del(it.first)
            }.collect()
        }
    }

    install(Sessions) {
        val sessionType = Session::class
        val cookieConfig = CookieConfiguration().apply {
            extensions["SameSite"] = "lax"
            domain = cookieDomain
        }

        val transport = SessionTransportCookie(cookieName, cookieConfig, listOf())
        val tracker = when (sessionStorage) {
            is MongoSessionStorage -> TypedSessionTracker(sessionType, sessionStorage) { generateSessionId() }
            is SessionStorageMemory -> SessionTrackerById(sessionType, defaultSessionSerializer(), sessionStorage) { generateSessionId() }
            else -> throw Exception("Impossible session storage type")
        }
        val provider = SessionProvider(cookieName, sessionType, transport, tracker)
        register(provider)
    }
}

class MongoSessionStorage(private val collection: MongoCollection<MongoSession>) : TypedSessionStorage<Session> {
    override suspend fun read(id: String) = collection.findOne(MongoSession::_id eq id)?.session ?: throw NoSuchElementException()

    override suspend fun write(id: String, value: Session) = writeLocal(id, value)

    fun writeLocal(id: String, value: Session, ttl: Long = 7 * 24 * 3600L) {
        collection.replaceOne(
            MongoSession::_id eq id,
            MongoSession(id, value, Clock.System.now().plus(ttl.toDuration(DurationUnit.SECONDS))),
            ReplaceOptions().upsert(true)
        )
    }

    override suspend fun invalidate(id: String) {
        collection.deleteOne(
            MongoSession::_id eq id
        )
    }
}
