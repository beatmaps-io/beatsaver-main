package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.cdnPrefix
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.contains
import io.beatmaps.common.db.distinctOn
import io.beatmaps.common.db.ilike
import io.beatmaps.common.db.similar
import io.beatmaps.common.db.unaccent
import io.beatmaps.common.db.unaccentLiteral
import io.beatmaps.common.db.wildcard
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.lang.Integer.toHexString

@Location("/api") class SearchApi {
    @Group("Search") @Location("/search/text/{page}")
    data class Text(
        val q: String? = "",
        @Description("Options are a little weird, I may add another enum field in future to make this clearer.\ntrue = both, false = only ai, null = no ai") val automapper: Boolean? = null,
        val minNps: Float? = null,
        val maxNps: Float? = null,
        val chroma: Boolean? = null,
        @DefaultValue("0") val page: Long = 0,
        val sortOrder: SearchOrder = SearchOrder.Relevance,
        val from: Instant? = null,
        val to: Instant? = null,
        @Ignore val api: SearchApi,
        val noodle: Boolean? = null,
        val ranked: Boolean? = null,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        val fullSpread: Boolean? = null,
        val minDuration: Int? = null,
        val maxDuration: Int? = null,
        val minRating: Float? = null,
        val maxRating: Float? = null,
        val minBpm: Float? = null,
        val maxBpm: Float? = null,
        val me: Boolean? = null,
        val cinema: Boolean? = null,
        @Description("Comma seperated tags")
        val tags: String? = null,
        @Ignore val mapper: Int? = null,
        @Ignore val curator: Int? = null
    )
}

fun <T> Op<Boolean>.notNull(b: T?, block: (T) -> Op<Boolean>) = if (b == null) this else this.and(block(b))

class SearchParams(
    val escapedQuery: String?,
    private val useLiteral: Boolean,
    private val searchIndex: ExpressionWithColumnType<String>,
    private val query: String,
    private val quotedSections: List<String>,
    private val bothWithoutQuotes: String,
    private val mappers: List<String>
) {
    val userSubQuery by lazy {
        if (mappers.isNotEmpty()) {
            User
                .slice(User.id)
                .select {
                    User.uniqueName inList mappers.map { m -> m.substring(7) }
                }
        } else {
            null
        }
    }

    val similarRank by lazy {
        CustomFunction<String>(
            "substring_similarity",
            searchIndex.columnType,
            (if (useLiteral) ::unaccentLiteral else ::unaccent).invoke(bothWithoutQuotes),
            searchIndex
        )
    }

    fun validateSearchOrder(originalOrder: SearchOrder) =
        when {
            escapedQuery != null && bothWithoutQuotes.replace(" ", "").length > 3 && originalOrder == SearchOrder.Relevance ->
                SearchOrder.Relevance
            originalOrder == SearchOrder.Rating -> SearchOrder.Rating
            originalOrder == SearchOrder.Curated -> SearchOrder.Curated
            else -> SearchOrder.Latest
        }

    private fun preApplyQuery(q: Op<Boolean>) =
        if (query.isBlank()) {
            q
        } else if (query.length > 3) {
            q.and(searchIndex similar unaccent(query))
        } else {
            q.and(searchIndex ilike wildcard(unaccent(query)))
        }

    fun applyQuery(q: Op<Boolean>) =
        preApplyQuery(q).let {
            quotedSections.fold(it) { p, section ->
                p.and(searchIndex ilike wildcard(unaccent(section)))
            }
        }
}
private val quotedPattern = Regex("\"([^\"]*)\"")
fun parseSearchQuery(q: String?, searchFields: ExpressionWithColumnType<String>, useLiteral: Boolean = false): SearchParams {
    val originalQuery = q?.replace("%", "\\%")

    val matches = quotedPattern.findAll(originalQuery ?: "")
    val quotedSections = matches.map { match -> match.groupValues[1] }.toList()
    val withoutQuotedSections = quotedPattern.split(originalQuery ?: "").filter { s -> s.isNotBlank() }.joinToString(" ") { s -> s.trim() }

    val (mappers, nonmappers) = withoutQuotedSections.split(' ').partition { s -> s.startsWith("mapper:") }
    val query = nonmappers.joinToString(" ")
    val bothWithoutQuotes = (nonmappers + quotedSections).joinToString(" ")

    return SearchParams(originalQuery, useLiteral, unaccent(searchFields), query, quotedSections, bothWithoutQuotes, mappers)
}

