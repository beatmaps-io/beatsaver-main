package io.beatmaps.controllers

import io.beatmaps.common.beatsaver.localCoverFolder
import io.beatmaps.common.beatsaver.localFolder
import io.beatmaps.common.dbo.Downloads
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.login.localAvatarFolder
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.origin
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
}

suspend fun PipelineContext<*, ApplicationCall>.returnFile(file: File?) {
    if (file != null && file.exists()) {
        call.respondFile(file)
    } else {
        call.respond(HttpStatusCode.NotFound)
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
        val file = File(localFolder(it.file), "${it.file}.zip")
        transaction {
            if (file.exists()) {
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
        val file = transaction {
            VersionsDao.wrapRows(Versions.select {
                Versions.key64 eq it.file
            }.limit(1)).firstOrNull()?.let { version ->
                val file = File(localFolder(version.hash), "${version.hash}.zip")

                if (file.exists()) {
                    Downloads.insert { dl ->
                        dl[hash] = it.file
                        dl[remote] = call.request.origin.remoteHost
                    }
                }

                file
            }
        }

        call.response.header("Access-Control-Allow-Origin", "*")
        returnFile(file)
    }

    get<CDN.Cover> {
        returnFile(File(localCoverFolder(it.file), "${it.file}.jpg"))
    }

    get<CDN.Avatar> {
        returnFile(File(localAvatarFolder(), "${it.user}.png"))
    }
}