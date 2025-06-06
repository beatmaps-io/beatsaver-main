package io.beatmaps.api.search

import io.beatmaps.api.SearchResponse
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.and
import java.lang.Integer.toHexString

abstract class SearchParams(
    private val escapedQuery: String,
    private val mappers: List<String>
) {
    val userSubQuery by lazy {
        if (mappers.isNotEmpty()) {
            User
                .select(User.id)
                .where {
                    User.uniqueName inList mappers.map { m -> m.substring(7) }
                }
        } else {
            null
        }
    }

    open fun validateSearchOrder(originalOrder: SearchOrder) =
        when (originalOrder) {
            SearchOrder.Rating, SearchOrder.Curated, SearchOrder.Random, SearchOrder.Duration -> originalOrder
            else -> SearchOrder.Latest
        }

    suspend fun checkKeySearch(call: ApplicationCall) =
        if (escapedQuery.startsWith("key:") || escapedQuery.startsWith("!bsr")) {
            val mapId = escapedQuery.substring(4).trim().toIntOrNull(16)
            Beatmap
                .select(Beatmap.id)
                .where {
                    Beatmap.id eq mapId and (Beatmap.deletedAt.isNull())
                }
                .limit(1).firstOrNull()?.let { r ->
                    call.respond(SearchResponse(redirect = toHexString(r[Beatmap.id].value)))
                    true
                }
        } else {
            null
        } ?: false

    companion object {
        private val splitPattern = Regex("([^\" ]+|\"[^\"]*\")")
        fun <T : SearchParams> parseSearchQuery(originalQuery: String, block: (List<String>, List<String>) -> T): T {
            val split = splitPattern.findAll(originalQuery).map { match -> match.groupValues[1] }.toList()
            val (mappers, nonMappers) = split.partition { s -> s.startsWith("mapper:") }

            return block(mappers, nonMappers)
        }
    }
}
