package io.beatmaps.api.search

import io.beatmaps.common.SearchOrder
import io.beatmaps.common.solr.all
import org.apache.solr.client.solrj.SolrQuery

class SolrSearchParams(
    escapedQuery: String,
    private val query: String,
    mappers: List<String>
) : SearchParams(escapedQuery, mappers) {
    fun applyQuery(q: SolrQuery): SolrQuery =
        if (query.isBlank()) {
            q.all()
        } else {
            // Recombine string without key: or mapper:
            q.setQuery(query.replace(specialRegex, " "))
        }

    override fun validateSearchOrder(originalOrder: SearchOrder) =
        if (originalOrder == SearchOrder.Relevance && query.isNotBlank()) {
            SearchOrder.Relevance
        } else {
            super.validateSearchOrder(originalOrder)
        }

    companion object {
        val specialRegex = Regex("[&|!(){}^~*?:\\\\]")

        fun parseSearchQuery(q: String) =
            parseSearchQuery(q) { mappers, nonMappers ->
                val query = nonMappers.joinToString(" ")
                SolrSearchParams(q, query, mappers)
            }
    }
}
