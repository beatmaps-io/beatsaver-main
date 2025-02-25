@file:UseSerializers(OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import io.beatmaps.api.user.from
import io.beatmaps.common.ModLogOpType
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.ModLogDao
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.joinUser
import io.beatmaps.common.or
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.UseSerializers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString

@Resource("/api")
class ModLogApi {
    @Resource("/modlog/{page}")
    data class ModLog(
        @ModelClass(Long::class)
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        val mod: String? = null,
        val user: String? = null,
        val type: String? = null,
        @Ignore
        val api: ModLogApi
    ) {
        init {
            requireParams(
                paramInfo(ModLog::page)
            )
        }
    }
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
                        .joinUser(ModLog.targetUser)
                        .join(Beatmap, JoinType.LEFT, ModLog.opOn, Beatmap.id)
                        .selectAll()
                        .where {
                            Op.TRUE
                                .notNull(it.mod) { m -> curatorAlias[User.uniqueName] eq m }
                                .notNull(it.user) { u -> User.uniqueName eq u }
                                .notNull(it.type) { t -> ModLogOpType.fromName(t)?.let { ModLog.type eq it.ordinal } ?: Op.FALSE }
                        }
                        .orderBy(ModLog.opAt, SortOrder.DESC)
                        .limit(it.page.or(0), 30)
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
