package io.beatmaps.api.playlist

import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import io.beatmaps.api.LatestPlaylistSort
import io.beatmaps.api.OauthScope
import io.beatmaps.api.PlaylistApi
import io.beatmaps.api.PlaylistBasic
import io.beatmaps.api.PlaylistFull
import io.beatmaps.api.PlaylistSearchResponse
import io.beatmaps.api.from
import io.beatmaps.api.limit
import io.beatmaps.api.notNull
import io.beatmaps.api.optionalAuthorization
import io.beatmaps.api.parseSearchQuery
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.api.EPlaylistType
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.greaterEqF
import io.beatmaps.common.db.lessEqF
import io.beatmaps.common.dbo.*
import io.beatmaps.util.cdnPrefix
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.playlistSearch() {
    options<PlaylistApi.ByUploadDate> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.ByUploadDate>("Get playlists ordered by created/updated".responds(ok<PlaylistSearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            val sortField = when (it.sort) {
                null, LatestPlaylistSort.CREATED -> Playlist.createdAt
                LatestPlaylistSort.SONGS_UPDATED -> Playlist.songsChangedAt
                LatestPlaylistSort.UPDATED -> Playlist.updatedAt
            }

            val playlists = transaction {
                Playlist
                    .joinMaps()
                    .joinPlaylistCurator()
                    .joinOwner()
                    .slice(Playlist.columns + User.columns + curatorAlias.columns + Playlist.Stats.all)
                    .select {
                        Playlist.id.inSubQuery(
                            Playlist
                                .slice(Playlist.id)
                                .select {
                                    (Playlist.deletedAt.isNull() and (sess?.let { s -> Playlist.owner eq s.userId or (Playlist.type eq EPlaylistType.Public) } ?: (Playlist.type eq EPlaylistType.Public)))
                                        .notNull(it.before) { o -> sortField less o.toJavaInstant() }
                                        .notNull(it.after) { o -> sortField greater o.toJavaInstant() }
                                }
                                .orderBy(sortField to (if (it.after != null) SortOrder.ASC else SortOrder.DESC))
                                .limit(20)
                        )
                    }
                    .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                    .handleOwner()
                    .handleCurator()
                    .sortedByDescending { row ->
                        when (it.sort) {
                            null, LatestPlaylistSort.CREATED -> row[Playlist.createdAt]
                            LatestPlaylistSort.SONGS_UPDATED -> row[Playlist.songsChangedAt]
                            LatestPlaylistSort.UPDATED -> row[Playlist.updatedAt]
                        }
                    }
                    .map { playlist ->
                        PlaylistFull.from(playlist, cdnPrefix())
                    }
            }

            call.respond(PlaylistSearchResponse(playlists))
        }
    }

    options<PlaylistApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.Text>("Search for playlists".responds(ok<PlaylistSearchResponse>())) { it ->
        call.response.header("Access-Control-Allow-Origin", "*")

        val searchFields = PgConcat(" ", Playlist.name, Playlist.description)
        val searchInfo = parseSearchQuery(it.q, searchFields)
        val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)
        val sortArgs = when (actualSortOrder) {
            SearchOrder.Relevance -> listOf(searchInfo.similarRank to SortOrder.DESC, Playlist.createdAt to SortOrder.DESC)
            SearchOrder.Rating, SearchOrder.Latest -> listOf(Playlist.createdAt to SortOrder.DESC)
            SearchOrder.Curated -> listOf(Playlist.curatedAt to SortOrder.DESC_NULLS_LAST, Playlist.createdAt to SortOrder.DESC)
            SearchOrder.Oldest -> listOf(Beatmap.createdAt to SortOrder.ASC)
        }.toTypedArray()
        newSuspendedTransaction {
            val playlists = Playlist
                .joinMaps()
                .joinOwner()
                .joinPlaylistCurator()
                .slice(
                    (if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) +
                        Playlist.columns + User.columns + curatorAlias.columns + Playlist.Stats.all
                )
                .select {
                    Playlist.id.inSubQuery(
                        Playlist
                            .joinOwner()
                            .slice(Playlist.id)
                            .select {
                                (Playlist.deletedAt.isNull() and (Playlist.type inList EPlaylistType.entries.filter { v -> v.anonymousAllowed }))
                                    .let { q -> searchInfo.applyQuery(q) }
                                    .let { q ->
                                        if (it.includeEmpty != true) {
                                            q.and((Playlist.totalMaps greater 0) or (Playlist.type eq EPlaylistType.Search))
                                        } else { q }
                                    }
                                    .notNull(searchInfo.userSubQuery) { o -> Playlist.owner inSubQuery o }
                                    .notNull(it.minNps) { o -> Playlist.maxNps greaterEqF o }
                                    .notNull(it.maxNps) { o -> Playlist.minNps lessEqF o }
                                    .notNull(it.from) { o -> Playlist.createdAt greaterEq o.toJavaInstant() }
                                    .notNull(it.to) { o -> Playlist.createdAt lessEq o.toJavaInstant() }
                                    .notNull(it.curated) { o -> with(Playlist.curatedAt) { if (o) isNotNull() else isNull() } }
                                    .notNull(it.verified) { o -> User.verifiedMapper eq o }
                            }
                            .orderBy(*sortArgs)
                            .limit(it.page)
                    )
                }
                .groupBy(Playlist.id, User.id, curatorAlias[User.id])
                .orderBy(*sortArgs)
                .handleCurator()
                .handleOwner()
                .map { playlist ->
                    PlaylistFull.from(playlist, cdnPrefix())
                }

            call.respond(PlaylistSearchResponse(playlists))
        }
    }

    options<PlaylistApi.ByUser> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<PlaylistApi.ByUser>("Get playlists by user".responds(ok<PlaylistSearchResponse>())) { req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        optionalAuthorization(OauthScope.PLAYLISTS) { sess ->
            fun <T> doQuery(table: FieldSet = Playlist, groupBy: Array<Column<*>> = arrayOf(Playlist.id), block: (ResultRow) -> T) =
                transaction {
                    table
                        .select {
                            Playlist.id.inSubQuery(
                                Playlist
                                    .slice(Playlist.id)
                                    .select {
                                        ((Playlist.owner eq req.userId) and Playlist.deletedAt.isNull()).let {
                                            if (req.userId == sess?.userId) {
                                                it
                                            } else {
                                                it and (Playlist.type eq EPlaylistType.Public)
                                            }
                                        }
                                    }
                                    .orderBy(
                                        (Playlist.type neq EPlaylistType.System) to SortOrder.ASC,
                                        Playlist.createdAt to SortOrder.DESC
                                    )
                                    .limit(req.page, 20)
                            )
                        }
                        .orderBy(
                            (Playlist.type neq EPlaylistType.System) to SortOrder.ASC,
                            Playlist.createdAt to SortOrder.DESC
                        )
                        .groupBy(*groupBy)
                        .handleOwner()
                        .handleCurator()
                        .map(block)
                }

            if (req.basic) {
                val page = doQuery {
                    PlaylistBasic.from(it, cdnPrefix())
                }

                call.respond(page)
            } else {
                val page = doQuery(
                    Playlist
                        .joinMaps()
                        .joinOwner()
                        .joinPlaylistCurator()
                        .slice(Playlist.columns + User.columns + curatorAlias.columns + Playlist.Stats.all),
                    arrayOf(Playlist.id, User.id, curatorAlias[User.id])
                ) {
                    PlaylistFull.from(it, cdnPrefix())
                }

                call.respond(PlaylistSearchResponse(page))
            }
        }
    }
}
