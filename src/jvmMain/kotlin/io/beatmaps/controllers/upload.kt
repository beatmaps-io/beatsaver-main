package io.beatmaps.controllers

import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.common.Config
import io.beatmaps.common.CopyException
import io.beatmaps.common.FileLimits
import io.beatmaps.common.Folders
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaber.info.BaseMapInfo
import io.beatmaps.common.beatsaber.info.toJson
import io.beatmaps.common.beatsaber.vivify.Vivify
import io.beatmaps.common.copyToSuspend
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.pub
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.RarException
import io.beatmaps.common.zip.ZipHelper
import io.beatmaps.common.zip.ZipHelper.Companion.openZip
import io.beatmaps.common.zip.ZipHelperException
import io.beatmaps.common.zip.ZipHelperWithAudio
import io.beatmaps.controllers.upload.Upload
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.util.handleMultipart
import io.beatmaps.util.requireAuthorization
import io.ktor.client.HttpClient
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.Integer.toHexString
import java.security.DigestOutputStream
import java.util.logging.Logger

val allowUploads = System.getenv("ALLOW_UPLOADS") != "false"

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

fun Route.uploadController(client: HttpClient) {
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

                handleMultipart(client) { part ->
                    part.streamProvider().use { its ->
                        Thumbnails
                            .of(its)
                            .size(128, 128)
                            .imageType(BufferedImage.TYPE_INT_RGB)
                            .outputFormat("JPEG")
                            .outputQuality(0.8)
                            .toFile(localFile)

                        transaction {
                            User.update({ User.id eq sess.userId }) {
                                it[avatar] = "${Config.cdnBase("", true)}/avatar/$filename"
                                it[updatedAt] = NowExpression(updatedAt)
                            }
                        }
                    }
                }

                call.respond(HttpStatusCode.OK)
            } catch (_: UnsupportedFormatException) {
                throw UploadException("Bad or unknown image format")
            }
        }
    }

    post<UploadMap> {
        requireAuthorization { authType, session ->
            val user = Upload.checkUserCanUpload(session)

            val file = File(
                Folders.uploadTempFolder(),
                "upload-${System.currentTimeMillis()}-${session.userId.hashCode()}.zip"
            )

            val basicLimit = user.uploadLimit * 1024 * 1024L
            val vivifyLimit = user.vivifyLimit * 1024 * 1024L
            val totalLimit = basicLimit + (vivifyLimit * Vivify.allowedBundles.size)

            val multipart = handleMultipart(client) { part ->
                uploadLogger.info("Upload of '${part.originalFileName}' started by '${session.uniqueName}' (${session.userId})")

                part.streamProvider().use { its ->
                    try {
                        file.outputStream().buffered().use {
                            its.copyToSuspend(it, sizeLimit = totalLimit)
                        }.let { actualSize ->
                            openZip(file) {
                                validateFiles(vivifyLimit)
                            }.copy(uncompressedSize = actualSize)
                        }
                    } catch (_: RarException) {
                        throw UploadException("Don't upload rar files. Use the package button in your map editor.")
                    } catch (e: SerializationException) {
                        e.printStackTrace()
                        throw UploadException("Could not parse json")
                    } catch (e: ZipHelperException) {
                        throw UploadException(e.msg)
                    } catch (_: CopyException) {
                        throw UploadException("Zip file too big")
                    } catch (e: Exception) {
                        throw e
                    } finally {
                        file.delete()
                    }
                }
            }

            multipart.validRecaptcha(authType) || throw UploadException("Missing recaptcha?")
            val data = multipart.get<MapUploadMultipart>()

            val extractedInfo = multipart.fileOutput ?: throw UploadException("Internal error 1")

            // Zip could have been too big but within vivify allowance
            val sizeWithoutVivify = extractedInfo.uncompressedSize - extractedInfo.vivifySize
            if (sizeWithoutVivify > basicLimit) {
                throw UploadException("Zip file too big (${FileLimits.printLimit(sizeWithoutVivify, basicLimit)})")
            }

            val newMapId = Upload.insertNewMap(extractedInfo, data, session, file)

            call.pub("beatmaps", "maps.$newMapId.updated.upload", null, newMapId)
            call.respond(toHexString(newMapId))
        }
    }
}

fun ZipHelperWithAudio.validateFiles(maxVivify: Long) =
    info.let {
        // Add files referenced in info.dat to whitelist
        ExtractedInfo(findAllowedFiles(it), ByteArrayOutputStream(), it, scoreMap())
    }.also { p ->
        DigestOutputStream(OutputStream.nullOutputStream(), p.md).use { dos ->
            // Rename audio file if it ends in .ogg
            val (newFiles, newFilesOriginalCase) = oggToEgg(p)

            // Ensure it ends in a slash
            val prefix = infoPrefix()
            val withoutPrefix = newFiles.map { its -> its.removePrefix(prefix.lowercase()) }.toSet()

            // Validate info.dat
            p.mapInfo.validate(withoutPrefix, p, audioFile, previewAudioFile, maxVivify, ::fromInfo)

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
    }

fun findAllowedFiles(info: BaseMapInfo) =
    (listOfNotNull("info.dat", "cinema-video.json") + info.getExtraFiles())
        .map { it.lowercase() }

fun ZipHelper.oggToEgg(info: ExtractedInfo): Pair<Set<String>, Set<String>> {
    val moved = setOf(info.mapInfo.getSongFilename(), info.mapInfo.getPreviewInfo().filename)
        .filterNotNull()
        .filter { it.endsWith(".ogg") }
        .fold(mapOf<String, String>()) { acc, filename ->
            fromInfo(filename.lowercase())?.let { path ->
                val newFilename = filename.replace(Regex("\\.ogg$"), ".egg")
                moveFile(path, "/$newFilename")
                acc.plus(filename to newFilename)
            } ?: acc
        }

    info.mapInfo = info.mapInfo.updateFiles(moved)

    return files
        .minus(moved.keys.map { (infoPrefix() + it).lowercase() }.toSet())
        .plus(moved.values.map { (infoPrefix() + it).lowercase() }.toSet()) to
        // Don't add it back so that we don't later try and remove the file as it's no longer whitelisted
        filesOriginalCase.minus(moved.keys.map { infoPrefix() + it }.toSet())
}

class UploadException(private val msg: String) : RuntimeException() {
    fun toResponse() = FailedUploadResponse(listOf(UploadValidationInfo(listOf(), msg)))
}
