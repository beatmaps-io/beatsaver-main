package io.beatmaps.login

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.sessions.SessionTracker
import io.ktor.util.AttributeKey
import kotlin.reflect.KClass

interface TypedSessionStorage<S> {
    suspend fun write(id: String, value: S)

    suspend fun invalidate(id: String)

    suspend fun read(id: String): S
}

class TypedSessionTracker<S : Any>(
    private val type: KClass<S>,
    private val storage: TypedSessionStorage<S>,
    private val sessionIdProvider: () -> String
) : SessionTracker<S> {
    private val sessionIdKey: AttributeKey<String> = AttributeKey("SessionId")

    override suspend fun load(call: ApplicationCall, transport: String?): S? {
        val sessionId = transport ?: return null

        call.attributes.put(sessionIdKey, sessionId)
        try {
            return storage.read(sessionId)
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug(
                "Failed to lookup session: ${notFound.message ?: notFound.toString()}. " + "The session id is wrong or outdated."
            )
        }

        // Remove the wrong session identifier if no related session was found
        call.attributes.remove(sessionIdKey)

        return null
    }

    override suspend fun store(call: ApplicationCall, value: S): String {
        val sessionId = call.attributes.computeIfAbsent(sessionIdKey, sessionIdProvider)
        storage.write(sessionId, value)
        return sessionId
    }

    override suspend fun clear(call: ApplicationCall) {
        val sessionId = call.attributes.takeOrNull(sessionIdKey)
        if (sessionId != null) {
            storage.invalidate(sessionId)
        }
    }

    override fun validate(value: S) {
        if (!type.isInstance(value)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }

    override fun toString(): String {
        return "io.beatmaps.login.TypedSessionTracker: $storage"
    }
}
