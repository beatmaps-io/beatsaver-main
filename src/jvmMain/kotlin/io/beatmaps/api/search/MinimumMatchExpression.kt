package io.beatmaps.api.search

interface MinimumMatchExpression {
    fun toText(): String
}

data class AbsoluteMinimumMatchExpression(val limit: Int) : MinimumMatchExpression {
    override fun toText() = limit.toString()
}

data class PercentageMinimumMatchExpression(val limit: Float) : MinimumMatchExpression {
    override fun toText() = "${(limit * 100).toInt()}%"
}

data class ConditionalMinimumMatchExpression(val first: AbsoluteMinimumMatchExpression, val second: MinimumMatchExpression, val less: Boolean = true) : MinimumMatchExpression {
    override fun toText() = "${first.toText()}${if (less) "<" else ">"}${second.toText()}"
}
