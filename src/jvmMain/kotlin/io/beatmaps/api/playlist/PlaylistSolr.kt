package io.beatmaps.api.playlist

import io.beatmaps.api.solr.EDisMaxQuery
import io.beatmaps.api.solr.PercentageMinimumMatchExpression
import io.beatmaps.api.solr.SolrCollection
import io.beatmaps.api.solr.SolrHelper
import io.beatmaps.api.solr.SolrMs
import io.beatmaps.api.solr.SolrNow
import io.beatmaps.api.solr.SolrRecip
import io.beatmaps.api.solr.SolrScore
import io.beatmaps.common.SearchOrder
import org.apache.solr.client.solrj.SolrQuery

object PlaylistSolr : SolrCollection() {
    val id = pint("id")
    val sId = string("sId")
    val ownerId = pint("ownerId")
    val verified = boolean("verified")
    val name = string("name")
    val description = string("description")
    val created = pdate("created")
    val deleted = pdate("deleted")
    val updated = pdate("updated")
    val songsChanged = pdate("songsChanged")
    val curated = pdate("curated")
    val curatorId = pint("curatorId")
    val minNps = pfloat("minNps")
    val maxNps = pfloat("maxNps")
    val totalMaps = pint("totalMaps")
    val type = string("type")
    val mapIds = pints("mapIds")

    // Copy fields
    val nameEn = string("name_en")
    val descriptionEn = string("description_en")

    // Weights
    private val queryFields = arrayOf(
        name to 4.0,
        nameEn to 1.0,
        descriptionEn to 0.5
    )

    private val boostFunction = SolrRecip(SolrMs(SolrNow, songsChanged), SolrHelper.msPerYear, 1f, 1f)

    fun newQuery() =
        EDisMaxQuery()
            .setBoostFunction(boostFunction)
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
            SearchOrder.Rating, SearchOrder.Latest -> listOf(
                created.desc()
            )
            SearchOrder.Curated -> listOf(
                curated.desc(),
                created.desc()
            )
            SearchOrder.Random -> listOf(
                SolrQuery.SortClause("random_$seed", SolrQuery.ORDER.desc)
            )
        }.let {
            q.setSorts(it)
        }

    override val collection = System.getenv("SOLR_PLAYLIST_COLLECTION") ?: "playlists"
}
