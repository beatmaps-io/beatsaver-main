package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.search.PgSearchParams
import io.beatmaps.api.search.SolrSearchParams
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagQuery
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EBeatsaberEnvironment
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.api.searchEnumOrNull
import io.beatmaps.common.applyToQuery
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.greaterEqF
import io.beatmaps.common.db.lateral
import io.beatmaps.common.db.lessEqF
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
import io.beatmaps.common.solr.SolrHelper
import io.beatmaps.common.solr.collections.BsSolr
import io.beatmaps.common.solr.field.SolrFilter
import io.beatmaps.common.solr.field.apply
import io.beatmaps.common.solr.field.betweenInc
import io.beatmaps.common.solr.field.eq
import io.beatmaps.common.solr.field.greaterEq
import io.beatmaps.common.solr.field.lessEq
import io.beatmaps.common.solr.getIds
import io.beatmaps.common.solr.paged
import io.beatmaps.common.toQuery
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.plugins.origin
import io.ktor.server.request.queryString
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.apache.solr.client.solrj.SolrQuery
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.logging.Logger
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.toDuration

private val searchThreshold = (System.getenv("SEARCH_THRESHOLD")?.toIntOrNull() ?: 10).toDuration(DurationUnit.SECONDS)
private val searchLogger = Logger.getLogger("bmio.Search")

@Location("/api")
class SearchApi {
    @Group("Search")
    @Location("/search/v1/{page}")
    data class Text(
        val q: String = "",
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
        @Ignore val ranked: Boolean? = null,
        val leaderboard: RankedFilter = RankedFilter.All,
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

    @Group("Search")
    @Location("/search/text/{page}")
    data class Solr(
        val q: String = "",
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
        @Ignore val ranked: Boolean? = null,
        val leaderboard: RankedFilter = RankedFilter.All,
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
        @Description("Comma seperated list of environments")
        val environments: String? = null,
        @Ignore val mapper: Int? = null,
        @Ignore val collaborator: Int? = null,
        @Ignore val curator: Int? = null,
        @Ignore val seed: String? = null
    )

    @Location("/search/v2/{page}")
    class SolrRedirect(@DefaultValue("0") val page: Long = 0, @Ignore val api: SearchApi)
}

fun <T> Op<Boolean>.notNull(b: T?, block: (T) -> Op<Boolean>) = if (b == null) this else this.and(block(b))
fun <T> SolrQuery.notNull(b: T?, block: (T) -> SolrFilter): SolrQuery = if (b == null) this else apply(block(b))

fun Op.Companion.of(b: Boolean): Op<Boolean> = if (b) Op.TRUE else Op.FALSE

fun MapTagQuery.applyToQuery(q: SolrQuery) =
    flatMap { x ->
        x.groupBy { it.first }.mapNotNull { y ->
            y.value
                .filterNot { t -> t.second == MapTag.None }
                .map { t -> (BsSolr.tags eq t.second.slug).let { if (y.key) it else it.not() } }
                .reduceOrNull { a, b ->
                    if (y.key) a or b else a and b
                }
        }
    }.fold(q) { query, it ->
        query.apply(it)
    }

fun Route.searchRoute() {
    getWithOptions<SearchApi.SolrRedirect> {
        val params = call.request.queryString()
        call.respondRedirect(
            "/api/search/${if (SolrHelper.enabled) "text" else "v1"}/${it.page}${if (params.isNotEmpty()) "?" else ""}$params"
        )
    }

    getWithOptions<SearchApi.Solr>("Search for maps with solr".responds(ok<SearchResponse>())) {
        optionalAuthorization { _, user ->
            val searchInfo = SolrSearchParams.parseSearchQuery(it.q)
            val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)

            newSuspendedTransaction {
                if (searchInfo.checkKeySearch(call)) return@newSuspendedTransaction

                val followingSubQuery = if (user != null && it.followed == true) {
                    Follows
                        .select(Follows.userId)
                        .where { Follows.followerId eq user.userId and Follows.following }
                        .map { it[Follows.userId].value }
                } else {
                    listOf()
                }

                val results = BsSolr.newQuery(actualSortOrder)
                    .let { q ->
                        searchInfo.applyQuery(q)
                    }
                    .also { q ->
                        when (it.automapper) {
                            true -> null
                            false -> BsSolr.ai eq true
                            null -> BsSolr.ai eq false
                        }?.let { filter ->
                            q.apply(filter)
                        }
                    }
                    .also { q ->
                        listOfNotNull(
                            if (it.leaderboard.blRanked) BsSolr.rankedbl eq true else null,
                            if (it.leaderboard.ssRanked) BsSolr.rankedss eq true else null
                        ).reduceOrNull<SolrFilter, SolrFilter> { a, b -> a or b }?.let {
                            q.apply(it)
                        }
                    }
                    .notNull(it.ranked) { o -> (BsSolr.rankedbl eq o) or (BsSolr.rankedss eq o) }
                    .also { q ->
                        val f = if (it.minNps != null && it.maxNps != null) {
                            BsSolr.nps.betweenInc(it.minNps, it.maxNps)
                        } else if (it.minNps != null) {
                            BsSolr.nps greaterEq it.minNps
                        } else if (it.maxNps != null) {
                            BsSolr.nps lessEq it.maxNps
                        } else {
                            null
                        }

                        f?.let { q.apply(it) }
                    }
                    .let { q ->
                        val tq = it.tags?.toQuery()
                        val emptyTags = tq?.any { a ->
                            a.any { b ->
                                b.second == MapTag.None
                            }
                        } == true

                        if (emptyTags) {
                            searchLogger.warning("Query contained empty tag (${it.tags}) [${call.request.origin.remoteAddress}] (${call.request.userAgent()})")
                        }

                        tq?.applyToQuery(q) ?: q
                    }
                    .also { q ->
                        it.environments?.let { env ->
                            env.split(",")
                                .mapNotNull { e -> searchEnumOrNull<EBeatsaberEnvironment>(e) }
                                .map { e -> BsSolr.environment eq e.name }
                                .reduceOrNull<SolrFilter, SolrFilter> { a, b -> a or b }
                                ?.let { q.apply(it) }
                        }
                    }
                    .notNull(it.curated) { o -> BsSolr.curated.any().let { if (o) it else it.not() } }
                    .notNull(it.verified) { o -> BsSolr.verified eq o }
                    .notNull(it.fullSpread) { o -> BsSolr.fullSpread eq o }
                    .notNull(it.minRating) { o -> BsSolr.voteScore greaterEq o }
                    .notNull(it.maxRating) { o -> BsSolr.voteScore lessEq o }
                    .notNull(it.mapper) { o -> BsSolr.mapperId eq o }
                    .notNull(it.collaborator) { o -> BsSolr.mapperIds eq o }
                    .notNull(it.minBpm) { o -> BsSolr.bpm greaterEq o }
                    .notNull(it.maxBpm) { o -> BsSolr.bpm lessEq o }
                    .notNull(it.from) { o -> BsSolr.uploaded greaterEq o }
                    .notNull(it.to) { o -> BsSolr.uploaded lessEq o }
                    .notNull(it.minDuration) { o -> BsSolr.duration greaterEq o }
                    .notNull(it.maxDuration) { o -> BsSolr.duration lessEq o }
                    .notNull(it.curator) { o -> BsSolr.curatorId eq o }
                    .also { q ->
                        val mapperIds = followingSubQuery + (searchInfo.userSubQuery?.map { it[User.id].value } ?: listOf())

                        mapperIds.map { id ->
                            BsSolr.mapperIds eq id
                        }.reduceOrNull<SolrFilter, SolrFilter> { a, b -> a or b }?.let {
                            q.apply(it)
                        }
                    }
                    .notNull(it.chroma) { o ->
                        val chromaQuery = (BsSolr.suggestions eq "Chroma") or (BsSolr.requirements eq "Chroma")
                        if (o) chromaQuery else chromaQuery.not()
                    }
                    .notNull(it.noodle) { o ->
                        val noodleQuery = BsSolr.requirements eq "Noodle Extensions"
                        if (o) noodleQuery else noodleQuery.not()
                    }
                    .notNull(it.me) { o ->
                        val meQuery = BsSolr.requirements eq "Mapping Extensions"
                        if (o) meQuery else meQuery.not()
                    }
                    .notNull(it.cinema) { o ->
                        val cinemaQuery = (BsSolr.suggestions eq "Cinema") or (BsSolr.requirements eq "Cinema")
                        if (o) cinemaQuery else cinemaQuery.not()
                    }
                    .let { q ->
                        BsSolr.addSortArgs(q, it.seed.hashCode(), actualSortOrder)
                    }
                    .paged(page = it.page.toInt())
                    .getIds(BsSolr)

                val beatmaps = Beatmap
                    .joinVersions(true)
                    .joinUploader()
                    .joinCurator()
                    .joinBookmarked(user?.userId)
                    .joinCollaborators()
                    .select(
                        Beatmap.columns + Versions.columns + Difficulty.columns + User.columns +
                            curatorAlias.columns + bookmark.columns + collaboratorAlias.columns
                    )
                    .where {
                        Beatmap.id.inList(results.mapIds)
                    }
                    .complexToBeatmap()
                    .sortedBy { results.order[it.id.value] } // Match order from solr
                    .map { m -> MapDetail.from(m, cdnPrefix()) }
                call.respond(SearchResponse(beatmaps, results.searchInfo))
            }
        }
    }

