package io.beatmaps.util

import io.beatmaps.common.Folders
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.pub
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.lang.Integer.toHexString
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Timer
import java.util.TimerTask

class BeatsageCleanse(private val app: Application) : TimerTask() {
    private val logger = KotlinLogging.logger {}

    override fun run() {
        try {
            transaction {
                // This query is probably fairly slow but as we only run it once per minute (and could run it less often)
                // it probably doesn't need optimisation
                VersionsDao.wrapRows(
                    Versions.join(Beatmap, JoinType.INNER, Versions.mapId, Beatmap.id).selectAll().where {
                        // Version not deleted, beatsage, beatmap is deleted
                        (Versions.deletedAt.isNull()) and (Versions.sageScore lessEq (-10).toShort()) and
                            (Beatmap.deletedAt less Instant.now().minus(7L, ChronoUnit.DAYS))
                    }.limit(10)
                ).forEach { version ->
                    val digest = version.hash
                    val file = File(Folders.localFolder(digest), "$digest.zip")
                    val imageFile = File(Folders.localCoverFolder(digest), "$digest.jpg")
                    val audioFile = File(Folders.localAudioFolder(digest), "$digest.mp3")

                    file.delete()
                    imageFile.delete()
                    audioFile.delete()

                    Versions.update({
                        Versions.hash eq digest
                    }) { u ->
                        u[deletedAt] = NowExpression(deletedAt)
                    }

                    logger.info { "Permanently deleted $digest" }
                }
            }

            transaction {
                val subQuery = Beatmap
                    .joinVersions {
                        Versions.sageScore less (-10).toShort()
                    }
                    .select(Beatmap.id)
                    .where {
                        Beatmap.deletedAt.isNull() and (Beatmap.declaredAi neq AiDeclarationType.None) and (Beatmap.updatedAt less Instant.now().minus(90L, ChronoUnit.DAYS))
                    }
                    .orderBy(Beatmap.updatedAt to SortOrder.ASC)
                    .limit(5)

                Beatmap.updateReturning({
                    Beatmap.id inSubQuery(subQuery) and Beatmap.deletedAt.isNull()
                }, {
                    it[deletedAt] = NowExpression(deletedAt)
                }, Beatmap.id)?.map {
                    it[Beatmap.id].value
                }?.onEach {
                    logger.info { "Deleted old beatsage map #${toHexString(it)}" }
                }
            }?.forEach {
                app.pub("beatmaps", "maps.$it.updated.deleted", null, it)
            }
        } catch (e: Exception) {
            logger.error(e) { "Exception while running task" }
        }
    }

    companion object {
        fun Application.scheduleCleanser() {
            val cleanserEnabled = System.getenv("CLEANSE_ENABLED") == "true"
            if (cleanserEnabled) Timer("BeatSage Cleanse").scheduleAtFixedRate(BeatsageCleanse(this), 5000L, 60 * 1000L)
        }
    }
}
