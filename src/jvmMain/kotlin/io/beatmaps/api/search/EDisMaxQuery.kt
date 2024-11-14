package io.beatmaps.api.search

class EDisMaxQuery : DisMaxQuery() {
    init {
        set("defType", "edismax")
    }

    fun setBoost(field: SolrField<*>?) = this.also {
        set(BOOST, field?.toText())
    }

    fun setSplitOnWhitespace(split: Boolean = true) = this.also {
        set(SOW, split.toString())
    }

    fun setLowercaseOperators(lowercase: Boolean = true) = this.also {
        set(LOWERCASE_OPERATORS, lowercase.toString())
    }

    fun setUserFields(allowedFields: Array<out SolrField<*>>? = arrayOf(), disallowedFields: Array<out SolrField<*>> = arrayOf()) = this.also {
        val v = if (allowedFields?.isEmpty() == true && disallowedFields.isEmpty()) {
            "-*"
        } else {
            "${allowedFields?.joinToString(" ") { it.toText() } ?: "*"} ${disallowedFields.joinToString(" ") { it.toText() }}"
        }
        set(USER_FIELDS, v)
    }

    fun setAllowedFields(vararg allowedFields: SolrField<*>) =
        setUserFields(allowedFields)

    fun setDisallowedFields(vararg disallowedFields: SolrField<*>) =
        setUserFields(null, disallowedFields)

    companion object {
        const val BOOST = "boost"
        const val SOW = "sow"
        const val USER_FIELDS = "uf"
        const val LOWERCASE_OPERATORS = "lowercaseOperators"
    }
}
