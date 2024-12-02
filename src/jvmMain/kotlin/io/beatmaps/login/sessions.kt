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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.KMongo
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import java.util.logging.Logger
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
data class MongoSession(@SerialName("_id") val id: String, val session: Session, @Contextual val expireAt: Instant)

object MongoClient {
    private val mongoClient = if (mongoHost.isEmpty()) { null } else {
        KMongo.createClient(
            "mongodb://$mongoUser:$mongoPass@$mongoHost:$mongoPort/$mongoAuthDb?serverSelectionTimeoutMS=2000&connectTimeoutMS=2000"
        )
    }

    private val database = mongoClient?.getDatabase(mongoDb)
    lateinit var sessions: MongoCollection<MongoSession>

    var connected = false

    fun deleteSessionsFor(userId: Int) =
        connected && sessions.deleteMany(MongoSession::session / Session::userId eq userId).wasAcknowledged()

    fun testConnection() =
        try {
            if (database == null) throw Exception("Mongo not configured")

            sessions = database.getCollection<MongoSession>("sessions")
            sessions.countDocuments(EMPTY_BSON)
            connected = true

            true
        } catch (e: Exception) {
            false
        }
}

fun Application.installSessions() {
    install(Sessions) {
        val sessionType = Session::class
        val cookieConfig = CookieConfiguration().apply {
            extensions["SameSite"] = "lax"
            domain = cookieDomain
        }

        val transport = SessionTransportCookie(cookieName, cookieConfig, listOf())
        val tracker = if (MongoClient.testConnection()) {
            logger.info("Using mongodb session storage")
            val sessionStorage = MongoSessionStorage(MongoClient.sessions)
            TypedSessionTracker(sessionType, sessionStorage) { generateSessionId() }
        } else {
            logger.warning("Using in memory session storage")
            val sessionStorage = SessionStorageMemory()
            SessionTrackerById(sessionType, defaultSessionSerializer(), sessionStorage) { generateSessionId() }
        }

        val provider = SessionProvider(cookieName, sessionType, transport, tracker)
        register(provider)
    }
}

class MongoSessionStorage(private val collection: MongoCollection<MongoSession>) : TypedSessionStorage<Session> {
    override suspend fun read(id: String) = collection.findOne(MongoSession::id eq id)?.session ?: throw NoSuchElementException()

    override suspend fun write(id: String, value: Session) = writeLocal(id, value)

    private fun writeLocal(id: String, value: Session, ttl: Long = 7 * 24 * 3600L) {
        collection.replaceOne(
            MongoSession::id eq id,
            MongoSession(id, value, Clock.System.now().plus(ttl.toDuration(DurationUnit.SECONDS))),
            ReplaceOptions().upsert(true)
        )
    }

    override suspend fun invalidate(id: String) {
        collection.deleteOne(
            MongoSession::id eq id
        )
    }
}
