package io.beatmaps.api.solr

import io.beatmaps.api.SearchInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException
import org.apache.solr.client.solrj.impl.Http2SolrClient
import org.apache.solr.common.params.ModifiableSolrParams
import kotlin.math.ceil

object SolrHelper {
    private val solrHost = System.getenv("SOLR_HOST") ?: "https://solr.beatsaver.com/solr"
    private val solrUser = System.getenv("SOLR_USER") ?: "solr"
    private val solrPass = System.getenv("SOLR_PASS") ?: "insecure-password"
    val enabled = System.getenv("SOLR_ENABLED") == "true"

    val solr: Http2SolrClient by lazy {
        Http2SolrClient.Builder(solrHost)
            .withBasicAuthCredentials(solrUser, solrPass)
            .build()
    }

    val logger = KotlinLogging.logger {}

    const val msPerYear = 3.16e-11f
}

data class SolrResults(val mapIds: List<Int>, val qTime: Int, val numRecords: Int) {
    val pages = ceil(numRecords / 20f).toInt()
    val order = mapIds.mapIndexed { idx, i -> i to idx }.toMap()
    val searchInfo = SearchInfo(numRecords, pages, qTime / 1000f)

    companion object {
        val empty = SolrResults(listOf(), 0, 0)
    }
}

fun SolrQuery.all(): SolrQuery =
    setQuery("*:*")

fun SolrQuery.paged(page: Int = 0, pageSize: Int = 20): SolrQuery =
    setFields("id")
        .setStart(page * pageSize).setRows(pageSize)

fun ModifiableSolrParams.getIds(coll: SolrCollection, field: SolrField<Int>? = null) =
    try {
        val fieldName = field?.name ?: "id"
        val response = coll.query(this)

        val mapIds = response.results.mapNotNull { it[fieldName] as? Int }
        val numRecords = response.results.numFound.toInt()

        SolrResults(mapIds, response.qTime, numRecords)
    } catch (e: RemoteSolrException) {
        SolrHelper.logger.warn(e) { "Failed to perform solr query $this" }
        SolrResults.empty
    } catch (e: SolrServerException) {
        SolrHelper.logger.warn(e) { "Failed to perform solr query $this" }
        SolrResults.empty
    }
