package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.applyToQuery
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.greaterEqF
import io.beatmaps.common.db.ilike
import io.beatmaps.common.db.lateral
import io.beatmaps.common.db.lessEqF
import io.beatmaps.common.db.similar
import io.beatmaps.common.db.unaccent
import io.beatmaps.common.db.unaccentLiteral
import io.beatmaps.common.db.wildcard
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.Follows
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.bookmark
import io.beatmaps.common.dbo.collaboratorAlias
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.curatorAlias
import io.beatmaps.common.dbo.joinBookmarked
import io.beatmaps.common.dbo.joinCollaborators
import io.beatmaps.common.dbo.joinCurator
import io.beatmaps.common.dbo.joinUploader
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.toQuery
import io.beatmaps.login.Session
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.lang.Integer.toHexString
import java.util.logging.Logger
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

private val searchThreshold = (System.getenv("SEARCH_THRESHOLD")?.toIntOrNull() ?: 10).toDuration(DurationUnit.SECONDS)
private val searchLogger = Logger.getLogger("bmio.Search")

@Location("/api")
class SearchApi {
    @Group("Search")
    @Location("/search/text/{page}")
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
        val followed: Boolean? = null,
        val fullSpread: Boolean? = null,
        val minDuration: Int? = null,
        val maxDuration: Int? = null,
        val minRating: Float? = null,
        val maxRating: Float? = null,
        val minBpm: Float? = null,
        val maxBpm: Float? = null,
        val me: Boolean? = null,
        val cinema: Boolean? = null,
        @Description("Tag query, separated by `,` (and) or `|` (or). Excluded tags are prefixed with `!`.")
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
                .select(User.id)
                .where {
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

    fun sortArgsFor(searchOrder: SearchOrder) = when (searchOrder) {
        SearchOrder.Relevance -> listOf(similarRank to SortOrder.DESC, Beatmap.score to SortOrder.DESC, Beatmap.uploaded to SortOrder.DESC)
        SearchOrder.Rating -> listOf(Beatmap.score to SortOrder.DESC, Beatmap.uploaded to SortOrder.DESC)
        SearchOrder.Latest -> listOf(Beatmap.uploaded to SortOrder.DESC)
        SearchOrder.Curated -> listOf(Beatmap.curatedAt to SortOrder.DESC_NULLS_LAST, Beatmap.uploaded to SortOrder.DESC)
    }.toTypedArray()

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
        optionalAuthorization { _, user ->
            call.response.header("Access-Control-Allow-Origin", "*")
            val sess = call.sessions.get<Session>()

            val needsDiff = it.minNps != null || it.maxNps != null
            val searchFields = PgConcat(" ", Beatmap.name, Beatmap.description, Beatmap.levelAuthorName)
            val searchInfo = parseSearchQuery(it.q, searchFields, needsDiff)
            val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)
            val sortArgs = searchInfo.sortArgsFor(actualSortOrder)

            newSuspendedTransaction {
                val followingSubQuery = if (user != null && it.followed == true) {
                    Follows
                        .select(Follows.userId)
                        .where { Follows.followerId eq user.userId }
                } else {
                    null
                }

                if (searchInfo.escapedQuery != null && searchInfo.escapedQuery.startsWith("key:")) {
                    Beatmap
                        .select(Beatmap.id)
                        .where {
                            Beatmap.id eq searchInfo.escapedQuery.substring(4).toInt(16) and (Beatmap.deletedAt.isNull())
                        }
                        .limit(1).firstOrNull()?.let { r ->
                            call.respond(SearchResponse(redirect = toHexString(r[Beatmap.id].value)))
                            return@newSuspendedTransaction
                        }
                }

                val time = measureTime {
                    val beatmaps = Beatmap
                        .joinVersions(true)
                        .joinUploader()
                        .joinCurator()
                        .joinBookmarked(sess?.userId)
                        .joinCollaborators()
                        .select(
                            (if (actualSortOrder == SearchOrder.Relevance) listOf(searchInfo.similarRank) else listOf()) +
                                Beatmap.columns + Versions.columns + Difficulty.columns + User.columns +
                                curatorAlias.columns + bookmark.columns + collaboratorAlias.columns
                        )
                        .where {
                            Beatmap.id.inSubQuery(
                                Beatmap
                                    .joinUploader()
                                    .crossJoin(
                                        Versions
                                            .let { q ->
                                                if (needsDiff) q.join(Difficulty, JoinType.INNER, Versions.id, Difficulty.versionId) else q
                                            }
                                            .select(intLiteral(1))
                                            .where {
                                                EqOp(Versions.mapId, Beatmap.id) and (Versions.state eq EMapState.Published)
                                                    .notNull(it.minNps) { o -> (Difficulty.nps greaterEqF o) }
                                                    .notNull(it.maxNps) { o -> (Difficulty.nps lessEqF o) }
                                            }
                                            .limit(1)
                                            .lateral().alias("diff")
                                    )
                                    .select(Beatmap.id)
                                    .where {
                                        Beatmap.deletedAt.isNull()
                                            .let { q -> searchInfo.applyQuery(q) }
                                            .let { q ->
                                                // Doesn't quite make sense but we want to exclude beat sage by default
                                                when (it.automapper) {
                                                    true -> q
                                                    false -> q.and(Beatmap.declaredAi neq AiDeclarationType.None)
                                                    null -> q.and(Beatmap.declaredAi eq AiDeclarationType.None)
                                                }
                                            }
                                            .notNull(searchInfo.userSubQuery) { o -> Beatmap.uploader inSubQuery o }
                                            .notNull(followingSubQuery) { o -> Beatmap.uploader inSubQuery o }
                                            .notNull(it.chroma) { o -> Beatmap.chroma eq o }
                                            .notNull(it.noodle) { o -> Beatmap.noodle eq o }
                                            .notNull(it.ranked) { o -> (Beatmap.ranked eq o) or (Beatmap.blRanked eq o) }
                                            .notNull(it.curated) { o -> with(Beatmap.curatedAt) { if (o) isNotNull() else isNull() } }
                                            .notNull(it.verified) { o -> User.verifiedMapper eq o }
                                            .notNull(it.fullSpread) { o -> Beatmap.fullSpread eq o }
                                            .notNull(it.minNps) { o -> (Beatmap.maxNps greaterEqF o) }
                                            .notNull(it.maxNps) { o -> (Beatmap.minNps lessEqF o) }
                                            .notNull(it.minDuration) { o -> Beatmap.duration greaterEq o }
                                            .notNull(it.maxDuration) { o -> Beatmap.duration lessEq o }
                                            .notNull(it.minRating) { o -> Beatmap.score greaterEqF o }
                                            .notNull(it.maxRating) { o -> Beatmap.score lessEqF o }
                                            .notNull(it.minBpm) { o -> Beatmap.bpm greaterEq o }
                                            .notNull(it.maxBpm) { o -> Beatmap.bpm lessEq o }
                                            .notNull(it.from) { o -> Beatmap.uploaded greaterEq o.toJavaInstant() }
                                            .notNull(it.to) { o -> Beatmap.uploaded lessEq o.toJavaInstant() }
                                            .notNull(it.me) { o -> Beatmap.me eq o }
                                            .notNull(it.cinema) { o -> Beatmap.cinema eq o }
                                            .notNull(it.tags) { o ->
                                                o.toQuery()?.applyToQuery() ?: Op.TRUE
                                            }
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

                if (time > searchThreshold) {
                    searchLogger.info("Search took longer than $searchThreshold ($time)\n$sess\n$it")
                }
            }
        }
    }
}
