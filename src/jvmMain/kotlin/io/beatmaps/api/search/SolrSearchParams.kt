package io.beatmaps.api.search

import io.beatmaps.api.solr.all
import io.beatmaps.common.SearchOrder
import org.apache.solr.client.solrj.SolrQuery

class SolrSearchParams(
    escapedQuery: String,
    private val query: String,
    private val quotedSections: List<String>,
    bothWithoutQuotes: String,
    mappers: List<String>
) : SearchParams(escapedQuery, bothWithoutQuotes, mappers) {
    fun applyQuery(q: SolrQuery): SolrQuery =
        if (query.isBlank() && quotedSections.isEmpty()) {
            q.all()
        } else {
            // Recombine string without key: or mapper:
            q.setQuery("${query.replace(specialRegex, " ")} ${quotedSections.joinToString("\" \"", "\"", "\"") { it.replace(specialRegex, " ") }}")
        }

    override fun validateSearchOrder(originalOrder: SearchOrder) =
        if (originalOrder == SearchOrder.Relevance && bothWithoutQuotes.isNotBlank()) {
            SearchOrder.Relevance
        } else {
            super.validateSearchOrder(originalOrder)
        }

    companion object {
        val specialRegex = Regex("[\\+\\-\\&\\|\\!\\(\\)\\{\\}\\[\\]\\^\\\"\\~\\*\\?\\:\\\\]")

        fun parseSearchQuery(q: String) =
            parseSearchQuery(q) { originalQuery, query, quotedSections, bothWithoutQuotes, mappers ->
                SolrSearchParams(originalQuery, query, quotedSections, bothWithoutQuotes, mappers)
            }
    }
}
