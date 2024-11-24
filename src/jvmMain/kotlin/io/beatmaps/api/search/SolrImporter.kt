package io.beatmaps.api.search

import io.beatmaps.api.playlist.PlaylistSolr
import io.beatmaps.api.solr.insert
import io.beatmaps.api.solr.insertMany
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.arrayAgg
import io.beatmaps.common.db.boolOr
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinOwner
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.Integer.toHexString

object SolrImporter {
    private fun trigger(updateMapId: Int) {
        transaction {
            val map = Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .joinCollaborators()
                .selectAll()
                .where { Beatmap.id eq updateMapId }
                .complexToBeatmap()
                .firstOrNull()

            if (map == null || map.deletedAt != null) {
                BsSolr.delete(toHexString(updateMapId))
            } else {
                val version = map.versions.values.firstOrNull()
                val diffs = version?.difficulties?.values ?: emptyList()

                BsSolr.insert {
                    it[author] = map.levelAuthorName
                    it[created] = map.createdAt
                    it[description] = map.description.take(10000)
                    it[id] = updateMapId
                    it[mapId] = toHexString(updateMapId)
                    it[mapper] = map.uploader.name
                    it[mapperIds] = map.collaborators.keys.map { c -> c.value } + map.uploader.id.value
                    it[name] = map.name
                    it[updated] = map.updatedAt
                    it[curated] = map.curatedAt
                    it[uploaded] = map.uploaded
                    it[voteScore] = map.score.toFloat()
                    it[verified] = map.uploader.verifiedMapper || map.collaborators.values.any { c -> c.verifiedMapper }
                    it[rankedss] = map.ranked
                    it[rankedbl] = map.blRanked
                    it[ai] = map.declaredAi.markAsBot
                    it[mapperId] = map.uploader.id
                    it[curatorId] = map.curator?.id
                    it[tags] = map.tags
                    it[suggestions] = diffs.flatMap { d -> d.suggestions ?: listOf() }.distinct()
                    it[requirements] = diffs.flatMap { d -> d.requirements ?: listOf() }.distinct()
                    it[nps] = diffs.map { d -> d.nps.toFloat() }
                    it[fullSpread] = map.fullSpread
                    it[bpm] = map.bpm
                    it[duration] = map.duration
                }
            }
        }
    }

    private val collabAlias = Collaboration.alias("c2")
    private val boolColumn = boolOr(collaboratorAlias[User.verifiedMapper]).alias("cVerified")

    private fun triggerUser(updateUserId: Int) {
        transaction {
            // Maps
            val mapStates = Beatmap
                .joinVersions()
                .joinCollaborators()
                .selectAll()
                .where { Beatmap.uploader eq updateUserId and Beatmap.deletedAt.isNull() }
                .complexToBeatmap()
                .map { map ->
                    map.id.value to (map.uploader.verifiedMapper || map.collaborators.values.any { c -> c.verifiedMapper })
                }

            val collabStates = Collaboration
                .join(Beatmap, JoinType.INNER, Collaboration.mapId, Beatmap.id) { Beatmap.deletedAt.isNull() }
                .joinVersions()
                .joinUploader()
                .join(collabAlias, JoinType.LEFT, Collaboration.mapId, collabAlias[Collaboration.mapId]) { collabAlias[Collaboration.accepted] eq Op.TRUE }
                .join(collaboratorAlias, JoinType.LEFT, collabAlias[Collaboration.collaboratorId], collaboratorAlias[User.id])
                .select(Beatmap.id, boolColumn)
                .where { Collaboration.collaboratorId eq updateUserId and Collaboration.accepted and not(User.verifiedMapper) }
                .groupBy(Beatmap.id, User.id)
                .map {
                    it[Beatmap.id].value to it[boolColumn]
                }

            BsSolr.insertMany(mapStates + collabStates) { it, (mId, v) ->
                it[mapId] = toHexString(mId)
                it.update(verified, v)
            }

            // Playlists
            val playlistStates = Playlist
                .joinOwner()
                .select(Playlist.id, User.verifiedMapper)
                .where { Playlist.deletedAt.isNull() }
                .map {
                    it[Playlist.id].value to it[User.verifiedMapper]
                }

            PlaylistSolr.insertMany(playlistStates) { it, (pId, v) ->
                it[sId] = pId.toString()
                it.update(verified, v)
            }
        }
    }

    private fun triggerPlaylist(updatePlaylistId: Int) {
        transaction {
            val mapIdsAgg = arrayAgg(PlaylistMap.mapId)
            val (playlist, verifiedMapper, pMapIds) = Playlist
                .joinMaps()
                .joinOwner()
                .select(Playlist.columns + User.verifiedMapper + mapIdsAgg)
                .where { Playlist.id eq updatePlaylistId }
                .groupBy(Playlist.id, User.id)
                .singleOrNull()
                .let {
                    if (it == null) {
                        Triple(null, false, null)
                    } else {
                        Triple(PlaylistDao.wrapRow(it), it[User.verifiedMapper], it[mapIdsAgg])
                    }
                }

            if (playlist == null || playlist.deletedAt != null) {
                PlaylistSolr.delete(updatePlaylistId.toString())
            } else {
                PlaylistSolr.insert {
                    it[id] = updatePlaylistId
                    it[sId] = updatePlaylistId.toString()
                    it[ownerId] = playlist.ownerId.value
                    it[verified] = verifiedMapper
                    it[name] = playlist.name
                    it[description] = playlist.description.take(10000)
                    it[created] = playlist.createdAt
                    it[updated] = playlist.updatedAt
                    it[songsChanged] = playlist.songsChangedAt
                    it[curated] = playlist.curatedAt
                    it[curatorId] = playlist.curator?.id
                    it[minNps] = playlist.minNps.toFloat()
                    it[maxNps] = playlist.maxNps.toFloat()
                    it[totalMaps] = playlist.totalMaps
                    it[type] = playlist.type.name
                    it[mapIds] = pMapIds
                }
            }
        }
    }

    fun Application.solrUpdater() {
        rabbitOptional {
            consumeAck("bm.solr", Int.serializer()) { _, mapId ->
                trigger(mapId)
            }

            consumeAck("bm.solr-user", Int.serializer()) { _, userId ->
                triggerUser(userId)
            }

            consumeAck("bm.solr-playlist", Int.serializer()) { _, playlistId ->
                triggerPlaylist(playlistId)
            }
        }
    }
}
