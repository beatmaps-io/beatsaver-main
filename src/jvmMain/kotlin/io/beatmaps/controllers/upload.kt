package io.beatmaps.controllers

import ch.compile.recaptcha.ReCaptchaVerify
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.PatreonTier
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.api.toTier
import io.beatmaps.common.Config
import io.beatmaps.common.CopyException
import io.beatmaps.common.Folders
import io.beatmaps.common.MapTag
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaber.BaseMapInfo
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.beatsaber.MapInfoV4
import io.beatmaps.common.beatsaber.toJson
import io.beatmaps.common.copyToSuspend
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
import io.beatmaps.common.or
import io.beatmaps.common.pub
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.RarException
import io.beatmaps.common.zip.ZipHelper
import io.beatmaps.common.zip.ZipHelper.Companion.openZip
import io.beatmaps.common.zip.ZipHelperException
import io.beatmaps.common.zip.ZipHelperWithAudio
import io.beatmaps.common.zip.sharedInsert
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.util.handleMultipart
import io.beatmaps.util.requireAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.Integer.toHexString
import java.math.BigInteger
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.logging.Logger
import kotlin.math.roundToInt

val allowUploads = System.getenv("ALLOW_UPLOADS") != "false"
val reCaptchaVerify = System.getenv("RECAPTCHA_SECRET")?.let { ReCaptchaVerify(it) }

@Location("/upload")
class UploadMap

@Location("/avatar")
class UploadAvatar

@Serializable
data class MapUploadMultipart(
    val mapId: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = null,
    val beatsage: String? = null
)

private val uploadLogger = Logger.getLogger("bmio.Upload")

fun userWipCount(userId: Int) = Beatmap
    .join(Versions, JoinType.LEFT, onColumn = Beatmap.id, otherColumn = Versions.mapId) {
        Versions.state eq EMapState.Published
    }.selectAll().where {
        Beatmap.uploader eq userId and Beatmap.deletedAt.isNull() and Versions.id.isNull()
    }.count()

