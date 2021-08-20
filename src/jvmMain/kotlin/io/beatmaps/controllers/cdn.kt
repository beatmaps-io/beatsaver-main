package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.beatsaver.localAudioFolder
import io.beatmaps.common.beatsaver.localCoverFolder
import io.beatmaps.common.beatsaver.localFolder
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Downloads
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.pub
import io.beatmaps.login.localAvatarFolder
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.NotFoundException
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respondFile
import io.ktor.routing.Route
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

@Location("/cdn") class CDN {
    @Location("/{file}.zip") data class Zip(val file: String, val api: CDN)
    @Location("/{file}.jpg") data class Cover(val file: String, val api: CDN)
    @Location("/avatar/{user}.png") data class Avatar(val user: Long, val api: CDN)
    @Location("/avatar/{user}.jpg") data class AvatarSimple(val user: Long, val api: CDN)
    @Location("/beatsaver/{file}.zip") data class BeatSaver(val file: String, val api: CDN)
    @Location("/{file}.mp3") data class Audio(val file: String, val api: CDN)
    @Location("/beatsaver/{file}.mp3") data class BSAudio(val file: String, val api: CDN)
}

suspend fun PipelineContext<*, ApplicationCall>.returnFile(file: File?, filename: String? = null) {
    if (file != null && file.exists()) {
        filename?.let {
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"$it\""
            )
        }

        call.respondFile(file)
    } else {
        throw NotFoundException()
    }
}

val illegalCharacters = arrayOf(
    '<', '>', ':', '/', '\\', '|', '?', '*', '"',
    '\u0000', '\u0001', '\u0002', '\u0003', '\u0004', '\u0005', '\u0006', '\u0007',
    '\u0008', '\u0009', '\u000a', '\u000b', '\u000c', '\u000d', '\u000e', '\u000d',
    '\u000f', '\u0010', '\u0011', '\u0012', '\u0013', '\u0014', '\u0015', '\u0016',
    '\u0017', '\u0018', '\u0019', '\u001a', '\u001b', '\u001c', '\u001d', '\u001f',
).toCharArray()

data class DownloadInfo(val hash: String, val remote: String)

fun Route.cdnRoute() {
    options<CDN.Zip> {
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
        if (file.exists()) {
            call.pub("beatmaps", "download.${it.file}", null, DownloadInfo(it.file, call.request.origin.remoteHost))
        }

        call.response.header("Access-Control-Allow-Origin", "*")
        returnFile(file)
    }

    get<CDN.BeatSaver> {
        val res = try {
            transaction {
                Beatmap.joinVersions(false)
                    .select {
                        Beatmap.id eq it.file.toInt(16)
                    }.limit(1)
                    .complexToBeatmap()
                    .map { MapDetail.from(it) }
                    .firstOrNull()?.let { map ->
                        map.publishedVersion()?.let { version ->
                            val file = File(localFolder(version.hash), "${version.hash}.zip")

                            if (file.exists()) {
                                call.pub("beatmaps", "download.${it.file}", null, DownloadInfo(it.file, call.request.origin.remoteHost))
                            }

                            val filename = "${map.id} (${map.metadata.songName} - ${map.metadata.levelAuthorName}).zip".split(*illegalCharacters).joinToString()

                            file to filename
                        }
                    }
            }
        } catch (_: NumberFormatException) {
            null
        }

        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        returnFile(res?.first, res?.second)
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
                            Beatmap.id eq it.file.toInt(16)
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

        returnFile(File(localCoverFolder(it.file), "${it.file}.jpg"))
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
