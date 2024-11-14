package io.beatmaps.api.search

class EDisMaxQuery : DisMaxQuery() {
    init {
        set("defType", "edismax")
    }

    fun setBoost(field: SolrField<*>?) = this.also {
        set("boost", field?.toText())
    }
}
