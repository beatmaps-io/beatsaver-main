package io.beatmaps.api.search

import io.beatmaps.api.UserStats
import io.beatmaps.common.amqp.consumeAck
import io.beatmaps.common.amqp.rabbitOptional
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.db.arrayAgg
import io.beatmaps.common.db.boolOr
import io.beatmaps.common.db.countWithFilter
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.Playlist
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.PlaylistMap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinUser
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.solr.collections.BsSolr
import io.beatmaps.common.solr.collections.PlaylistSolr
import io.beatmaps.common.solr.collections.UserSolr
import io.beatmaps.common.solr.insert
import io.beatmaps.common.solr.insertMany
import io.ktor.server.application.Application
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
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

                val fullSpreadLocal = diffs
                    .filter { diff -> diff.characteristic == ECharacteristic.Standard }
                    .map { diff -> diff.difficulty }
                    .distinct().count() == 5

                BsSolr.insert {
                    it[author] = version?.levelAuthorName
                    it[created] = map.createdAt
                    it[description] = map.description
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
                    it[rankedss] = diffs.any { d -> d.rankedAt != null }
                    it[rankedbl] = diffs.any { d -> d.blRankedAt != null }
                    it[ai] = map.declaredAi.markAsBot
                    it[mapperId] = map.uploader.id
                    it[curatorId] = map.curator?.id
                    it[tags] = map.tags
                    it[suggestions] = diffs.flatMap { d -> d.suggestions ?: listOf() }.distinct()
                    it[requirements] = diffs.flatMap { d -> d.requirements ?: listOf() }.distinct()
                    it[nps] = diffs.map { d -> d.nps.toFloat() }
                    it[fullSpread] = fullSpreadLocal
                    it[bpm] = version?.bpm
                    it[duration] = version?.duration
                    it[environment] = diffs.mapNotNull { d -> d.environment?.name }.distinct()
                    it[characteristics] = diffs.map { d -> d.characteristic.name }.distinct()
                    it[upvotes] = map.upVotesInt
                    it[downvotes] = map.downVotesInt
                    it[votes] = map.upVotesInt + map.downVotesInt
                    it[blStars] = diffs.mapNotNull { d -> d.blStars?.toFloat() }
                    it[ssStars] = diffs.mapNotNull { d -> d.stars?.toFloat() }
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
                .joinUser(Playlist.owner)
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

    private fun triggerUserInfo(updateUserId: Int) {
        transaction {
            val (user, stats) = User
                .join(Beatmap, JoinType.LEFT, User.id, Beatmap.uploader) {
                    Beatmap.deletedAt.isNull()
                }
                .join(Versions, JoinType.LEFT, onColumn = Beatmap.id, otherColumn = Versions.mapId, additionalConstraint = { Versions.state eq EMapState.Published })
                .select(
                    User.columns +
                        Beatmap.uploader +
                        Beatmap.id.count() +
                        Beatmap.upVotesInt.sum() +
                        Beatmap.downVotesInt.sum() +
                        Beatmap.bpm.avg() +
                        Beatmap.score.avg(3) +
                        Beatmap.duration.avg(0) +
                        countWithFilter(Beatmap.ranked or Beatmap.blRanked) +
                        Beatmap.uploaded.min() +
                        Beatmap.uploaded.max()
                )
                .where {
                    User.id eq updateUserId and User.active
                }
                .groupBy(Beatmap.uploader, User.id)
                .singleOrNull()
                ?.let { row ->
                    UserDao.wrapRow(row) to UserStats(
                        row[Beatmap.upVotesInt.sum()] ?: 0,
                        row[Beatmap.downVotesInt.sum()] ?: 0,
                        row[Beatmap.id.count()].toInt(),
                        row[countWithFilter(Beatmap.ranked or Beatmap.blRanked)],
                        row[Beatmap.bpm.avg()]?.toFloat() ?: 0f,
                        row[Beatmap.score.avg(3)]?.movePointRight(2)?.toFloat() ?: 0f,
                        row[Beatmap.duration.avg(0)]?.toFloat() ?: 0f,
                        row[Beatmap.uploaded.min()]?.toKotlinInstant(),
                        row[Beatmap.uploaded.max()]?.toKotlinInstant()
                    )
                } ?: (null to null)

            if (user == null || stats == null) {
                UserSolr.delete(updateUserId.toString())
            } else {
                val last = stats.lastUpload
                val first = stats.firstUpload
                val mapAgeValue = if (last != null && first != null) (last - first).inWholeDays.toInt() else null

                UserSolr.insert {
                    it[id] = user.id
                    it[sId] = user.id.toString()
                    it[name] = user.uniqueName ?: user.name
                    it[description] = user.description
                    it[created] = user.createdAt
                    it[admin] = user.admin
                    it[curator] = user.curator
                    it[seniorCurator] = user.seniorCurator
                    it[verifiedMapper] = user.verifiedMapper
                    it[avgBpm] = stats.avgBpm
                    it[avgDuration] = stats.avgDuration
                    it[totalUpvotes] = stats.totalUpvotes
                    it[totalDownvotes] = stats.totalDownvotes
                    it[avgScore] = stats.avgScore
                    it[totalMaps] = stats.totalMaps
                    it[rankedMaps] = stats.rankedMaps
                    it[firstUpload] = stats.firstUpload
                    it[lastUpload] = stats.lastUpload
                    it[mapAge] = mapAgeValue
                }
            }
        }
    }

    private fun triggerPlaylist(updatePlaylistId: Int) {
        transaction {
            val mapIdsAgg = arrayAgg(PlaylistMap.mapId)
            val (playlist, verifiedMapper, pMapIds) = Playlist
                .joinMaps()
                .joinUser(Playlist.owner)
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
                    it[description] = playlist.description
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

            consumeAck("bm.solr-user-info", Int.serializer()) { _, userId ->
                triggerUserInfo(userId)
            }

            consumeAck("bm.solr-playlist", Int.serializer()) { _, playlistId ->
                triggerPlaylist(playlistId)
            }
        }
    }
}