fun Route.searchRoute() {
    options<SearchApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    get<SearchApi.Text>("Search for maps".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val needsDiff = it.minNps != null || it.maxNps != null
        val searchFields = PgConcat(" ", Beatmap.name, Beatmap.description, Beatmap.levelAuthorName)
        val searchInfo = parseSearchQuery(it.q, searchFields, needsDiff)
        val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)
        val sortArgs = when (actualSortOrder) {
            SearchOrder.Relevance -> listOf(searchInfo.similarRank to SortOrder.DESC, Beatmap.score to SortOrder.DESC, Beatmap.uploaded to SortOrder.DESC)
            SearchOrder.Rating -> listOf(Beatmap.score to SortOrder.DESC, Beatmap.uploaded to SortOrder.DESC)
            SearchOrder.Latest -> listOf(Beatmap.uploaded to SortOrder.DESC)
            SearchOrder.Curated -> listOf(Beatmap.curatedAt to SortOrder.DESC_NULLS_LAST, Beatmap.uploaded to SortOrder.DESC)
        }.toTypedArray()

        newSuspendedTransaction {
            if (searchInfo.escapedQuery != null && searchInfo.escapedQuery.startsWith("key:")) {
                Beatmap
                    .slice(Beatmap.id)
                    .select {
                        Beatmap.id eq searchInfo.escapedQuery.substring(4).toInt(16) and (Beatmap.deletedAt.isNull())
                    }
                    .limit(1).firstOrNull()?.let { r ->
                        call.respond(SearchResponse(redirect = toHexString(r[Beatmap.id].value)))
                        return@newSuspendedTransaction
                    }
            }

            val beatmaps = Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .slice((if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) + Beatmap.columns + Versions.columns + Difficulty.columns + User.columns + curatorAlias.columns)
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .joinVersions(needsDiff)
                            .slice(
                                if (needsDiff) {
                                    Beatmap.id.distinctOn(
                                        Beatmap.id,
                                        *sortArgs.map { arg -> arg.first }.toTypedArray()
                                    )
                                } else {
                                    Beatmap.id
                                }
                            )
                            .select {
                                Beatmap.deletedAt.isNull()
                                    .let { q -> searchInfo.applyQuery(q) }
                                    .let { q ->
                                        // Doesn't quite make sense but we want to exclude beat sage by default
                                        when (it.automapper) {
                                            true -> q
                                            false -> q.and(Beatmap.automapper eq true)
                                            null -> q.and(Beatmap.automapper eq false)
                                        }
                                    }
                                    .notNull(searchInfo.userSubQuery) { o -> Beatmap.uploader inSubQuery o }
                                    .notNull(it.chroma) { o -> Beatmap.chroma eq o }
                                    .notNull(it.noodle) { o -> Beatmap.noodle eq o }
                                    .notNull(it.ranked) { o -> Beatmap.ranked eq o }
                                    .notNull(it.curated) { o -> with(Beatmap.curatedAt) { if (o) isNotNull() else isNull() } }
                                    .notNull(it.verified) { o -> User.verifiedMapper eq o }
                                    .notNull(it.fullSpread) { o -> Beatmap.fullSpread eq o }
                                    .notNull(it.minNps) { o -> (Beatmap.maxNps greaterEq o) and (Difficulty.nps greaterEq o) }
                                    .notNull(it.maxNps) { o -> (Beatmap.minNps lessEq o) and (Difficulty.nps lessEq o) }
                                    .notNull(it.minDuration) { o -> Beatmap.duration greaterEq o }
                                    .notNull(it.maxDuration) { o -> Beatmap.duration lessEq o }
                                    .notNull(it.minRating) { o -> Beatmap.score greaterEq o }
                                    .notNull(it.maxRating) { o -> Beatmap.score lessEq o }
                                    .notNull(it.minBpm) { o -> Beatmap.bpm greaterEq o }
                                    .notNull(it.maxBpm) { o -> Beatmap.bpm lessEq o }
                                    .notNull(it.from) { o -> Beatmap.uploaded greaterEq o.toJavaInstant() }
                                    .notNull(it.to) { o -> Beatmap.uploaded lessEq o.toJavaInstant() }
                                    .notNull(it.me) { o -> Beatmap.me eq o }
                                    .notNull(it.cinema) { o -> Beatmap.cinema eq o }
                                    .notNull(it.tags) { o -> Beatmap.tags contains o.split(",").toTypedArray() }
                                    .notNull(it.mapper) { o -> Beatmap.uploader eq o }
                                    .notNull(it.curator) { o -> Beatmap.curator eq o }
                            }
                            .orderBy(*sortArgs)
                            .limit(it.page)
                    )
                }
                .orderBy(*sortArgs)
                .complexToBeatmap()
                .map { m -> MapDetail.from(m, cdnPrefix()) }

            call.respond(SearchResponse(beatmaps))
        }
    }
}
