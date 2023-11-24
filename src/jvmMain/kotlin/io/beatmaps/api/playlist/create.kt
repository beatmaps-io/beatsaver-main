package io.beatmaps.api.playlist

import io.beatmaps.api.OauthScope
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistBasic
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.api.requireAuthorization
import io.beatmaps.common.DeletedPlaylistData
import io.beatmaps.common.EditPlaylistData
import io.beatmaps.common.SearchPlaylistConfig
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.copyToSuspend
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.dbo.ModLog
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.localPlaylistCoverFolder
import io.beatmaps.controllers.UploadException
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.handleMultipart
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.locations.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable
import net.coobird.thumbnailator.Thumbnails
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.valiktor.functions.hasSize
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
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
    override val type: String? = null
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

fun Route.playlistCreate() {
    post<PlaylistApi.Create> {
        requireAuthorization(io.beatmaps.api.OauthScope.ADMIN_PLAYLISTS) { sess ->
            val files = mutableMapOf<Int, File>()

            try {
                val multipart = call.handleMultipart { part ->
                    part.streamProvider().use { its ->
                        val tmp = ByteArrayOutputStream()
                        its.copyToSuspend(tmp, sizeLimit = 10 * 1024 * 1024)

                        thumbnailSizes.forEach { s ->
                            files[s] = File(io.beatmaps.controllers.uploadDir, "upload-${java.lang.System.currentTimeMillis()}-${sess.userId.hashCode()}-$s.jpg").also { localFile ->
                                net.coobird.thumbnailator.Thumbnails
                                    .of(tmp.toByteArray().inputStream())
                                    .size(s, s)
                                    .outputFormat("JPEG")
                                    .outputQuality(0.8)
                                    .toFile(localFile)
                            }
                        }
                    }
                }

                multipart.recaptchaSuccess || throw UploadException("Missing recaptcha?")
                val data = multipart.get<PlaylistCreateMultipart>()

                val toCreate = PlaylistBasic(
                    0,
                    "",
                    data.name ?: "",
                    typeFromReq(data, sess),
                    sess.userId
                )

                validate(toCreate) {
                    validate(io.beatmaps.api.PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                    validate(io.beatmaps.api.PlaylistBasic::playlistImage).validate(org.valiktor.constraints.NotBlank) {
                        files.isNotEmpty()
                    }
                }

                val newId = transaction {
                    io.beatmaps.common.dbo.Playlist.insertAndGetId {
                        it[name] = toCreate.name
                        it[description] = data.description ?: ""
                        it[owner] = toCreate.owner
                        it[type] = toCreate.type
                    }
                }

                files.forEach { (s, temp) ->
                    val localFile = File(localPlaylistCoverFolder(s), "$newId.jpg")
                    Files.move(temp.toPath(), localFile.toPath())
                }

                call.respond(newId.value)
            } finally {
                files.values.forEach { temp ->
                    temp.delete()
                }
            }
        }
    }

    post<PlaylistApi.Edit> { req ->
        requireAuthorization(OauthScope.ADMIN_PLAYLISTS) { sess ->
            val query = (Playlist.id eq req.id and Playlist.deletedAt.isNull()).let { q ->
                if (sess.isAdmin()) {
                    q
                } else {
                    q.and(Playlist.owner eq sess.userId)
                } and (Playlist.type neq EPlaylistType.System)
            }

            val beforePlaylist = transaction {
                Playlist.select(query).firstOrNull()?.let { PlaylistFull.from(it, cdnPrefix()) }
            } ?: throw UploadException("Playlist not found")

            val multipart = call.handleMultipart { part ->
                part.streamProvider().use { its ->
                    val tmp = ByteArrayOutputStream()
                    its.copyToSuspend(tmp, sizeLimit = 10 * 1024 * 1024)

                    thumbnailSizes.forEach { s ->
                        val localFile = File(localPlaylistCoverFolder(s), "${req.id}.jpg")

                        Thumbnails
                            .of(tmp.toByteArray().inputStream())
                            .size(s, s)
                            .outputFormat("JPEG")
                            .outputQuality(0.8)
                            .toFile(localFile)
                    }
                }
            }
            val data = multipart.get<PlaylistEditMultipart>()

            val newDescription = data.description ?: ""
            val toCreate = PlaylistBasic(
                0, "",
                data.name ?: "",
                typeFromReq(data, sess),
                sess.userId,
                data.config
            )

            if (data.deleted != true) {
                validate(toCreate) {
                    validate(PlaylistBasic::name).isNotBlank().hasSize(3, 255)
                }
            }

            transaction {
                fun updatePlaylist() {
                    Playlist.update({
                        query
                    }) {
                        if (data.deleted == true) {
                            it[deletedAt] = NowExpression(deletedAt.columnType)
                        } else {
                            it[name] = toCreate.name
                            it[description] = newDescription
                            it[type] = toCreate.type
                            it[config] = toCreate.config
                        }
                        it[updatedAt] = NowExpression(updatedAt.columnType)
                    } > 0 || throw UploadException("Update failed")
                }

                updatePlaylist().also {
                    if (sess.isAdmin() && beforePlaylist.owner.id != sess.userId) {
                        ModLog.insert(
                            sess.userId,
                            null,
                            if (data.deleted == true) {
                                DeletedPlaylistData(req.id, data.reason ?: "")
                            } else {
                                EditPlaylistData(
                                    req.id,
                                    beforePlaylist.name, beforePlaylist.description, beforePlaylist.type == EPlaylistType.Public,
                                    toCreate.name, newDescription, toCreate.type == EPlaylistType.Public
                                )
                            },
                            beforePlaylist.owner.id
                        )
                    }
                }
            }

            call.respond(HttpStatusCode.OK)
        }
    }
}