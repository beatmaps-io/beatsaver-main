@file:UseSerializers(OptionalPropertySerializer::class)

package io.beatmaps.controllers

import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.from
import io.beatmaps.common.Config
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.serialization.UseSerializers
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Resource("/playlists")
class PlaylistController {
    @Resource("/{id}/edit")
    data class Edit(
        @ModelClass(Int::class)
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistController
    ) {
        init {
            requireParams(
                paramInfo(Edit::id)
            )
        }
    }

    @Resource("/new")
    data class New(
        @Ignore
        val api: PlaylistController
    )

    @Resource("/{id}")
    data class Detail(
        val id: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: PlaylistController
    ) {
        init {
            requireParams(
                paramInfo(Detail::id)
            )
        }
    }
}

fun Route.playlistController() {
    get<PlaylistController> {
        genericPage {
            link("${Config.siteBase()}/playlists", "canonical")
        }
    }

    get<PlaylistController.New> {
        genericPage()
    }

    get<PlaylistController.Detail> { req ->
        val sess = call.sessions.get<Session>()
        val isAdmin = sess?.isAdmin() == true

        val playlistData = transaction {
            Playlist
                .selectAll()
                .where {
                    (Playlist.id eq req.id?.orNull()).let {
                        if (isAdmin) {
                            it
                        } else {
                            it and Playlist.deletedAt.isNull()
                        }
                    }
                }
                .limit(1)
                .firstOrNull()
                ?.let { PlaylistFull.from(it, cdnPrefix()) }
        }

        val validPlaylist = playlistData != null && (playlistData.type.anonymousAllowed || playlistData.owner.id == sess?.userId || isAdmin)

        genericPage(if (validPlaylist) HttpStatusCode.OK else HttpStatusCode.NotFound) {
            (if (validPlaylist) playlistData else null)?.let {
                meta("og:type", "website")
                meta("og:site_name", "BeatSaver")
                meta("og:title", it.name)
                meta("og:url", it.link(true))
                link(it.link(true), "canonical")
                meta("og:image", it.playlistImage)
                meta("og:description", it.description.take(400))
                meta("og:author", it.owner.name)
                meta("og:author:url", it.owner.profileLink(absolute = true))
            }
        }
    }

    get<PlaylistController.Edit> {
        genericPage()
    }
}
