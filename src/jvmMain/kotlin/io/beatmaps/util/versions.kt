package io.beatmaps.util

import io.beatmaps.api.MapVersion
import io.beatmaps.api.from
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EAlertType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Alert
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.DifficultyDao
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinUploader
import org.jetbrains.exposed.sql.Coalesce
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.NoOpConversion
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import pl.jutupe.ktor_rabbitmq.RabbitMQInstance
import java.lang.Integer.toHexString
import java.math.BigDecimal

fun <T> QueryParameter<T>.withColumnType() = NoOpConversion(this, this.sqlType)

fun publishVersion(mapId: Int, hash: String, alert: Boolean?, rb: RabbitMQInstance?, additionalCallback: (Op<Boolean>) -> Op<Boolean> = { it }): Boolean {
    val publishingVersion = VersionsDao.wrapRow(
        Versions.selectAll().where {
            Versions.hash eq hash
        }.single()
    )

    val originalState = MapVersion.from(publishingVersion, "")

    fun updateState() =
        Versions.join(Beatmap, JoinType.INNER, onColumn = Versions.mapId, otherColumn = Beatmap.id).update({
            additionalCallback((Versions.mapId eq mapId))
        }) {
            val exp = Expression.build {
                case()
                    .When(Versions.hash eq hash, QueryParameter(EMapState.Published, Versions.state.columnType).withColumnType())
                    .Else(QueryParameter(EMapState.Uploaded, Versions.state.columnType).withColumnType())
            }

            val exp2 = Expression.build {
                case()
                    .When(Versions.hash eq hash, NowExpression(Versions.lastPublishedAt, transactionTime = false))
                    .Else(Versions.lastPublishedAt)
            }

            it[Versions.state] = exp
            it[Versions.scheduledAt] = null
            it[Versions.lastPublishedAt] = exp2
        }

    return (updateState() > 0).also { valid ->
        if (!valid) return@also

        val stats = DifficultyDao.wrapRows(
            Difficulty.selectAll().where {
                Difficulty.versionId eq publishingVersion.id
            }
        )

        val map = Beatmap
            .joinUploader()
            .joinCollaborators()
            .selectAll()
            .where {
                (Beatmap.id eq mapId)
            }.complexToBeatmap()
            .firstOrNull()
            ?.also {
                if (alert == true) {
                    pushAlerts(it, rb)
                }
            }

        // Set published time for sorting, but don't allow gaming the system
        Beatmap.update(
            { Beatmap.id eq mapId }
        ) {
            it[uploaded] = Coalesce(uploaded, NowExpression(uploaded, transactionTime = false))
            it[lastPublishedAt] = NowExpression(lastPublishedAt, transactionTime = false)
            it[updatedAt] = NowExpression(updatedAt, transactionTime = false)

            it[bpm] = publishingVersion.bpm
            it[duration] = publishingVersion.duration
            it[minNps] = stats.minByOrNull { s -> s.nps }?.nps ?: BigDecimal.ZERO
            it[maxNps] = stats.maxByOrNull { s -> s.nps }?.nps ?: BigDecimal.ZERO

            // Only override None and SageScore
            if (map?.declaredAi?.override == true) {
                it[declaredAi] = if (originalState.sageScore?.let { s -> s < 0 } == true) {
                    AiDeclarationType.SageScore
                } else {
                    AiDeclarationType.None
                }
            }
        }
    }
}

fun pushAlerts(map: BeatmapDao, rb: RabbitMQInstance?) {
    val allAuthors = (map.collaborators.values + map.uploader).reversed()
    val authorIds = allAuthors.map { it.id }

    val recipients = Follows.select(Follows.followerId).where {
        (Follows.userId inList authorIds) and (Follows.followerId notInList authorIds) and (((Follows.userId eq map.uploaderId) and Follows.upload) or ((Follows.userId neq map.uploaderId) and Follows.collab)) and Follows.following
    }.withDistinct().map { row ->
        row[Follows.followerId].value
    }

    val authorNames = allAuthors.joinToString(separator = ", ") {
        "@" + it.uniqueName
    }

    val (title, adjective) = if (map.lastPublishedAt == null) ("New Map Release" to "released") else ("Map Updated" to "updated")
    Alert.insert(
        title,
        "$authorNames just $adjective #${toHexString(map.id.value)}: **${map.name}**.\n" +
            "*\"${Alert.forDescription(map.description)}\"*",
        EAlertType.MapRelease,
        recipients
    )
    updateAlertCount(rb, recipients)
}
