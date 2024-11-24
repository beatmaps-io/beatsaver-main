package io.beatmaps.api.solr

import kotlinx.datetime.Instant
import org.apache.solr.client.solrj.SolrQuery

class SolrProduct<T : Number>(private val a: SolrFunction<T>, private val b: SolrFunction<T>) : SolrFunction<T>() {
    override fun toText() = "product(${a.toText()}, ${b.toText()})"
}

/**
 * recip(x,m,a,b) implementing a/(m*x+b)
 * where m,a,b are constants, and x is any arbitrarily complex function
 */
class SolrRecip<T : Number, U: Number, V: Number, W: Number>(private val x: SolrFunction<T>, private val m: U, private val a: V, private val b: W) : SolrFunction<Float>() {
    override fun toText() = "recip(${x.toText()},$m,$a,$b)"
}

class SolrMs(private val a: SolrFunction<Instant>? = null, private val b: SolrFunction<Instant>? = null) : SolrFunction<Long>() {
    override fun toText() = listOfNotNull(a, b).joinToString(",", "ms(", ")") { it.toText() }
}

object SolrNow : SolrFunction<Instant>() {
    override fun toText() = "NOW"
}

class SolrInstant(private val t: Instant) : SolrFunction<Instant>() {
    override fun toText() = t.toString()
}

object SolrBaseScore : SolrFunction<Float>() {
    override fun toText() = "query(\$q)"
}

abstract class SolrFunction<T> {
    abstract fun toText(): String

    private fun sort(order: SolrQuery.ORDER) = SolrQuery.SortClause(toText(), order)
    fun asc() = sort(SolrQuery.ORDER.asc)
    fun desc() = sort(SolrQuery.ORDER.desc)
}
