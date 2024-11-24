package io.beatmaps.api.solr

import kotlinx.datetime.Instant
import org.apache.solr.client.solrj.SolrQuery

class SolrProduct<T : Number>(private val a: NumericSolrFunction<T>, private val b: NumericSolrFunction<T>) : NumericSolrFunction<T>() {
    override fun toText() = "product(${a.toText()}, ${b.toText()})"
}

/**
 * recip(x,m,a,b) implementing a/(m*x+b)
 * where m,a,b are constants, and x is any arbitrarily complex function
 */
class SolrRecip(private val x: NumericSolrFunction<*>, private val m: Float, private val a: Float, private val b: Float) : NumericSolrFunction<Float>() {
    override fun toText() = "recip(${x.toText()},$m,$a,$b)"
}

class SolrMs(private val a: SolrFunction<Instant>? = null, private val b: SolrFunction<Instant>? = null) : NumericSolrFunction<Long>() {
    override fun toText() = listOfNotNull(a, b).joinToString(",", "ms(", ")") { it.toText() }
}

object SolrNow : SolrFunction<Instant>() {
    override fun toText() = "NOW"
}

class SolrInstant(private val t: Instant) : SolrFunction<Instant>() {
    override fun toText() = t.toString()
}

object SolrBaseScore : NumericSolrFunction<Float>() {
    override fun toText() = "query(\$q)"
}

abstract class NumericSolrFunction<T : Number> : SolrFunction<T>() {
    infix fun product(other: NumericSolrFunction<T>) = SolrProduct(this, other)
}

abstract class SolrFunction<T> {
    abstract fun toText(): String

    private fun sort(order: SolrQuery.ORDER) = SolrQuery.SortClause(toText(), order)
    fun asc() = sort(SolrQuery.ORDER.asc)
    fun desc() = sort(SolrQuery.ORDER.desc)
}
