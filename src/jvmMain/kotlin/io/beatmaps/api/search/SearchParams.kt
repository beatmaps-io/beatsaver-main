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
    val escapedQuery: String,
    protected val bothWithoutQuotes: String,
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
            SearchOrder.Rating -> SearchOrder.Rating
            SearchOrder.Curated -> SearchOrder.Curated
            SearchOrder.Random -> SearchOrder.Random
            else -> SearchOrder.Latest
        }

    suspend fun checkKeySearch(call: ApplicationCall) =
        if (escapedQuery.startsWith("key:")) {
            Beatmap
                .select(Beatmap.id)
                .where {
                    Beatmap.id eq escapedQuery.substring(4).toInt(16) and (Beatmap.deletedAt.isNull())
                }
                .limit(1).firstOrNull()?.let { r ->
                    call.respond(SearchResponse(redirect = toHexString(r[Beatmap.id].value)))
                    true
                }
        } else {
            null
        } ?: false

    companion object {
        private val quotedPattern = Regex("\"([^\"]*)\"")
        fun <T : SearchParams> parseSearchQuery(q: String, block: (String, String, List<String>, String, List<String>) -> T): T {
            val originalQuery = q.replace("%", "\\%")

            val matches = quotedPattern.findAll(originalQuery)
            val quotedSections = matches.map { match -> match.groupValues[1] }.toList()
            val withoutQuotedSections = quotedPattern.split(originalQuery).filter { s -> s.isNotBlank() }.joinToString(" ") { s -> s.trim() }

            val (mappers, nonmappers) = withoutQuotedSections.split(' ').partition { s -> s.startsWith("mapper:") }
            val query = nonmappers.joinToString(" ")
            val bothWithoutQuotes = (nonmappers + quotedSections).joinToString(" ")

            return block(originalQuery, query, quotedSections, bothWithoutQuotes, mappers)
        }
    }
}
