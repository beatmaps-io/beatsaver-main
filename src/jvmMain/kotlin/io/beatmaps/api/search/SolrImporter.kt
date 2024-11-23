package io.beatmaps.api.search

import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.boolOr
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Collaboration
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
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
                SolrHelper.solr.deleteById(Integer.toHexString(updateMapId))
            } else {
                val version = map.versions.values.firstOrNull()
                val diffs = version?.difficulties?.values ?: emptyList()

                BsSolr.insert {
                    it[author] = map.levelAuthorName
                    it[created] = map.createdAt
                    it[description] = map.description.take(10000)
                    it[id] = updateMapId
                    it[mapId] = Integer.toHexString(updateMapId)
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
        }
    }
}
