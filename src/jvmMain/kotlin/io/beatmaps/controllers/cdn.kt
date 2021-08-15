package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.from
import io.beatmaps.common.beatsaver.localAudioFolder
import io.beatmaps.common.beatsaver.localCoverFolder
import io.beatmaps.common.beatsaver.localFolder
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Downloads
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.login.localAvatarFolder
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.NotFoundException
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
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
    @Location("/beatsaver/{file}.zip") data class BeatSaver(val file: String, val api: CDN)
    @Location("/{file}.mp3") data class Audio(val file: String, val api: CDN)
    @Location("/beatsaver/{file}.mp3") data class BSAudio(val file: String, val api: CDN)
}

suspend fun PipelineContext<*, ApplicationCall>.returnFile(file: File?) {
    if (file != null && file.exists()) {
        call.respondFile(file)
    } else {
        throw NotFoundException()
    }
}

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
        if (file.exists() && call.request.origin.remoteHost.length <= 15) {
            transaction {
                Downloads.insert { dl ->
                    dl[hash] = it.file
                    dl[remote] = call.request.origin.remoteHost
                }
            }
        }

        call.response.header("Access-Control-Allow-Origin", "*")
        returnFile(file)
    }

    get<CDN.BeatSaver> {
        val file = try {
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

                            if (file.exists() && call.request.origin.remoteHost.length <= 15) {
                                Downloads.insert { dl ->
                                    dl[hash] = it.file
                                    dl[remote] = call.request.origin.remoteHost
                                }
                            }

                            call.response.header(
                                HttpHeaders.ContentDisposition,
                                "attachment; filename=\"${map.id} (${map.metadata.songName} - ${map.metadata.levelAuthorName}).zip\""
                            )

                            file
                        }
                    }
            }
        } catch (_: NumberFormatException) {
            null
        }

        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        returnFile(file)
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
}

suspend fun PipelineContext<Unit, ApplicationCall>.getAudio(hash: String) =
    returnFile(
        File(localAudioFolder(hash), "${hash}.mp3")
    )
