package io.beatmaps.api.playlist

import io.beatmaps.api.OauthScope
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistBasic
import io.beatmaps.api.PlaylistConstants
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.UploadResponse
import io.beatmaps.api.from
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.FileLimits
import io.beatmaps.common.Folders
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.amqp.pub
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.or
import io.beatmaps.common.util.copyToSuspend
import io.beatmaps.controllers.UploadException
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.handleMultipart
import io.beatmaps.util.requireAuthorization
import io.ktor.client.HttpClient
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import net.coobird.thumbnailator.Thumbnails
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.valiktor.constraints.NotBlank
import org.valiktor.functions.hasSize
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

interface IPlaylistUpdate {
    val name: String?
    val description: String?
    val type: String?
}

@Serializable
data class PlaylistCreateMultipart(
    override val name: String? = null,
    override val description: String? = null,
    override val type: String? = null,
    val config: SearchPlaylistConfig? = null
) : IPlaylistUpdate

@Serializable
data class PlaylistEditMultipart(
    override val name: String? = null,
    override val description: String? = null,
    override val type: String? = null,
    val deleted: Boolean? = null,
    val reason: String? = null,
    val config: SearchPlaylistConfig? = null
) : IPlaylistUpdate

fun typeFromReq(data: IPlaylistUpdate, sess: Session) =
    EPlaylistType.fromString(data.type)?.let { newType ->
        if (sess.suspended || newType == EPlaylistType.System) null else newType
    } ?: EPlaylistType.Private

val thumbnailSizes = listOf(256, 512)

fun Route.playlistCreate(client: HttpClient) {
    post<PlaylistApi.Create> {
        requireAuthorization(OauthScope.ADMIN_PLAYLISTS) { authType, sess ->
            val files = mutableMapOf<Int, File>()

            try {
                val multipart = handleMultipart(client) { part ->
                    val its = part.provider()
                    val tmp = ByteArrayOutputStream()
                    its.copyToSuspend(tmp, sizeLimit = FileLimits.PLAYLIST_IMAGE_LIMIT)

                    thumbnailSizes.forEach { s ->
                        files[s] = File(Folders.uploadTempFolder(), "upload-${System.currentTimeMillis()}-${sess.userId.hashCode()}-$s.jpg").also { localFile ->
                            Thumbnails
                                .of(tmp.toByteArray().inputStream())
                                .size(s, s)
                                .imageType(BufferedImage.TYPE_INT_RGB)
                                .outputFormat("JPEG")
                                .outputQuality(0.8)
                                .toFile(localFile)
                        }
                    }
                }

                multipart.validRecaptcha(authType) || throw UploadException("Missing recaptcha?")
                val data = multipart.get<PlaylistCreateMultipart>()

                val toCreate = PlaylistBasic(
                    0,
                    "",
                    data.name ?: "",
                    typeFromReq(data, sess),
                    sess.userId,
                    data.config
                )

                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, PlaylistConstants.MAX_NAME_LENGTH)
                    validate(PlaylistBasic::playlistImage).validate(NotBlank) {
                        files.isNotEmpty()
                    }
                }

                val newId = transaction {
                    Playlist.insertAndGetId {
                        it[name] = toCreate.name
                        it[description] = data.description?.take(PlaylistConstants.MAX_DESCRIPTION_LENGTH) ?: ""
                        it[owner] = toCreate.owner
                        it[type] = toCreate.type
                        it[config] = toCreate.config
                    }.value
                }

                files.forEach { (s, temp) ->
                    val localFile = File(Folders.localPlaylistCoverFolder(s), "$newId.jpg")
                    Files.move(temp.toPath(), localFile.toPath())
                }

                call.pub("beatmaps", "playlists.$newId.created", null, newId)
                call.respond(UploadResponse(newId.toString()))
            } finally {
                files.values.forEach { temp ->
                    temp.delete()
                }
            }
        }
    }

    post<PlaylistApi.Edit> { req ->
        requireAuthorization(OauthScope.ADMIN_PLAYLISTS) { _, sess ->
            val query = (Playlist.id eq req.id?.orNull() and Playlist.deletedAt.isNull()).let { q ->
                if (sess.isAdmin()) {
                    q
                } else {
                    q.and(Playlist.owner eq sess.userId)
                } and (Playlist.type neq EPlaylistType.System)
            }

            val beforePlaylist = transaction {
                Playlist.selectAll().where(query).firstOrNull()?.let { PlaylistFull.from(it, cdnPrefix()) }
            } ?: throw UploadException("Playlist not found")

            val multipart = handleMultipart(client) { part ->
                val its = part.provider()
                val tmp = ByteArrayOutputStream()
                its.copyToSuspend(tmp, sizeLimit = FileLimits.PLAYLIST_IMAGE_LIMIT)

                thumbnailSizes.forEach { s ->
                    val localFile = File(Folders.localPlaylistCoverFolder(s), "${req.id?.orNull()}.jpg")

                    Thumbnails
                        .of(tmp.toByteArray().inputStream())
                        .size(s, s)
                        .imageType(BufferedImage.TYPE_INT_RGB)
                        .outputFormat("JPEG")
                        .outputQuality(0.8)
                        .toFile(localFile)
                }
            }
            val data = multipart.get<PlaylistEditMultipart>()

            val newDescription = data.description?.take(PlaylistConstants.MAX_DESCRIPTION_LENGTH) ?: ""
            val toCreate = PlaylistBasic(
                0, "",
                data.name ?: "",
                typeFromReq(data, sess),
                sess.userId,
                data.config
            )

            if (data.deleted != true) {
                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, PlaylistConstants.MAX_NAME_LENGTH)
                }
            }

            transaction {
                fun updatePlaylist() {
                    Playlist.update({
                        query
                    }) {
                        if (data.deleted == true) {
                            it[deletedAt] = NowExpression(deletedAt)
                        } else {
                            it[name] = toCreate.name
                            it[description] = newDescription
                            it[type] = toCreate.type
                            it[config] = toCreate.config
                        }
                        it[updatedAt] = NowExpression(updatedAt)
                    } > 0 || throw UploadException("Update failed")
                }

                updatePlaylist().also {
                    if (sess.isAdmin() && beforePlaylist.owner.id != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            if (data.deleted == true) {
                                DeletedPlaylistData(req.id.or(0), data.reason ?: "")
                            } else {
                                EditPlaylistData(
                                    req.id.or(0),
                                    beforePlaylist.name, beforePlaylist.description, beforePlaylist.type == EPlaylistType.Public,
                                    toCreate.name, newDescription, toCreate.type == EPlaylistType.Public
                                )
                            },
                            beforePlaylist.owner.id
                        )
                    }
                }
            }

            call.pub("beatmaps", "playlists.${req.id}.updated.detail", null, req.id.or(0))
            call.respond(UploadResponse(req.id.toString()))
        }
    }
}
