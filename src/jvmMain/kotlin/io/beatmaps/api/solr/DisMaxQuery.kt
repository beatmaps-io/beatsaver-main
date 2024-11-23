package io.beatmaps.api.solr

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.DisMaxParams

open class DisMaxQuery : SolrQuery() {
    init {
        set("defType", "dismax")
    }

    fun setQueryFields(vararg fields: Pair<SolrField<*>, Double>) = this.also {
        set(DisMaxParams.QF, fields.joinToString(" ") { "${it.first.toText()}^${it.second}" })
    }

    fun setTie(tie: Double) = this.also {
        set(DisMaxParams.TIE, tie.toString())
    }

    fun setMinimumMatch(vararg exp: MinimumMatchExpression) = this.also {
        set(DisMaxParams.MM, exp.joinToString(" ") { it.toText() })
    }

    fun setPhraseFields(vararg fields: Pair<SolrField<*>, Double>) = this.also {
        set(DisMaxParams.PF, fields.joinToString(" ") { "${it.first.toText()}^${it.second}" })
    }

    fun setPhraseSlop(slop: Int) = this.also {
        set(DisMaxParams.PS, slop.toString())
    }

    fun setQueryPhraseSlop(slop: Int) = this.also {
        set(DisMaxParams.QS, slop.toString())
    }
}
