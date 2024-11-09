package io.beatmaps.api.search

import kotlinx.datetime.Instant
import org.apache.solr.client.solrj.SolrQuery

abstract class SolrCollection {
    private val _fields = mutableListOf<SolrField<*>>()

    // Built in scoring for each result
    val score = pfloat("score")

    fun string(name: String): SolrField<String> = registerField(name)
    fun strings(name: String): SolrField<List<String>> = registerField(name)
    fun pdate(name: String): SolrField<Instant> = registerField(name)
    fun pint(name: String): SolrField<Int> = registerField(name)
    fun pints(name: String): SolrField<List<Int>> = registerField(name)
    fun pfloat(name: String): SolrField<Float> = registerField(name)
    fun pfloats(name: String): SolrField<List<Float>> = registerField(name)
    fun boolean(name: String): SolrField<Boolean> = registerField(name)

    private fun <T> registerField(name: String) = SolrField<T>(this, name).also { _fields.add(it) }
}

data class SolrField<T>(private val collection: SolrCollection, val name: String) : SolrFunction<T>() {
    override fun toText() = name

    infix fun less(value: T) = lessThan(this, "$value")
    infix fun greater(value: T) = greaterThan(this, "$value")
    infix fun eq(value: T) = eq(this, "$value")
    fun any() = eq(this, "*")
}

object SolrBaseScore : SolrFunction<Float>() {
    override fun toText() = "query(\$q)"
}

class SolrProduct<T>(private val a: SolrFunction<T>, private val b: SolrFunction<T>) : SolrFunction<T>() {
    override fun toText() = "product(${a.toText()}, ${b.toText()})"
}

abstract class SolrFunction<T> {
    abstract fun toText(): String

    private fun sort(order: SolrQuery.ORDER) = SolrQuery.SortClause(toText(), order)
    fun asc() = sort(SolrQuery.ORDER.asc)
    fun desc() = sort(SolrQuery.ORDER.desc)

    infix fun product(other: SolrFunction<T>) = SolrProduct(this, other)
}

private fun lessThan(field: SolrField<*>, value: String) =
    SimpleFilter(field.name, "[* TO $value]")

private fun greaterThan(field: SolrField<*>, value: String) =
    SimpleFilter(field.name, "[$value TO *]")

private fun eq(field: SolrField<*>, value: String) =
    SimpleFilter(field.name, value, true)

infix fun <T> SolrField<List<T>>.less(value: T) = lessThan(this, "$value")
infix fun <T> SolrField<List<T>>.greater(value: T) = greaterThan(this, "$value")
infix fun <T> SolrField<List<T>>.eq(value: T) = eq(this, "$value")

interface SolrFilter {
    fun toText(): String

    infix fun and(other: SolrFilter): SolrFilter
    infix fun or(other: SolrFilter): SolrFilter
    fun not(): SolrFilter
}

data class SimpleFilter(val field: String, val value: String, val canQuote: Boolean = false) : SolrFilter {
    override fun toText() = if (canQuote && value.contains(' ')) "$field:\"$value\"" else "$field:$value"
    override fun and(other: SolrFilter) = CompoundFilter("${toText()} AND ${other.toText()}")
    override fun or(other: SolrFilter) = CompoundFilter("${toText()} OR ${other.toText()}")
    override fun not() = CompoundFilter("NOT ${toText()}")
}

data class CompoundFilter(val filter: String) : SolrFilter {
    override fun toText() = filter
    override fun and(other: SolrFilter) = CompoundFilter("$filter AND ${other.toText()}")
    override fun or(other: SolrFilter) = CompoundFilter("$filter OR ${other.toText()}")
    override fun not() = CompoundFilter("NOT ($filter)")
}

fun SolrQuery.apply(filter: SolrFilter): SolrQuery =
    addFilterQuery(filter.toText())
