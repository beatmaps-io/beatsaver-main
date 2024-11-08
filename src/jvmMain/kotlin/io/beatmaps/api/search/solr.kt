package io.beatmaps.api.search

import io.beatmaps.api.search.SolrHelper.deltaImport
import io.beatmaps.common.consumeAck
import io.beatmaps.common.rabbitHost
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import kotlinx.serialization.builtins.serializer
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException
import org.apache.solr.client.solrj.impl.Http2SolrClient
import org.apache.solr.client.solrj.request.GenericSolrRequest
import org.apache.solr.common.params.ModifiableSolrParams
import java.util.Timer
import java.util.TimerTask

object SolrHelper {
    private val solrHost = System.getenv("SOLR_HOST") ?: "https://solr.beatsaver.com/solr"
    private val solrUser = System.getenv("SOLR_USER") ?: "solr"
    private val solrPass = System.getenv("SOLR_PASS") ?: "insecure-password"
    private val solrCollection = System.getenv("SOLR_COLLECTION") ?: "beatsaver"
    val enabled = System.getenv("SOLR_ENABLED") == "false"

    val solr: Http2SolrClient by lazy {
        Http2SolrClient.Builder(solrHost)
            .withBasicAuthCredentials(solrUser, solrPass)
            .withDefaultCollection(solrCollection)
            .build()
    }

    // Would be more efficient to directly update documents from rabbit receiver but this gets us started
    fun deltaImport() =
        try {
            val req = GenericSolrRequest(SolrRequest.METHOD.GET, "/dataimport")
                .setRequiresCollection(true)
            (req.params as ModifiableSolrParams).set("command", "delta-import")
            solr.request(req)

            true
        } catch (e: RemoteSolrException) {
            false
        }

    fun Application.solrUpdater() {
        // Run every 30s
        val importer = SolrImporter()
        Timer("Solr Delta").scheduleAtFixedRate(importer, 5000L, 30 * 1000L)

        rabbitOptional {
            consumeAck("bm.solr", Int.serializer()) { _, _ ->
                importer.trigger()
            }
        }
    }
}

class SolrImporter : TimerTask() {
    var state = SolrImportState.IDLE

    fun trigger() {
        if (state == SolrImportState.IDLE) {
            execute()
        } else if (state == SolrImportState.RUNNING) {
            state = SolrImportState.QUEUED
        }
    }

    override fun run() {
        if (state == SolrImportState.QUEUED || rabbitHost.isEmpty()) {
            execute()
        } else if (state == SolrImportState.RUNNING) {
            state = SolrImportState.IDLE
        }
    }

    private fun execute() {
        state = SolrImportState.RUNNING
        deltaImport()
    }

    enum class SolrImportState {
        QUEUED, RUNNING, IDLE
    }
}
