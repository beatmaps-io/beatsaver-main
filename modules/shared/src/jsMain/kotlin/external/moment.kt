package external

@JsModule("moment")
@JsNonModule
external class Moment {
    constructor()
    constructor(d: String)
    fun add(v: Int, unit: String): Moment
    fun subtract(v: Int, unit: String): Moment
    fun format(format: String): String
    fun locale(l: String): Unit
    fun toISOString(): String
}
