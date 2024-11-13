package io.beatmaps.api.search

import io.beatmaps.api.SearchInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException
import org.apache.solr.client.solrj.impl.Http2SolrClient
import kotlin.math.ceil

object SolrHelper {
    private val solrHost = System.getenv("SOLR_HOST") ?: "https://solr.beatsaver.com/solr"
    private val solrUser = System.getenv("SOLR_USER") ?: "solr"
    private val solrPass = System.getenv("SOLR_PASS") ?: "insecure-password"
    private val solrCollection = System.getenv("SOLR_COLLECTION") ?: "beatsaver"
    val enabled = System.getenv("SOLR_ENABLED") == "true"

    val solr: Http2SolrClient by lazy {
        Http2SolrClient.Builder(solrHost)
            .withBasicAuthCredentials(solrUser, solrPass)
            .withDefaultCollection(solrCollection)
            .build()
    }

    val logger = KotlinLogging.logger {}
}

val emptyResults = SolrResults(listOf(), 0, 0)
data class SolrResults(val mapIds: List<Int>, val qTime: Int, val numRecords: Int) {
    val pages = ceil(numRecords / 20f).toInt()
    val order = mapIds.mapIndexed { idx, i -> i to idx }.toMap()
    val searchInfo = SearchInfo(numRecords, pages, qTime / 1000f)
}

fun SolrQuery.all(): SolrQuery =
    this.setQuery("*:*")

fun SolrQuery.getMapIds(page: Int = 0, pageSize: Int = 20, bf: String = "") =
    try {
        val response = SolrHelper.solr.query(
            this
                .setFields("id")
                .setStart(page * pageSize).setRows(pageSize)
                .set("defType", "edismax")
                .set("qf", "name_en^3 author^10 description_en^0.5")
                .set("boost", bf)
        )

        val mapIds = response.results.mapNotNull { it["id"] as? Int }
        val numRecords = response.results.numFound.toInt()

        SolrResults(mapIds, response.qTime, numRecords)
    } catch (e: RemoteSolrException) {
        SolrHelper.logger.warn(e) { "Failed to perform solr query $this" }
        emptyResults
    }
