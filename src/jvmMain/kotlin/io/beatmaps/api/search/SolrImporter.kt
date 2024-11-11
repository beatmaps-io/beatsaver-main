package io.beatmaps.api.search

import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object SolrImporter {
    suspend fun trigger(updateMapId: Int) {
        newSuspendedTransaction {
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

    fun Application.solrUpdater() {
        rabbitOptional {
            consumeAck("bm.solr", Int.serializer()) { _, mapId ->
                trigger(mapId)
            }
        }
    }
}
