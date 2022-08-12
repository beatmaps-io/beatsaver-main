package io.beatmaps.api

import io.beatmaps.cdnPrefix
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ModLogDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.curatorAlias
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

@Location("/api") class ModLogApi {
    @Location("/modlog/{page}") data class ModLog(
        val page: Long = 0,
        val api: ModLogApi,
        val mod: String? = null,
        val user: String? = null
    )
}

fun Route.modLogRoute() {
    get<ModLogApi.ModLog> {
        requireAuthorization { user ->
            if (!user.isAdmin()) {
                call.respond(HttpStatusCode.BadRequest)
            } else {
                val entries = transaction {
                    ModLog
                        .join(curatorAlias, JoinType.LEFT, ModLog.opBy, curatorAlias[User.id])
                        .join(User, JoinType.LEFT, ModLog.targetUser, User.id)
                        .join(Beatmap, JoinType.LEFT, ModLog.opOn, Beatmap.id)
                        .select {
                            (it.mod?.let { m -> curatorAlias[User.uniqueName] like "%$m%" } ?: Op.TRUE) and
                                (it.user?.let { u -> User.uniqueName like "%$u%" } ?: Op.TRUE)
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
                                    entry.opOn?.let { mapId -> MapDetail.from(mapId, cdnPrefix()) },
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