fun Route.uploadController() {
    get<UploadMap> {
        if (call.sessions.get<Session>().let { it == null || it.suspended }) {
            call.respondRedirect("/")
        } else {
            genericPage()
        }
    }

    post<UploadAvatar> {
        requireAuthorization { _, sess ->
            try {
                val filename = "${sess.userId}.jpg"
                val localFile = File(Folders.localAvatarFolder(), filename)

                call.handleMultipart { part ->
                    part.streamProvider().use { its ->
                        Thumbnails
                            .of(its)
                            .size(128, 128)
                            .outputFormat("JPEG")
                            .outputQuality(0.8)
                            .toFile(localFile)

                        transaction {
                            User.update({ User.id eq sess.userId }) {
                                it[avatar] = "${Config.cdnBase("", true)}/avatar/$filename"
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            } catch (e: UnsupportedFormatException) {
                throw UploadException("Bad or unknown image format")
            }
        }
    }

    post<UploadMap> {
        requireAuthorization { authType, session ->
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

            val file = File(
                Folders.uploadTempFolder(),
                "upload-${System.currentTimeMillis()}-${session.userId.hashCode()}.zip"
            )

            val md = MessageDigest.getInstance("SHA1")
            var extractedInfoTmp: ExtractedInfo? = null

            val multipart = call.handleMultipart { part ->
                uploadLogger.info("Upload of '${part.originalFileName}' started by '${session.uniqueName}' (${session.userId})")
                extractedInfoTmp = part.streamProvider().use { its ->
                    try {
                        file.outputStream().buffered().use {
                            its.copyToSuspend(it, sizeLimit = user.uploadLimit * 1024 * 1024)
                        }.run {
                            DigestOutputStream(OutputStream.nullOutputStream(), md).use { dos ->
                                openZip(file) {
                                    validateFiles(dos)
                                }
                            }
                        }
                    } catch (e: RarException) {
                        file.delete()
                        throw UploadException("Don't upload rar files. Use the package button in your map editor.")
                    } catch (e: SerializationException) {
                        e.printStackTrace()
                        file.delete()
                        throw UploadException("Could not parse json")
                    } catch (e: ZipHelperException) {
                        file.delete()
                        throw UploadException(e.msg)
                    } catch (e: CopyException) {
                        file.delete()
                        throw UploadException("Zip file too big")
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
            }

            multipart.validRecaptcha(authType) || throw UploadException("Missing recaptcha?")
            val data = multipart.get<MapUploadMultipart>()

            val newMapId = transaction {
                // Process upload
                val fx = "%0" + md.digestLength * 2 + "x"
                val digest = String.format(fx, BigInteger(1, md.digest()))
                val newFile = File(Folders.localFolder(digest), "$digest.zip")
                val newImageFile = File(Folders.localCoverFolder(digest), "$digest.jpg")
                val newAudioFile = File(Folders.localAudioFolder(digest), "$digest.mp3")

                val existsAlready = Versions.selectAll().where {
                    Versions.hash eq digest
                }.count() > 0

                val extractedInfo = extractedInfoTmp ?: throw UploadException("Internal error 1")

                if (existsAlready) {
                    file.delete()
                    throw UploadException("Map already uploaded")
                }

                if (!session.testplay && !allowUploads) {
                    file.delete()
                    throw UploadException("Your map is fine but we're not accepting uploads yet")
                }

                fun setBasicMapInfo(
                    setFloat: (column: Column<Float>, value: Float) -> Unit,
                    setInt: (column: Column<Int>, value: Int) -> Unit,
                    setString: (column: Column<String>, value: String) -> Unit
                ) {
                    setFloat(Beatmap.bpm, extractedInfo.mapInfo.getBpm() ?: 0f)
                    setInt(Beatmap.duration, extractedInfo.duration.roundToInt())
                    setString(Beatmap.songName, extractedInfo.mapInfo.getSongName() ?: "")
                    setString(Beatmap.songSubName, extractedInfo.mapInfo.getSubName() ?: "")
                    // TODO: Make field array??
                    setString(Beatmap.levelAuthorName, extractedInfo.mapInfo.getLevelAuthorNames().firstOrNull() ?: "")
                    setString(Beatmap.songAuthorName, extractedInfo.mapInfo.getSongAuthorName() ?: "")
                }

                val newMap = try {
                    fun insertOrUpdate() =
                        data.mapId?.let { mapId ->
                            fun updateIt() = Beatmap.updateReturning(
                                {
                                    (Beatmap.id eq mapId) and (Beatmap.uploader eq session.userId)
                                },
                                {
                                    setBasicMapInfo({ a, b -> it[a] = b }, { a, b -> it[a] = b }, { a, b -> it[a] = b })
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
                            it[name] = (data.title ?: "").take(1000)
                            it[description] = (data.description ?: "").take(10000)

                            val tagsList = (data.tags ?: "").split(',').mapNotNull { t -> MapTag.fromSlug(t) }.toSet()
                            val tooMany = tagsList.groupBy { t -> t.type }.mapValues { t -> t.value.size }.withDefault { 0 }.let { byType ->
                                MapTag.maxPerType.any { type -> byType.getValue(type.key) > type.value }
                            }

                            if (!tooMany) {
                                it[tags] = tagsList.filter { t -> t != MapTag.None }.map { t -> t.slug }
                            }
                            it[uploader] = EntityID(session.userId, User)

                            setBasicMapInfo({ a, b -> it[a] = b }, { a, b -> it[a] = b }, { a, b -> it[a] = b })

                            val declaredAsAI = !data.beatsage.isNullOrEmpty()
                            it[declaredAi] = when {
                                declaredAsAI -> AiDeclarationType.Uploader
                                extractedInfo.score < 0 -> AiDeclarationType.SageScore
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
                }

                try {
                    extractedInfo.thumbnail?.let {
                        newImageFile.writeBytes(it.toByteArray())
                    } ?: throw UploadException("Internal error 2")

                    extractedInfo.preview?.let {
                        newAudioFile.writeBytes(it.toByteArray())
                    } ?: throw UploadException("Internal error 3")

                    // Pretty much guaranteed to be set
                    val sli = extractedInfo.songLengthInfo ?: throw UploadException("Couldn't determine song length")

                    val newVersion = Versions.insertAndGetId {
                        it[mapId] = newMap
                        it[key64] = null
                        it[hash] = digest
                        it[state] = EMapState.Uploaded
                        it[sageScore] = extractedInfo.score
                    }

                    extractedInfo.diffs.forEach { cLoop ->
                        cLoop.value.forEach { dLoop ->
                            val diffInfo = dLoop.key
                            val bsdiff = dLoop.value
                            val bslights = extractedInfo.lights[cLoop.key]?.get(dLoop.key)

                            Difficulty.insertAndGetId {
                                it[mapId] = newMap
                                it[versionId] = newVersion

                                sharedInsert(it, diffInfo, bsdiff, bslights, extractedInfo.mapInfo, sli)
                                it[characteristic] = cLoop.key
                                it[difficulty] = dLoop.key.enumValue()
                            }
                        }
                    }

                    newMap.value
                } catch (e: Exception) {
                    if (newFile.exists()) newFile.delete()
                    if (newImageFile.exists()) newImageFile.delete()
                    if (newAudioFile.exists()) newAudioFile.delete()
                    throw e
                }
            }

            call.pub("beatmaps", "maps.$newMapId.updated.upload", null, newMapId)
            call.respond(toHexString(newMapId))
        }
    }
}

fun ZipHelperWithAudio.validateFiles(dos: DigestOutputStream) =
    info.let {
        // Add files referenced in info.dat to whitelist
        ExtractedInfo(findAllowedFiles(it), ByteArrayOutputStream(), it, scoreMap())
    }.also { p ->
        // Rename audio file if it ends in .ogg
        val (newFiles, newFilesOriginalCase) = oggToEgg(p)

        // Ensure it ends in a slash
        val prefix = infoPrefix()
        val withoutPrefix = newFiles.map { its -> its.removePrefix(prefix.lowercase()) }.toSet()

        // Validate info.dat
        p.mapInfo.validate(withoutPrefix, p, audioFile, ::fromInfo)

        val output = p.mapInfo.toJson().toByteArray()
        dos.write(output)
        p.toHash.writeTo(dos)

        // Generate 10 second preview
        p.preview = ByteArrayOutputStream().also {
            it.writeBytes(generatePreview())
        }

        // Write updated info.dat back to zip
        infoPath.deleteIfExists()
        getPathDirect("/Info.dat").outputStream().use {
            it.write(output)
        }

        // Delete any extra files in the zip (like autosaves)
        val paritioned = newFilesOriginalCase.filter { !it.endsWith("/Info.dat", true) }.partition {
            val originalWithoutPrefix = it.lowercase().removePrefix(prefix.lowercase())
            !p.allowedFiles.contains(originalWithoutPrefix)
        }

        paritioned.first.forEach {
            getPathDirect(it).deleteIfExists()
        }

        // Move files to root
        if (prefix.length > 1) {
            // Files in subfolder!
            paritioned.second.forEach {
                moveFile(getPathDirect(it), "/" + it.removePrefix(prefix))
            }
            directories.filter { it.startsWith(prefix) }.sortedBy { it.length }.forEach {
                getPathDirect(it).deleteIfExists()
            }
        }
    }

fun findAllowedFiles(info: BaseMapInfo) =
    (listOfNotNull("info.dat", "bpminfo.dat", "cinema-video.json") + info.getExtraFiles())
        .map { it.lowercase() }

fun ZipHelper.oggToEgg(info: ExtractedInfo) =
    info.mapInfo.getSongFilename()?.let { filename ->
        fromInfo(filename.lowercase())?.let { path ->
            if (filename.endsWith(".ogg")) {
                val newFilename = filename.replace(Regex("\\.ogg$"), ".egg")
                info.mapInfo = info.mapInfo.setSongFilename(newFilename)
                moveFile(path, "/$newFilename")
                files.minus((infoPrefix() + filename).lowercase()).plus((infoPrefix() + newFilename).lowercase()) to
                    filesOriginalCase.minus(infoPrefix() + filename)
            } else {
                null
            }
        }
    } ?: (files to filesOriginalCase)

class UploadException(private val msg: String) : RuntimeException() {
    fun toResponse() = FailedUploadResponse(listOf(UploadValidationInfo(listOf(), msg)))
}
