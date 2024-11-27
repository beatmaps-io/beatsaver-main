package io.beatmaps.api.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.common.db.ilike
import io.beatmaps.common.db.similar
import io.beatmaps.common.db.unaccent
import io.beatmaps.common.db.wildcard
import io.beatmaps.common.dbo.Beatmap
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

class PgSearchParams(
    escapedQuery: String,
    private val searchIndex: ExpressionWithColumnType<String>,
    private val query: String,
    private val quotedSections: List<String>,
    mappers: List<String>
) : SearchParams(escapedQuery, mappers) {
    fun sortArgsFor(searchOrder: SearchOrder) = when (searchOrder) {
        SearchOrder.Rating -> listOf(Beatmap.score to SortOrder.DESC, Beatmap.uploaded to SortOrder.DESC)
        SearchOrder.Curated -> listOf(Beatmap.curatedAt to SortOrder.DESC_NULLS_LAST, Beatmap.uploaded to SortOrder.DESC)
        else -> listOf(Beatmap.uploaded to SortOrder.DESC)
    }.toTypedArray()

    private fun preApplyQuery(q: Op<Boolean>) =
        if (query.isBlank()) {
            q
        } else if (query.length > 3) {
            q.and(searchIndex similar unaccent(query))
        } else {
            q.and(searchIndex ilike wildcard(unaccent(query)))
        }

    fun applyQuery(q: Op<Boolean>) =
        preApplyQuery(q).let {
            quotedSections.fold(it) { p, section ->
                p.and(searchIndex ilike wildcard(unaccent(section)))
            }
        }

    companion object {
        fun parseSearchQuery(q: String, searchFields: ExpressionWithColumnType<String>): PgSearchParams {
            val originalQuery = q.replace("%", "\\%")

            return parseSearchQuery(originalQuery) { mappers, nonMappers ->
                val (quotedSections, unquoted) = nonMappers.partition { it.startsWith("\"") }
                val query = unquoted.joinToString(" ")

                PgSearchParams(originalQuery, unaccent(searchFields), query, quotedSections, mappers)
            }
        }
    }
}
