package io.beatmaps.api

import de.nielsfalk.ktor.swagger.DefaultValue
import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.db.PgConcat
import io.beatmaps.common.db.ilike
import io.beatmaps.common.db.similar
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Beatmap.joinCurator
import io.beatmaps.common.dbo.Beatmap.joinUploader
import io.beatmaps.common.dbo.Difficulty
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.complexToBeatmap
import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.options
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.lang.Integer.toHexString

@Location("/api") class SearchApi {
    @Group("Search") @Location("/search/text/{page}")
    data class Text(val q: String? = "", val automapper: Boolean? = null, val minNps: Float? = null, val maxNps: Float? = null, val chroma: Boolean? = null, @DefaultValue("0") val page: Long = 0,
                    val sortOrder: SearchOrder = SearchOrder.Relevance, val from: Instant? = null, val to: Instant? = null, @Ignore val api: SearchApi, val noodle: Boolean? = null,
                    val ranked: Boolean? = null, val fullSpread: Boolean? = null, val minDuration: Int? = null, val maxDuration: Int? = null, val minRating: Float? = null,
                    val maxRating: Float? = null, val minBpm: Float? = null, val maxBpm: Float? = null, val me: Boolean? = null, val cinema: Boolean? = null)
}

val beatsaverRegex = Regex("^[0-9a-f]{1,5}$")
fun <T> Op<Boolean>.notNull(b: T?, block: (T) -> Op<Boolean>) = if (b == null) this else this.and(block(b))

fun Route.searchRoute() {
    options<SearchApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    get<SearchApi.Text>("Search for maps".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val searchIndex = PgConcat(" ", Beatmap.name, Beatmap.description)

        val similarRank = CustomFunction<String>("substring_similarity", searchIndex.columnType, stringLiteral(it.q ?: ""), searchIndex)
        val sortByRank = !it.q.isNullOrBlank() && it.sortOrder == SearchOrder.Relevance

        newSuspendedTransaction {
            if (it.q != null && it.q.startsWith("key:")) {
                Beatmap
                    .slice(Beatmap.id)
                    .select {
                        Beatmap.id eq it.q.substring(4).toInt(16) and (Beatmap.deletedAt.isNull())
                    }
                    .limit(1).firstOrNull()?.let { r ->
                        call.respond(SearchResponse(redirect = toHexString(r[Beatmap.id].value)))
                        return@newSuspendedTransaction
                    }
            }

            val user = if (!it.q.isNullOrBlank()) {
                val userQuery = User.select {
                    User.name ilike (it.q)
                }.orderBy(Expression.build { User.email.isNull() }).limit(1).toList()

                userQuery.firstOrNull()?.let { u -> UserDetail.from(u) }
            } else null

            val beatmaps = Beatmap
                .joinVersions(true)
                .joinUploader()
                .joinCurator()
                .slice((if (sortByRank) listOf(similarRank) else listOf()) + Beatmap.columns + Versions.columns + Difficulty.columns + User.columns)
                .select {
                    Beatmap.id.inSubQuery(
                        Beatmap
                            .slice(Beatmap.id)
                            .select {
                                Beatmap.deletedAt.isNull().let { q ->
                                    if (it.q.isNullOrBlank()) q else q.and(PgConcat(" ", Beatmap.name, Beatmap.description) similar it.q)
                                }.let { q ->
                                    // Doesn't quite make sense but we want to exclude beat sage by default
                                    when (it.automapper) {
                                        true -> q
                                        false -> q.and(Beatmap.automapper eq true)
                                        null -> q.and(Beatmap.automapper eq false)
                                    }
                                }
                                .notNull(it.chroma) { o -> Beatmap.chroma eq o }
                                .notNull(it.noodle) { o -> Beatmap.noodle eq o }
                                .notNull(it.ranked) { o -> Beatmap.ranked eq o }
                                .notNull(it.fullSpread) { o -> Beatmap.fullSpread eq o }
                                .notNull(it.minNps) { o -> Beatmap.maxNps greaterEq o }
                                .notNull(it.maxNps) { o -> Beatmap.minNps lessEq o }
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
                            }
                            .orderBy(if (sortByRank) similarRank else if (it.sortOrder == SearchOrder.Rating) Beatmap.score else Beatmap.uploaded, SortOrder.DESC)
                            .limit(it.page)
                    )
                }.let { q ->
                    if (sortByRank) {
                        q.orderBy(similarRank, SortOrder.DESC)
                    } else if (it.sortOrder == SearchOrder.Rating) {
                        q.orderBy(Beatmap.score, SortOrder.DESC)
                    } else {
                        q
                    }
                }
                .orderBy(Beatmap.uploaded, SortOrder.DESC)
                .complexToBeatmap()
                .map { m -> MapDetail.from(m) }

            call.respond(SearchResponse(beatmaps, user = user))
        }
    }
}