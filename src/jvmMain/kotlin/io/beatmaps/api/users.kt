package io.beatmaps.api

import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.beatsaber.BSDifficulty
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.beatsaver.IUserVerifyProvider
import io.beatmaps.common.beatsaver.UserNotVerified
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.*
import io.beatmaps.common.zip.IMapScorerProvider
import io.ktor.application.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null) =
    UserDetail(other.id.value, other.name, other.hash, if (roles) other.testplay else null, other.avatar ?: "https://www.gravatar.com/avatar/${other.hash}?d=retro", stats)

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false) = from(UserDao.wrapRow(row), roles)

@Location("/api/users")
class UsersApi {
    @Location("/me")
    data class Me(val api: UsersApi)

    @Location("/id/{id}/stats")
    data class UserStats(val id: Int, val api: UsersApi)

    @Location("/find/{id}")
    data class Find(val id: String, val api: UsersApi)

    @Location("/beatsaver/{hash?}")
    data class LinkBeatsaver(val hash: String? = null, val api: UsersApi)
}

fun Route.userRoute() {
    val userVerifyService = ServiceLoader.load(IUserVerifyProvider::class.java).findFirst().orElse(object : IUserVerifyProvider {
        override fun create() = UserNotVerified
    }).create()

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

            val valid = userVerifyService.validateUser(toCheck, userHash)
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
            val statTmp =
                Beatmap.slice(Beatmap.id.count(), Beatmap.upVotesInt.sum(), Beatmap.downVotesInt.sum(), Beatmap.bpm.avg(), Beatmap.score.avg(3), Beatmap.duration.avg(0)).select {
                    (Beatmap.uploader eq it.id) and (Beatmap.deletedAt.isNull())
                }.first().let {
                    UserStats(
                        it[Beatmap.upVotesInt.sum()] ?: 0,
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f
                    )
                }

            val cases = EDifficulty.values().associate { it to diffCase(it) }
            val diffStats = Difficulty
                .join(Beatmap, JoinType.INNER, Beatmap.id, Difficulty.mapId)
                .slice(Difficulty.id.count(), *cases.values.toTypedArray())
                .select {
                    (Beatmap.uploader eq it.id) and (Beatmap.deletedAt.isNull())
                }.first().let {
                    fun safeGetCount(diff: EDifficulty) = cases[diff]?.let { c -> it.getOrNull(c) } ?: 0
                    UserDiffStats(
                        it[Difficulty.id.count()].toInt(),
                        safeGetCount(EDifficulty.Easy),
                        safeGetCount(EDifficulty.Normal),
                        safeGetCount(EDifficulty.Hard),
                        safeGetCount(EDifficulty.Expert),
                        safeGetCount(EDifficulty.ExpertPlus)
                    )
                }

            statTmp.copy(diffStats = diffStats)
        }

        call.respond(UserDetail.from(user, stats = stats))
    }
}

fun diffCase(diff: EDifficulty) = Sum(Expression.build { case().When(Difficulty.difficulty eq diff, intLiteral(1)).Else(intLiteral(0)) }, IntegerColumnType())

fun getBSLinkHash(userId: Int) = MessageDigest.getInstance("MD5").let {
    it.update(ubyteArrayOf(0x1fu, 0x22u, 0x1fu, 0x5fu, 0x84u, 0xb0u).toByteArray() + userId.toString().toByteArray())
    String.format("%08x", BigInteger(1, it.digest().take(8).toByteArray()))
}