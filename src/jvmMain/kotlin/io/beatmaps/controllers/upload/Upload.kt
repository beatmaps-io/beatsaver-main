package io.beatmaps.controllers.upload

import io.beatmaps.api.MapConstants
import io.beatmaps.api.PatreonTier
import io.beatmaps.api.toTier
import io.beatmaps.common.Folders
import io.beatmaps.common.MapTag
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.handlePatreon
import io.beatmaps.common.dbo.joinPatreon
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.sharedInsert
import io.beatmaps.controllers.MapUploadMultipart
import io.beatmaps.controllers.UploadException
import io.beatmaps.controllers.userWipCount
import io.beatmaps.login.Session
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Files
import kotlin.math.roundToInt

object Upload {
    private val allowUploads = System.getenv("ALLOW_UPLOADS") != "false"

    fun checkUserCanUpload(session: Session): UserDao {
        val (user, patreon, currentWipCount) = transaction {
            val user = UserDao.wrapRow(
                User.joinPatreon().selectAll().where { User.id eq session.userId }.handlePatreon().first()
            )

            Triple(user, user.patreon, userWipCount(session.userId))
        }

        // Throw error if user is missing a username
        (user.active && user.uniqueName != null) || throw UploadException("Please pick a username to complete your account")

        // Don't allow suspended users to upload
        user.suspendedAt == null || throw UploadException("Suspended account")

        // Limit WIP maps
        val maxWips = (patreon.toTier() ?: PatreonTier.None).maxWips
        currentWipCount < maxWips || throw UploadException(PatreonTier.maxWipsMessage)

        return user
    }

    private fun insertVersion(info: ExtractedInfo, newMap: EntityID<Int>, file: File) {
        val digest = info.digest
        val newImageFile = File(Folders.localCoverFolder(digest), "$digest.jpg")
        val newAudioFile = File(Folders.localAudioFolder(digest), "$digest.mp3")

        try {
            info.thumbnail.size() > 0 || throw UploadException("Internal error 2")
            newImageFile.outputStream().use {
                info.thumbnail.writeTo(it)
            }

            info.preview.size() > 0 || throw UploadException("Internal error 3")
            newAudioFile.outputStream().use {
                info.preview.writeTo(it)
            }

            // Pretty much guaranteed to be set
            val sli = info.songLengthInfo ?: throw UploadException("Couldn't determine song length")

            val newVersion = Versions.insertAndGetId {
                it[mapId] = newMap
                it[key64] = null
                it[hash] = digest
                it[state] = EMapState.Uploaded
                it[sageScore] = info.score
                it[schemaVersion] = info.mapInfo.version.orNull()

                it[bpm] = info.mapInfo.getBpm() ?: 0f
                it[duration] = info.duration.roundToInt()
                it[songName] = info.mapInfo.getSongName() ?: ""
                it[songSubName] = info.mapInfo.getSubName() ?: ""
                it[levelAuthorName] = info.mapInfo.getLevelAuthorNamesString()
                it[songAuthorName] = info.mapInfo.getSongAuthorName() ?: ""
            }

            info.diffs.forEach { cLoop ->
                cLoop.value.forEach { dLoop ->
                    val diffInfo = dLoop.key
                    val bsdiff = dLoop.value
                    val bslights = info.lights[cLoop.key]?.get(dLoop.key)

                    Difficulty.insertAndGetId {
                        it[mapId] = newMap
                        it[versionId] = newVersion

                        sharedInsert(it, cLoop.key, diffInfo, bsdiff, bslights, info.mapInfo, sli)
                        it[characteristic] = cLoop.key
                        it[difficulty] = dLoop.key.enumValue()
                    }
                }
            }
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            if (newImageFile.exists()) newImageFile.delete()
            if (newAudioFile.exists()) newAudioFile.delete()
            throw e
        }
    }

    fun insertNewMap(info: ExtractedInfo, data: MapUploadMultipart, session: Session, file: File) = transaction {
        // Process upload
        val newFile = File(Folders.localFolder(info.digest), "${info.digest}.zip")

        val existsAlready = Versions.selectAll().where {
            Versions.hash eq info.digest
        }.count() > 0

        if (existsAlready) {
            file.delete()
            throw UploadException("Map already uploaded")
        }

        if (!session.testplay && !allowUploads) {
            file.delete()
            throw UploadException("Your map is fine but we're not accepting uploads yet")
        }

        try {
            fun insertOrUpdate() =
                data.mapId?.let { mapId ->
                    fun updateIt() = Beatmap.updateReturning(
                        {
                            (Beatmap.id eq mapId) and (Beatmap.uploader eq session.userId)
                        },
                        {
                            // Bpm and duration will be updated on publish
                            it[updatedAt] = NowExpression(updatedAt)
                        },
                        Beatmap.id
                    )?.firstOrNull()?.let { it[Beatmap.id] } ?: throw UploadException("Map doesn't exist to add version")

                    updateIt().also {
                        val latestVersions = VersionsDao.wrapRows(
                            Versions.selectAll().where {
                                (Versions.mapId eq mapId)
                            }.orderBy(Versions.uploaded, SortOrder.DESC).limit(2)
                        ).toList()

                        if (latestVersions.size > 1) {
                            // Check time since one before previous upload = 2 uploads / day / map
                            val hoursUntilNext = 12 - Clock.System.now().minus(latestVersions[1].uploaded.toKotlinInstant()).inWholeHours
                            if (hoursUntilNext > 0) {
                                throw UploadException("Please wait another $hoursUntilNext hours before uploading another version")
                            }
                        }
                    }
                } ?: Beatmap.insertAndGetId {
                    it[name] = (data.title ?: "").take(MapConstants.MAX_NAME_LENGTH)
                    it[description] = (data.description ?: "").take(MapConstants.MAX_DESCRIPTION_LENGTH)

                    val tagsList = (data.tags ?: "").split(',').mapNotNull { t -> MapTag.fromSlug(t) }.toSet()
                    val tooMany = tagsList.groupBy { t -> t.type }.mapValues { t -> t.value.size }.withDefault { 0 }.let { byType ->
                        MapTag.maxPerType.any { type -> byType.getValue(type.key) > type.value }
                    }

                    if (!tooMany) {
                        it[tags] = tagsList.filter { t -> t != MapTag.None }.map { t -> t.slug }
                    }
                    it[uploader] = EntityID(session.userId, User)

                    // Should these be real data, fields are updated on publish
                    it[bpm] = info.mapInfo.getBpm() ?: 0f
                    it[duration] = info.duration.roundToInt()

                    val declaredAsAI = !data.beatsage.isNullOrEmpty()
                    it[declaredAi] = when {
                        declaredAsAI -> AiDeclarationType.Uploader
                        info.score < 0 -> AiDeclarationType.SageScore
                        else -> AiDeclarationType.None
                    }

                    it[plays] = 0
                }

            insertOrUpdate().also {
                // How is a file here if it hasn't been uploaded before?
                if (newFile.exists()) {
                    newFile.delete()
                }

                Files.move(file.toPath(), newFile.toPath())
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }.also { newMap ->
            insertVersion(info, newMap, newFile)
        }.value
    }
}
