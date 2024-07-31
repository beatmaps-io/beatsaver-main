package io.beatmaps.api

import io.beatmaps.api.user.from
import io.beatmaps.common.ModLogOpType
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ModLogDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString

@Location("/api")
class ModLogApi {
    @Location("/modlog/{page}")
    data class ModLog(
        val page: Long = 0,
        val api: ModLogApi,
        val mod: String? = null,
        val user: String? = null,
        val type: String? = null
    )
}

fun Route.modLogRoute() {
    get<ModLogApi.ModLog> {
        requireAuthorization { _, user ->
            if (!user.isAdmin()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val entries = transaction {
                    ModLog
                        .join(curatorAlias, JoinType.LEFT, ModLog.opBy, curatorAlias[User.id])
                        .join(User, JoinType.LEFT, ModLog.targetUser, User.id)
                        .join(Beatmap, JoinType.LEFT, ModLog.opOn, Beatmap.id)
                        .selectAll()
                        .where {
                            Op.TRUE
                                .notNull(it.mod) { m -> curatorAlias[User.uniqueName] eq m }
                                .notNull(it.user) { u -> User.uniqueName eq u }
                                .notNull(it.type) { t -> ModLogOpType.fromName(t)?.let { ModLog.type eq it.ordinal } ?: Op.FALSE }
                        }
                        .orderBy(ModLog.opAt, SortOrder.DESC)
                        .limit(it.page, 30)
                        .map { row ->
                            // Cache joins
                            UserDao.wrapRow(row)
                            UserDao.wrapRow(row, curatorAlias)
                            if (row[ModLog.opOn] != null) BeatmapDao.wrapRow(row)

                            ModLogDao.wrapRow(row).let { entry ->
                                ModLogEntry(
                                    UserDetail.from(entry.opBy),
                                    UserDetail.from(entry.targetUser),
                                    entry.opOn?.let { dao ->
                                        ModLogMapDetail(toHexString(dao.id.value), dao.name)
                                    },
                                    entry.realType(),
                                    entry.opAt.toKotlinInstant(),
                                    entry.realAction()
                                )
                            }
                        }
                }

                call.respond(entries)
            }
        }
    }
}
