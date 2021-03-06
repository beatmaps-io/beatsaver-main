package io.beatmaps.controllers

import io.beatmaps.api.MapDetail
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.cdnPrefix
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.response.respondRedirect
import io.ktor.routing.Route
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.meta
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/maps") class MapController {
    @Location("/{key}") data class Detail(val key: String, val api: MapController)
}

@Location("/beatsaver") class BeatsaverController {
    @Location("/{key}") data class Detail(val key: String, val api: BeatsaverController)
}

@Location("/beatmap") class BeatmapController {
    @Location("/{key}") data class RedirectOld(val key: String, val api: BeatmapController)
}

@Location("/uploader") class UploaderController {
    @Location("/{key}") data class RedirectOld(val key: String, val api: UploaderController)
}

@Location("/search") class OldSearch

@Location("/browse") class OldBrowseController {
    @Location("/hot") data class Hot(val api: OldBrowseController)
}

@Location("/profile") class UserController {
    @Location("/unlink-discord") data class UnlinkDiscord(val api: UserController)
    @Location("/{id?}") data class Detail(val id: Int? = null, val api: UserController)
    @Location("/username/{name}") data class RedirectName(val name: String, val api: UserController)
}

@Location("/playlists") class PlaylistController {
    @Location("/{id}/edit") data class Edit(val id: Int, val api: PlaylistController)
    @Location("/new") data class New(val api: PlaylistController)
    @Location("/{id}") data class Detail(val id: Int, val api: PlaylistController)
}

@Location("/mappers") class Mappers
@Location("/test") class Testplays

fun Route.mapController() {
    get<Mappers> {
        genericPage()
    }

    get<Testplays> {
        genericPage()
    }

    get<OldBrowseController.Hot> {
        call.respondRedirect("/")
    }

    get<OldSearch> {
        call.respondRedirect("/")
    }

    get<MapController.Detail> {
        genericPage(
            headerTemplate = {
                try {
                    transaction {
                        Beatmap.joinVersions().select {
                            Beatmap.id eq it.key.toInt(16)
                        }.limit(1).complexToBeatmap().map { MapDetail.from(it, cdnPrefix()) }.firstOrNull()
                    }?.let {
                        meta("og:type", "website")
                        meta("og:site_name", "BeatSaver")
                        meta("og:title", it.name)
                        meta("og:url", "${Config.basename}/maps/${it.id}")
                        meta("og:description", it.description.take(400))
                        meta("og:image", it.publishedVersion()?.coverURL)
                    }
                } catch (_: NumberFormatException) {
                    // key isn't an int
                }
            }
        )
    }

    get<BeatmapController.RedirectOld> {
        try {
            transaction {
                Beatmap.select {
                    Beatmap.id eq it.key.toInt(16)
                }.limit(1).map { MapDetail.from(it, "") }.firstOrNull()
            }?.let {
                call.respondRedirect("/maps/${it.id}")
                true
            }
        } catch (_: NumberFormatException) {
            null
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<UploaderController.RedirectOld> {
        transaction {
            User.select {
                User.hash eq it.key
            }.firstOrNull()?.let { UserDao.wrapRow(it) }
        }?.let {
            call.respondRedirect("/profile/${it.id}")
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<BeatsaverController.Detail> {
        genericPage()
    }

    get<PlaylistController.New> {
        genericPage()
    }

    get<PlaylistController.Detail> {
        genericPage(
            headerTemplate = {
                try {
                    transaction {
                        Playlist.select {
                            (Playlist.id eq it.id) and Playlist.deletedAt.isNull() and (Playlist.public eq true)
                        }.limit(1).map { PlaylistFull.from(it, cdnPrefix()) }.firstOrNull()
                    }?.let {
                        meta("og:type", "website")
                        meta("og:site_name", "BeatSaver")
                        meta("og:title", it.name)
                        meta("og:url", "${Config.basename}/playlists/${it.playlistId}")
                        meta("og:description", it.description.take(400))
                        meta("og:image", it.playlistImage)
                    }
                } catch (_: NumberFormatException) {
                    // key isn't an int
                }
            }
        )
    }

    get<PlaylistController.Edit> {
        genericPage()
    }

    get<UserController.Detail> {
        if (it.id == null && call.sessions.get<Session>() == null) {
            call.respondRedirect("/login")
        } else {
            genericPage()
        }
    }

    get<UserController.RedirectName> {
        transaction {
            User.select {
                (User.uniqueName eq it.name) and User.active
            }.firstOrNull()?.let { UserDao.wrapRow(it) }
        }?.let {
            call.respondRedirect("/profile/${it.id}")
        } ?: run {
            call.respondRedirect("/")
        }
    }

    post<UserController.UnlinkDiscord> {
        val sess = call.sessions.get<Session>()
        if (sess != null) {
            transaction {
                User.update({ User.id eq sess.userId }) {
                    it[discordId] = null
                }
            }
            call.respondRedirect("/profile#account")
        } else {
            call.respondRedirect("/login")
        }
    }
}
