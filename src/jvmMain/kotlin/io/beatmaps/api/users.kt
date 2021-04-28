package io.beatmaps.api

import com.fasterxml.jackson.module.kotlin.readValue
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.beatsaver.BeatsaverList
import io.beatmaps.common.client
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.jackson
import io.ktor.application.call
import io.ktor.client.features.timeout
import io.ktor.client.request.get
import io.ktor.http.userAgent
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Instant

fun getGravatar(email: String) = MessageDigest.getInstance("MD5").let {
    it.update(email.toByteArray())
    String.format("https://www.gravatar.com/avatar/%32x", BigInteger(1, it.digest()))
}

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null) = UserDetail(other.id.value, other.name, other.hash, if (roles) other.testplay else null, other.avatar ?: "https://www.gravatar.com/avatar/${other.hash}?d=retro", stats)
fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false) = from(UserDao.wrapRow(row), roles)

@Location("/api/users") class UsersApi {
    @Location("/me") data class Me(val api: UsersApi)
    @Location("/id/{id}/stats") data class UserStats(val id: Int, val api: UsersApi)
    @Location("/find/{id}") data class Find(val id: String, val api: UsersApi)
    @Location("/beatsaver/{hash?}") data class LinkBeatsaver(val hash: String? = null, val api: UsersApi)
}

fun Route.userRoute() {
    get<UsersApi.Me> {
        requireAuthorization {
            val user = transaction {
                UserDao.wrapRows(User.select {
                    User.id.eq(it.userId)
                }).first()
            }

            call.respond(UserDetail.from(user))
        }
    }

    get<UsersApi.LinkBeatsaver> {
        requireAuthorization {
            call.respond(BeatsaverLink(getBSLinkHash(it.userId), it.hash != null))
        }
    }

    post<UsersApi.LinkBeatsaver> { r ->
        requireAuthorization { s ->
            val userHash = getBSLinkHash(s.userId)

            val toCheck = transaction {
                r.hash?.let { rHash ->
                    if (rHash.length != 24 || !rHash.startsWith("5")) {
                        // Is a username?
                        UserDao.wrapRows(User.select {
                            User.name eq rHash and User.hash.isNotNull()
                        }).toList().mapNotNull { it.hash }
                    } else {
                        null
                    }
                } ?: listOfNotNull(r.hash)
            }

            val valid = toCheck.firstOrNull { bsHash ->
                val time = Instant.now().epochSecond
                val json = client.get<String>("http://beatsaver.com/api/maps/uploader/$bsHash/0?automapper=1&$time") {
                    userAgent("BeatSaverDownloader/5.4.0.0 BeatSaverSharp/1.6.0.0 BeatSaber /1.13.2-oculus")
                    timeout {
                        socketTimeoutMillis = 30000
                        requestTimeoutMillis = 60000
                    }
                }
                val obj = jackson.readValue<BeatsaverList>(json)

                obj.docs.any { map ->
                    map.name.contains(userHash) || map.description.contains(userHash)
                }
            }

            val result = transaction {
                val isUnlinked = UserDao.wrapRows(User.select {
                    User.id eq s.userId
                }).toList().firstOrNull()?.hash

                if (isUnlinked != null) {
                    call.sessions.set(s.copy(hash = isUnlinked))
                    return@transaction true
                }

                if (valid != null) {
                    User.updateReturning({ User.hash eq valid and User.email.isNull() }, { u ->
                        u[hash] = null
                    }, User.id)?.let { r ->
                        if (r.isEmpty()) return@let

                        // If we returned a row
                        val oldId = r.first()[User.id]

                        Beatmap.update({ Beatmap.uploader eq oldId }) {
                            it[uploader] = s.userId
                        }
                    }

                    User.update({ User.id eq s.userId }) {
                        it[hash] = valid
                    }
                    call.sessions.set(s.copy(hash = valid))

                    true
                } else {
                    false
                }
            }
            call.respond(BeatsaverLink(userHash, result))
        }
    }

    get<UsersApi.Find> {
        val user = transaction {
            UserDao.wrapRows(User.select {
                User.hash.eq(it.id)
            }).first()
        }

        call.respond(UserDetail.from(user))
    }

    options<MapsApi.UserId> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }
    get<MapsApi.UserId>("Get user info".responds(ok<UserDetail>())) {
        call.response.header("Access-Control-Allow-Origin", "*")
        val user = transaction {
            UserDao.wrapRows(User.select {
                User.id.eq(it.id)
            }).first()
        }

        val stats = transaction {
            Beatmap.slice(Beatmap.id.count(), Beatmap.upVotesInt.sum(), Beatmap.downVotesInt.sum(), Beatmap.bpm.avg()).select {
                (Beatmap.uploader eq it.id) and (Beatmap.deletedAt.isNull())
            }.first().let {
                UserStats(it[Beatmap.upVotesInt.sum()] ?: 0, it[Beatmap.downVotesInt.sum()] ?: 0, it[Beatmap.id.count()].toInt(), it[Beatmap.bpm.avg()]?.toFloat() ?: 0f)
            }
        }

        call.respond(UserDetail.from(user, stats = stats))
    }
}

fun getBSLinkHash(userId: Int) = MessageDigest.getInstance("MD5").let {
    it.update(ubyteArrayOf(0x1fu, 0x22u, 0x1fu, 0x5fu, 0x84u, 0xb0u).toByteArray() + userId.toString().toByteArray())
    String.format("%08x", BigInteger(1, it.digest().take(8).toByteArray()))
}