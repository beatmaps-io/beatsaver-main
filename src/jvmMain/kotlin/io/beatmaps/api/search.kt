@file:UseSerializers(LenientInstantSerializer::class, OptionalPropertySerializer::class)

package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Description
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.ModelClass
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.api.search.PgSearchParams
import io.beatmaps.api.search.SolrSearchParams
import io.beatmaps.api.util.getWithOptions
import io.beatmaps.common.MapTag
import io.beatmaps.common.MapTagQuery
import io.beatmaps.common.ModChecker
import io.beatmaps.common.OptionalProperty
import io.beatmaps.common.OptionalPropertySerializer
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EBeatsaberEnvironment
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.api.RankedFilter
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
import io.beatmaps.common.or
import io.beatmaps.common.solr.SolrHelper
import io.beatmaps.common.solr.collections.BsSolr
import io.beatmaps.common.solr.field.SolrFilter
import io.beatmaps.common.solr.field.anyOf
import io.beatmaps.common.solr.field.apply
import io.beatmaps.common.solr.field.betweenNullableInc
import io.beatmaps.common.solr.field.eq
import io.beatmaps.common.solr.field.inList
import io.beatmaps.common.solr.getIds
import io.beatmaps.common.solr.paged
import io.beatmaps.common.toQuery
import io.beatmaps.common.util.LenientInstantSerializer
import io.beatmaps.common.util.applyToQuery
import io.beatmaps.common.util.paramInfo
import io.beatmaps.common.util.requireParams
import io.beatmaps.util.cdnPrefix
import io.beatmaps.util.optionalAuthorization
import io.ktor.resources.Resource
import io.ktor.server.plugins.origin
import io.ktor.server.request.queryString
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.UseSerializers
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

private val searchThreshold = (System.getenv("SEARCH_THRESHOLD")?.toIntOrNull() ?: 10).seconds
private val searchLogger = Logger.getLogger("bmio.Search")

@Resource("/api")
class SearchApi {
    @Group("Search")
    @Resource("/search/v1/{page}")
    data class Text(
        val q: String = "",
        @Description("Options are a little weird, I may add another enum field in future to make this clearer.\ntrue = both, false = only ai, null = no ai")
        val automapper: Boolean? = null,
        @ModelClass(Float::class)
        val minNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        val chroma: Boolean? = null,
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class) @Ignore
        val sortOrder: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class)
        val order: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val from: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val to: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        val noodle: Boolean? = null,
        @Ignore
        val ranked: Boolean? = null,
        @ModelClass(RankedFilter::class)
        val leaderboard: OptionalProperty<RankedFilter>? = OptionalProperty.NotPresent,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        val followed: Boolean? = null,
        val fullSpread: Boolean? = null,
        @ModelClass(Int::class)
        val minDuration: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val maxDuration: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minRating: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxRating: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minBpm: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxBpm: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        val me: Boolean? = null,
        val cinema: Boolean? = null,
        @Description("Tag query, separated by `,` (and) or `|` (or). Excluded tags are prefixed with `!`.")
        val tags: String? = null,
        @ModelClass(Int::class) @Ignore
        val mapper: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @Ignore
        val collaborator: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class) @Ignore
        val curator: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: SearchApi
    ) {
        init {
            requireParams(
                paramInfo(Text::minNps), paramInfo(Text::maxNps), paramInfo(Text::page), paramInfo(Text::sortOrder), paramInfo(Text::order), paramInfo(Text::from),
                paramInfo(Text::to), paramInfo(Text::leaderboard), paramInfo(Text::minDuration), paramInfo(Text::maxDuration), paramInfo(Text::minRating),
                paramInfo(Text::maxRating), paramInfo(Text::minBpm), paramInfo(Text::maxBpm), paramInfo(Text::mapper), paramInfo(Text::collaborator), paramInfo(Text::curator)
            )
        }
    }

    @Group("Search")
    @Resource("/search/text/{page}")
    data class Solr(
        val q: String = "",
        @Description("Options are a little weird, I may add another enum field in future to make this clearer.\ntrue = both, false = only ai, null = no ai")
        val automapper: Boolean? = null,
        @ModelClass(Float::class)
        val minNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxNps: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        val chroma: Boolean? = null,
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class) @Ignore
        val sortOrder: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(SearchOrder::class)
        val order: OptionalProperty<SearchOrder>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val from: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        @ModelClass(Instant::class)
        val to: OptionalProperty<Instant>? = OptionalProperty.NotPresent,
        val noodle: Boolean? = null,
        @Ignore
        val ranked: Boolean? = null,
        @ModelClass(RankedFilter::class)
        val leaderboard: OptionalProperty<RankedFilter>? = OptionalProperty.NotPresent,
        val curated: Boolean? = null,
        val verified: Boolean? = null,
        val followed: Boolean? = null,
        val fullSpread: Boolean? = null,
        @ModelClass(Int::class)
        val minDuration: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val maxDuration: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minRating: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxRating: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minBpm: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxBpm: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val minVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val maxVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val minUpVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val maxUpVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val minDownVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Int::class)
        val maxDownVotes: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minSsStars: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxSsStars: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val minBlStars: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @ModelClass(Float::class)
        val maxBlStars: OptionalProperty<Float>? = OptionalProperty.NotPresent,
        @Description("Comma seperated list of characteristics")
        val characteristics: String? = null,
        val me: Boolean? = null,
        val cinema: Boolean? = null,
        val vivify: Boolean? = null,
        @Description("Tag query, separated by `,` (and) or `|` (or). Excluded tags are prefixed with `!`.")
        val tags: String? = null,
        @Description("Comma seperated list of environments")
        val environments: String? = null,
        @ModelClass(Int::class) @Ignore
        val mapper: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Description("Comma seperated list of collaborators")
        val collaborator: String? = null,
        @ModelClass(Int::class) @Ignore
        val curator: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val seed: String? = null,
        @ModelClass(Int::class) @DefaultValue("20")
        val pageSize: OptionalProperty<Int>? = OptionalProperty.NotPresent,
        @Ignore
        val api: SearchApi
    ) {
        init {
            requireParams(
                paramInfo(Solr::minNps), paramInfo(Solr::maxNps), paramInfo(Solr::page), paramInfo(Solr::sortOrder), paramInfo(Solr::order), paramInfo(Solr::from),
                paramInfo(Solr::to), paramInfo(Solr::leaderboard), paramInfo(Solr::minDuration), paramInfo(Solr::maxDuration), paramInfo(Solr::minRating),
                paramInfo(Solr::maxRating), paramInfo(Solr::minBpm), paramInfo(Solr::maxBpm), paramInfo(Solr::minVotes), paramInfo(Solr::maxVotes), paramInfo(Solr::minUpVotes),
                paramInfo(Solr::maxUpVotes), paramInfo(Solr::minDownVotes), paramInfo(Solr::maxDownVotes), paramInfo(Solr::minSsStars), paramInfo(Solr::maxSsStars),
                paramInfo(Solr::minBlStars), paramInfo(Solr::maxBlStars), paramInfo(Solr::mapper), paramInfo(Solr::curator), paramInfo(Solr::pageSize)
            )
        }
    }

    @Resource("/search/v2/{page}")
    class SolrRedirect(
        @ModelClass(Long::class) @DefaultValue("0")
        val page: OptionalProperty<Long>? = OptionalProperty.NotPresent,
        @Ignore
        val api: SearchApi
    ) {
        init {
            requireParams(
                paramInfo(SolrRedirect::page)
            )
        }
    }
}

