package io.beatmaps.api.search

import io.beatmaps.api.solr.EDisMaxQuery
import io.beatmaps.api.solr.PercentageMinimumMatchExpression
import io.beatmaps.api.solr.SolrCollection
import io.beatmaps.api.solr.SolrScore
import io.beatmaps.common.SearchOrder
import org.apache.solr.client.solrj.SolrQuery

object BsSolr : SolrCollection() {
    val author = string("author")
    val created = pdate("created")
    val deleted = pdate("deleted")
    val description = string("description")
    val id = pint("id")
    val mapId = string("mapId")
    val mapper = string("mapper")
    val mapperIds = pints("mapperIds")
    val name = string("name")
    val updated = pdate("updated")
    val curated = pdate("curated")
    val uploaded = pdate("uploaded")
    val voteScore = pfloat("voteScore")
    val verified = boolean("verified")
    val rankedss = boolean("rankedss")
    val rankedbl = boolean("rankedbl")
    val ai = boolean("ai")
    val mapperId = pint("mapperId")
    val curatorId = pint("curatorId")
    val tags = strings("tags")
    val suggestions = strings("suggestions")
    val requirements = strings("requirements")
    val nps = pfloats("nps")
    val fullSpread = boolean("fullSpread")
    val bpm = pfloat("bpm")
    val duration = pint("duration")
    val environment = strings("environment")

    // Copy fields
    val authorEn = string("author_en")
    val nameEn = string("name_en")
    val descriptionEn = string("description_en")

    // Weights
    private val queryFields = arrayOf(
        name to 4.0,
        nameEn to 1.0,
        author to 10.0,
        authorEn to 2.0,
        descriptionEn to 0.5
    )

    fun newQuery(sortOrder: SearchOrder) =
        EDisMaxQuery()
            .setBoost(if (sortOrder == SearchOrder.Relevance) voteScore else null)
            .setQueryFields(*queryFields)
            .setTie(0.1)
            .setMinimumMatch(
                PercentageMinimumMatchExpression(-0.5f)
            )

    fun addSortArgs(q: SolrQuery, seed: Int?, searchOrder: SearchOrder): SolrQuery =
        when (searchOrder) {
            SearchOrder.Relevance -> listOf(
                SolrScore.desc()
            )
            SearchOrder.Rating -> listOf(
                voteScore.desc(),
                uploaded.desc()
            )
            SearchOrder.Latest -> listOf(
                uploaded.desc()
            )
            SearchOrder.Curated -> listOf(
                curated.desc(),
                uploaded.desc()
            )
            SearchOrder.Random -> listOf(
                SolrQuery.SortClause("random_$seed", SolrQuery.ORDER.desc)
            )
        }.let {
            q.setSorts(it)
        }

    override val collection = System.getenv("SOLR_COLLECTION") ?: "beatsaver"
}
