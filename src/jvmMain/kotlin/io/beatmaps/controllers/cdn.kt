package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.DownloadInfo
import io.beatmaps.common.DownloadType
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.downloadFilename
import io.beatmaps.common.localAudioFolder
import io.beatmaps.common.localAvatarFolder
import io.beatmaps.common.localCoverFolder
import io.beatmaps.common.localFolder
import io.beatmaps.common.localPlaylistCoverFolder
import io.beatmaps.common.pub
import io.beatmaps.common.returnFile
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.options
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

@Location("/cdn")
class CDN {
    @Location("/{file}.zip")
    data class Zip(val file: String, val api: CDN)

    @Location("/{file}.jpg")
    data class Cover(val file: String, val api: CDN)

    @Location("/avatar/{user}.png")
    data class Avatar(val user: String, val api: CDN)

    @Location("/avatar/{user}.jpg")
    data class AvatarSimple(val user: String, val api: CDN)

    @Location("/beatsaver/{file}.zip")
    data class BeatSaver(val file: String, val api: CDN)

    @Location("/{file}.mp3")
    data class Audio(val file: String, val api: CDN)

    @Location("/beatsaver/{file}.mp3")
    data class BSAudio(val file: String, val api: CDN)

    @Location("/playlist/{file}.jpg")
    data class PlaylistCover(val file: String, val api: CDN)

    @Location("/playlist/{size}/{file}.jpg")
    data class PlaylistCoverSized(val file: String, val size: Int, val api: CDN)
}

fun Route.cdnRoute() {
    options<CDN.Zip> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    options<CDN.Cover> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    options<CDN.BeatSaver> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    get<CDN.Zip> {
        if (it.file.isBlank()) {
            throw NotFoundException()
        }

        val file = File(localFolder(it.file), "${it.file}.zip")
        val name = if (file.exists()) {
            transaction {
                Beatmap
                    .join(Versions, JoinType.FULL, onColumn = Beatmap.id, otherColumn = Versions.mapId) // , additionalConstraint = { Versions.state neq EMapState.Uploaded })
                    .select {
                        (Versions.hash eq it.file) and Beatmap.deletedAt.isNull()
                    }
                    .complexToBeatmap()
                    .firstOrNull()
                    ?.let { map ->
                        downloadFilename(Integer.toHexString(map.id.value), map.songName, map.levelAuthorName)
                    }
            }?.also { _ ->
                call.pub("beatmaps", "download.hash.${it.file}", null, DownloadInfo(it.file, DownloadType.HASH, call.request.origin.remoteHost))
            }
        } else {
            null
        } ?: throw NotFoundException()

        call.response.header("Access-Control-Allow-Origin", "*")
        returnFile(file, name)
    }

    get<CDN.BeatSaver> {
        val res = try {
            transaction {
                Beatmap.joinVersions(false)
                    .select {
                        Beatmap.id eq it.file.toInt(16) and Beatmap.deletedAt.isNull()
                    }.limit(1)
                    .complexToBeatmap()
                    .map { MapDetail.from(it, "") }
                    .firstOrNull()?.let { map ->
                        map.publishedVersion()?.let { version ->
                            val file = File(localFolder(version.hash), "${version.hash}.zip")

                            if (file.exists()) {
                                call.pub("beatmaps", "download.key.${it.file}", null, DownloadInfo(it.file, DownloadType.KEY, call.request.origin.remoteHost))
                            }

                            file to downloadFilename(map.id, map.metadata.songName, map.metadata.levelAuthorName)
                        }
                    }
            }
        } catch (_: NumberFormatException) {
            null
        } ?: throw NotFoundException()

        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        returnFile(res.first, res.second)
    }

    get<CDN.Audio> {
        if (it.file.isBlank()) {
            throw NotFoundException()
        }

        getAudio(it.file)
    }

    get<CDN.BSAudio> {
        try {
            transaction {
                VersionsDao.wrapRows(
                    Beatmap.joinVersions(false)
                        .select {
                            Beatmap.id eq it.file.toInt(16) and Beatmap.deletedAt.isNull()
                        }.limit(1)
                ).firstOrNull()?.hash
            }
        } catch (_: NumberFormatException) {
            null
        }?.let {
            getAudio(it)
        } ?: throw NotFoundException()
    }

    get<CDN.Cover> {
        if (it.file.isBlank()) {
            throw NotFoundException()
        }

        call.response.header("Access-Control-Allow-Origin", "*")
        returnFile(File(localCoverFolder(it.file), "${it.file}.jpg"))
    }

    get<CDN.PlaylistCover> {
        if (it.file.isBlank()) {
            throw NotFoundException()
        }

        returnFile(File(localPlaylistCoverFolder(), "${it.file}.jpg"))
    }

    get<CDN.PlaylistCoverSized> {
        if (it.file.isBlank()) {
            throw NotFoundException()
        }

        returnFile(File(localPlaylistCoverFolder(it.size), "${it.file}.jpg"))
    }

    get<CDN.Avatar> {
        returnFile(File(localAvatarFolder(), "${it.user}.png"))
    }

    get<CDN.AvatarSimple> {
        returnFile(File(localAvatarFolder(), "${it.user}.jpg"))
    }
}

suspend fun PipelineContext<Unit, ApplicationCall>.getAudio(hash: String) =
    returnFile(
        File(localAudioFolder(hash), "$hash.mp3")
    )
