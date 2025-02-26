package io.beatmaps.controllers

import io.beatmaps.api.UploadResponse
import io.beatmaps.api.UploadValidationInfo
import io.beatmaps.common.Config
import io.beatmaps.common.FileLimits
import io.beatmaps.common.Folders
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaber.vivify.Vivify
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.util.CopyException
import io.beatmaps.common.util.copyToSuspend
import io.beatmaps.common.zip.RarException
import io.beatmaps.common.zip.ZipHelper.Companion.openZip
import io.beatmaps.common.zip.ZipHelperException
import io.beatmaps.controllers.upload.Upload
import io.beatmaps.controllers.upload.initValidation
import io.beatmaps.controllers.upload.validateFiles
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.util.handleMultipart
import io.beatmaps.util.requireAuthorization
import io.ktor.client.HttpClient
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.utils.io.jvm.javaio.toInputStream
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
import java.io.File
import java.lang.Integer.toHexString
import java.util.logging.Logger

@Resource("/upload")
class UploadMap

@Resource("/avatar")
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
                    part.provider().toInputStream().use { its ->
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

                call.respond(UploadResponse())
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

                val its = part.provider()

                runCatching {
                    file.outputStream().buffered().use {
                        its.copyToSuspend(it, sizeLimit = totalLimit)
                    }.let { actualSize ->
                        openZip(file) {
                            validateFiles(
                                initValidation(vivifyLimit)
                            )
                        }.copy(compressedSize = actualSize)
                    }
                }.getOrElse { e ->
                    file.delete()

                    when (e) {
                        is RarException -> throw UploadException("Don't upload rar files. Use the package button in your map editor.")
                        is SerializationException -> {
                            e.printStackTrace()
                            throw UploadException("Could not parse json")
                        }
                        is ZipHelperException -> throw UploadException(e.msg)
                        is CopyException -> throw UploadException("Zip file too big")
                        else -> throw e
                    }
                }
            }

            multipart.validRecaptcha(authType) || throw UploadException("Missing recaptcha?")
            val data = multipart.get<MapUploadMultipart>()

            val extractedInfo = multipart.fileOutput ?: throw UploadException("Internal error 1")

            // Zip could have been too big but within vivify allowance
            val sizeWithoutVivify = extractedInfo.compressedSize - extractedInfo.vivifySize
            if (sizeWithoutVivify > basicLimit) {
                throw UploadException("Zip file too big (${FileLimits.printLimit(sizeWithoutVivify, basicLimit)})")
            }

            val newMapId = Upload.insertNewMap(extractedInfo, data, session, file)

            call.pub("beatmaps", "maps.$newMapId.updated.upload", null, newMapId)
            call.respond(UploadResponse(toHexString(newMapId)))
        }
    }
}

class UploadException(private val msg: String) : RuntimeException() {
    fun toResponse() = UploadResponse(listOf(UploadValidationInfo(listOf(), msg)))
}
