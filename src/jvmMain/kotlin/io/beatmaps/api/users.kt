package io.beatmaps.api

import com.toxicbakery.bcrypt.Bcrypt
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.common.Config
import io.beatmaps.common.ModLogOpType
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaver.IUserVerifyProvider
import io.beatmaps.common.beatsaver.UserNotVerified
import io.beatmaps.common.client
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ModLogDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.ktor.application.call
import io.ktor.client.features.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.OrOp
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Sum
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.booleanLiteral
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.ServiceLoader

fun UserDetail.Companion.from(other: UserDao, roles: Boolean = false, stats: UserStats? = null) =
    UserDetail(other.id.value, other.name, other.hash, if (roles) other.testplay else null, other.avatar ?: "https://www.gravatar.com/avatar/${other.hash}?d=retro", stats)

fun UserDetail.Companion.from(row: ResultRow, roles: Boolean = false) = from(UserDao.wrapRow(row), roles)
fun Alert.Companion.from(other: ModLogDao, map: MapDetail) = Alert(map, other.opAt.toKotlinInstant(), other.realAction())
fun Alert.Companion.from(row: ResultRow) = from(ModLogDao.wrapRow(row), MapDetail.from(row))

@Location("/api/users")
class UsersApi {
    @Location("/me")
    data class Me(val api: UsersApi)

    @Location("/id/{id}/stats")
    data class UserStats(val id: Int, val api: UsersApi)

    @Location("/id/{id}/playlist")
    data class UserPlaylist(val id: Int, val api: UsersApi)

    @Location("/find/{id}")
    data class Find(val id: String, val api: UsersApi)

    @Location("/beatsaver")
    data class LinkBeatsaver(val api: UsersApi)

    @Location("/alerts")
    data class Alerts(val api: UsersApi)

    @Location("/list/{page}")
    data class List(val page: Long = 0, val api: UsersApi)
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
            call.respond(BeatsaverLink(it.hash != null))
        }
    }

    post<UsersApi.LinkBeatsaver> { _ ->
        requireAuthorization { s ->
            val r = call.receive<BeatsaverLinkReq>()

            val userToCheck = transaction {
                UserDao.wrapRows(User.select {
                    User.name eq r.user and User.hash.isNotNull()
                }).firstOrNull()
            }

            val valid = userToCheck != null && Bcrypt.verify(r.password, userToCheck.password.toByteArray())
            val result = transaction {
                val isUnlinked = UserDao.wrapRows(User.select {
                    User.id eq s.userId
                }).toList().firstOrNull()?.hash

                if (isUnlinked != null) {
                    call.sessions.set(s.copy(hash = isUnlinked))
                    return@transaction true
                }

                if (valid && userToCheck != null) {
                    User.updateReturning({ User.hash eq userToCheck.hash and User.email.isNull() }, { u ->
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
                        it[hash] = userToCheck.hash
                        it[admin] = OrOp(listOf(admin, booleanLiteral(userToCheck.admin)))
                    }
                    call.sessions.set(s.copy(hash = userToCheck.hash))

                    true
                } else {
                    false
                }
            }
            call.respond(BeatsaverLink(result))
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

    get<UsersApi.Alerts> {
        requireAuthorization { user ->
            val alerts = transaction {
                ModLog.join(Beatmap, JoinType.INNER, Beatmap.id, ModLog.opOn).select {
                    (Beatmap.uploader eq user.userId) and
                    (ModLog.type inList listOf(ModLogOpType.Unpublish, ModLogOpType.Delete).map { it.ordinal })
                }.orderBy(ModLog.opAt, SortOrder.DESC).limit(30).map { Alert.from(it) }
            }

            call.respond(alerts)
        }
    }

    get<UsersApi.List> {
        val us = transaction {
            val userAlias = User.slice(User.upvotes, User.id, User.name, User.avatar, User.hash).select {
                User.hash.isNotNull()
            }.orderBy(User.upvotes, SortOrder.DESC).limit(it.page).alias("u")

            val query = userAlias
                .join(Beatmap, JoinType.INNER, userAlias[User.id], Beatmap.uploader) {
                    Beatmap.deletedAt.isNull()
                }
                .slice(
                    Beatmap.uploader,
                    Beatmap.id.count(),
                    userAlias[User.upvotes],
                    userAlias[User.name],
                    userAlias[User.avatar],
                    userAlias[User.hash],
                    Beatmap.downVotesInt.sum(),
                    Beatmap.bpm.avg(),
                    Beatmap.score.avg(3),
                    Beatmap.duration.avg(0),
                    countWithFilter(Beatmap.ranked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                )
                .selectAll()
                .groupBy(Beatmap.uploader, userAlias[User.upvotes], userAlias[User.name], userAlias[User.avatar], userAlias[User.hash])
                .orderBy(userAlias[User.upvotes], SortOrder.DESC)

            query.toList().map {
                UserDetail(
                    it[Beatmap.uploader].value,
                    it[userAlias[User.name]],
                    avatar = it[userAlias[User.avatar]] ?: "https://www.gravatar.com/avatar/${it[userAlias[User.hash]]}?d=retro",
                    stats = UserStats(
                        it[userAlias[User.upvotes]],
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
                    )
                )
            }
        }

        call.respond(us)
    }

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    get<UsersApi.UserPlaylist> {
        val (maps, user) = transaction {
            Beatmap.joinVersions().select {
                Beatmap.uploader eq it.id and Beatmap.deletedAt.isNull()
            }.complexToBeatmap().sortedByDescending { b -> b.uploaded } to UserDetail.from(User.select { User.id eq it.id }.first())
        }

        val playlistSongs = maps.mapNotNull { map ->
            map.versions.values.firstOrNull { v -> v.state == EMapState.Published }?.let { v ->
                PlaylistSong(
                    v.key64,
                    v.hash,
                    map.name
                )
            }
        }

        val imageStr = Base64.getEncoder().encodeToString(
            client.get<ByteArray>(user.avatar) {
                timeout {
                    socketTimeoutMillis = 30000
                    requestTimeoutMillis = 60000
                }
            }
        )

        val dateStr = formatter.format(LocalDateTime.now())

        call.response.headers.append(HttpHeaders.ContentDisposition, "filename=\"${user.name}-$dateStr.bplist\"")
        call.respond(
            Playlist(
                "Maps by ${user.name} (${playlistSongs.size} Total)",
                user.name,
                "All maps by ${user.name} ($dateStr)",
                imageStr,
                PlaylistCustomData("${Config.apiremotebase}/users/id/${it.id}/playlist"),
                playlistSongs
            )
        )
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
                Beatmap.slice(
                    Beatmap.id.count(),
                    Beatmap.upVotesInt.sum(),
                    Beatmap.downVotesInt.sum(),
                    Beatmap.bpm.avg(),
                    Beatmap.score.avg(3),
                    Beatmap.duration.avg(0),
                    countWithFilter(Beatmap.ranked),
                    Beatmap.uploaded.min(),
                    Beatmap.uploaded.max()
                ).select {
                    (Beatmap.uploader eq it.id) and (Beatmap.deletedAt.isNull())
                }.first().let {
                    UserStats(
                        it[Beatmap.upVotesInt.sum()] ?: 0,
                        it[Beatmap.downVotesInt.sum()] ?: 0,
                        it[Beatmap.id.count()].toInt(),
                        it[countWithFilter(Beatmap.ranked)],
                        it[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        it[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        it[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        it[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        it[Beatmap.uploaded.max()]?.toKotlinInstant()
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