    getWithOptions<SearchApi.Text>("Search for maps".responds(ok<SearchResponse>())) {
        optionalAuthorization { _, user ->
            val needsDiff = it.minNps != null || it.maxNps != null
            val searchFields = PgConcat(" ", Beatmap.name, Beatmap.description, Beatmap.levelAuthorName)
            val searchInfo = PgSearchParams.parseSearchQuery(it.q, searchFields)
            val actualSortOrder = searchInfo.validateSearchOrder(it.sortOrder)
            val sortArgs = searchInfo.sortArgsFor(actualSortOrder)

            newSuspendedTransaction {
                val followingSubQuery = if (user != null && it.followed == true) {
                    Follows
                        .select(Follows.userId)
                        .where { Follows.followerId eq user.userId and Follows.following }
                } else {
                    null
                }

                if (searchInfo.checkKeySearch(call)) return@newSuspendedTransaction

                val time = measureTime {
                    val beatmaps = Beatmap
                        .joinVersions(true)
                        .joinUploader()
                        .joinCurator()
                        .joinBookmarked(user?.userId)
                        .joinCollaborators()
                        .select(
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
                                            .notNull(it.leaderboard) { o ->
                                                Op.of(o == RankedFilter.All).run {
                                                    if (o.blRanked) this or Beatmap.blRanked else this
                                                }.run {
                                                    if (o.ssRanked) this or Beatmap.ranked else this
                                                }
                                            }
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
                    searchLogger.info("Search took longer than $searchThreshold ($time)\n$user\n$it")
                }
            }
        }
    }
}