fun <T> Op<Boolean>.notNull(b: T?, block: (T) -> Op<Boolean>) = if (b == null) this else this.and(block(b))
fun <T : OptionalProperty<O>, O> Op<Boolean>.notNullOpt(b: T?, block: (O) -> Op<Boolean>) = notNull(b?.orNull(), block)
fun <T> SolrQuery.notNull(b: T?, block: (T) -> SolrFilter?): SolrQuery = if (b == null) this else apply(block(b))
fun <T : OptionalProperty<O>, O> SolrQuery.notNullOpt(b: T?, block: (O) -> SolrFilter?) = notNull(b?.orNull(), block)

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
        optionalAuthorization(OauthScope.SEARCH) { _, user ->
            val searchInfo = SolrSearchParams.parseSearchQuery(it.q)
            val actualSortOrder = searchInfo.validateSearchOrder(it.order.or(it.sortOrder.or(SearchOrder.Relevance)))

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
                    .apply(
                        when (it.automapper) {
                            true -> null
                            false -> BsSolr.ai eq true
                            null -> BsSolr.ai eq false
                        }
                    )
                    .apply(
                        it.leaderboard.or(RankedFilter.All).let { leaderboard ->
                            listOfNotNull(
                                if (leaderboard.blRanked) BsSolr.rankedbl eq true else null,
                                if (leaderboard.ssRanked) BsSolr.rankedss eq true else null
                            ).anyOf()
                        }
                    )
                    .apply(BsSolr.nps.betweenNullableInc(it.minNps?.orNull(), it.maxNps?.orNull()))
                    .apply(BsSolr.votes.betweenNullableInc(it.minVotes?.orNull(), it.maxVotes?.orNull()))
                    .apply(BsSolr.upvotes.betweenNullableInc(it.minUpVotes?.orNull(), it.maxUpVotes?.orNull()))
                    .apply(BsSolr.downvotes.betweenNullableInc(it.minDownVotes?.orNull(), it.maxDownVotes?.orNull()))
                    .apply(BsSolr.blStars.betweenNullableInc(it.minBlStars?.orNull(), it.maxBlStars?.orNull()))
                    .apply(BsSolr.ssStars.betweenNullableInc(it.minSsStars?.orNull(), it.maxSsStars?.orNull()))
                    .also { q ->
                        it.environments?.let { env ->
                            env.split(",")
                                .mapNotNull { e -> EBeatsaberEnvironment.fromString(e)?.name }
                                .let {
                                    q.apply(BsSolr.environment inList it)
                                }
                        }

                        it.characteristics?.let { char ->
                            char.split(",")
                                .mapNotNull { c -> ECharacteristic.fromNameOrNull(c)?.human() }
                                .let {
                                    q.apply(BsSolr.characteristics inList it)
                                }
                        }
                    }
                    .notNull(it.ranked) { o -> (BsSolr.rankedbl eq o) or (BsSolr.rankedss eq o) }
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
                    .notNull(it.curated) { o -> BsSolr.curated.any().let { if (o) it else it.not() } }
                    .notNull(it.verified) { o -> BsSolr.verified eq o }
                    .notNull(it.fullSpread) { o -> BsSolr.fullSpread eq o }
                    .notNullOpt(it.minRating) { o -> BsSolr.voteScore greaterEq o }
                    .notNullOpt(it.maxRating) { o -> BsSolr.voteScore lessEq o }
                    .notNullOpt(it.mapper) { o -> BsSolr.mapperId eq o }
                    .notNull(it.collaborator) { o -> BsSolr.mapperIds inList o.split(",").mapNotNull { it.toIntOrNull() } }
                    .notNullOpt(it.minBpm) { o -> BsSolr.bpm greaterEq o }
                    .notNullOpt(it.maxBpm) { o -> BsSolr.bpm lessEq o }
                    .notNullOpt(it.from) { o -> BsSolr.uploaded greaterEq o }
                    .notNullOpt(it.to) { o -> BsSolr.uploaded lessEq o }
                    .notNullOpt(it.minDuration) { o -> BsSolr.duration greaterEq o }
                    .notNullOpt(it.maxDuration) { o -> BsSolr.duration lessEq o }
                    .notNullOpt(it.curator) { o -> BsSolr.curatorId eq o }
                    .also { q ->
                        val mapperIds = followingSubQuery + (searchInfo.userSubQuery?.map { it[User.id].value } ?: listOf())
                        q.apply(BsSolr.mapperIds inList mapperIds)
                    }
                    .also { q ->
                        val mods = mapOf(
                            it.chroma to { ModChecker.chroma() },
                            it.noodle to { ModChecker.ne() },
                            it.me to { ModChecker.me() },
                            it.cinema to { ModChecker.cinema() },
                            it.vivify to { ModChecker.vivify() }
                        )
                        mods.forEach { (t, u) ->
                            q.notNull(t) { o ->
                                if (o) u() else u().not()
                            }
                        }
                    }
                    .let { q ->
                        BsSolr.addSortArgs(q, it.seed.hashCode(), actualSortOrder)
                    }
                    .paged(page = it.page.or(0).toInt(), pageSize = it.pageSize.or(20).coerceIn(1, 100))
                    .getIds(BsSolr, call = call)

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
            val searchFields = Beatmap.name
            val searchInfo = PgSearchParams.parseSearchQuery(it.q, searchFields)
            val actualSortOrder = searchInfo.validateSearchOrder(it.order.or(it.sortOrder.or(SearchOrder.Relevance)))
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
                                                    .notNullOpt(it.minNps) { o -> (Difficulty.nps greaterEqF o) }
                                                    .notNullOpt(it.maxNps) { o -> (Difficulty.nps lessEqF o) }
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
                                            .notNull(it.ranked) { o -> (Beatmap.ranked eq o) or (Beatmap.blRanked eq o) }
                                            .notNullOpt(it.leaderboard) { o ->
                                                Op.of(o == RankedFilter.All).run {
                                                    if (o.blRanked) this or Beatmap.blRanked else this
                                                }.run {
                                                    if (o.ssRanked) this or Beatmap.ranked else this
                                                }
                                            }
                                            .notNull(it.curated) { o -> with(Beatmap.curatedAt) { if (o) isNotNull() else isNull() } }
                                            .notNull(it.verified) { o -> User.verifiedMapper eq o }
                                            .notNullOpt(it.minNps) { o -> (Beatmap.maxNps greaterEqF o) }
                                            .notNullOpt(it.maxNps) { o -> (Beatmap.minNps lessEqF o) }
                                            .notNullOpt(it.minRating) { o -> Beatmap.score greaterEqF o }
                                            .notNullOpt(it.maxRating) { o -> Beatmap.score lessEqF o }
                                            .notNullOpt(it.from) { o -> Beatmap.uploaded greaterEq o.toJavaInstant() }
                                            .notNullOpt(it.to) { o -> Beatmap.uploaded lessEq o.toJavaInstant() }
                                            .notNull(it.tags) { o ->
                                                o.toQuery()?.applyToQuery() ?: Op.TRUE
                                            }
                                            .notNullOpt(it.mapper ?: it.collaborator) { o -> Beatmap.uploader eq o }
                                            .notNullOpt(it.curator) { o -> Beatmap.curator eq o }
                                    }
                                    .orderBy(*sortArgs)
                                    .limit(it.page.or(0))
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
