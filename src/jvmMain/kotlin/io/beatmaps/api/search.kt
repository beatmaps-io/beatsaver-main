package io.beatmaps.api

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
import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

@Location("/api") class SearchApi {
    @Group("Search") @Location("/search/text/{page?}")
    data class Text(val q: String = "", val automapper: Boolean = false, val minNps: Float? = null, val maxNps: Float? = null, val chroma: Boolean = false, val page: Long = 0,
                    val sortOrder: SearchOrder = SearchOrder.Relevance, val from: Instant? = null, val to: Instant? = null, @Ignore val api: SearchApi, val noodle: Boolean = false,
                    val ranked: Boolean = false, val fullSpread: Boolean = false)
}

val beatsaverRegex = Regex("^[0-9a-f]{1,5}$")

fun Route.searchRoute() {
    options<SearchApi.Text> {
        call.response.header("Access-Control-Allow-Origin", "*")
    }

    get<SearchApi.Text>("Search for maps".responds(ok<SearchResponse>())) {
        call.response.header("Access-Control-Allow-Origin", "*")

        val searchIndex = PgConcat(" ", Beatmap.name, Beatmap.description)

        val similarRank = CustomFunction<String>("substring_similarity", searchIndex.columnType, stringLiteral(it.q), searchIndex)
        val sortByRank = it.q.isNotBlank() && it.sortOrder == SearchOrder.Relevance

        newSuspendedTransaction {
            if (beatsaverRegex.matches(it.q)) {
                Versions
                    .join(Beatmap, JoinType.INNER, Versions.mapId, Beatmap.id)
                    .slice(Versions.mapId)
                    .select {
                        Versions.key64 eq it.q and Beatmap.deletedAt.isNull()
                    }
                    .limit(1).firstOrNull()?.let { r ->
                        call.respond(SearchResponse(redirect = r[Versions.mapId].value))
                        return@newSuspendedTransaction
                    }
            }

            val user = if (it.q.isNotBlank()) {
                val userQuery = User.select {
                    User.name ilike it.q
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
                                    if (it.q.isBlank()) q else q.and(PgConcat(" ", Beatmap.name, Beatmap.description) similar it.q)
                                }.let { q ->
                                    if (it.automapper) q else q.and(Beatmap.automapper eq false)
                                }.let { q ->
                                    if (!it.chroma) q else q.and(Beatmap.chroma eq true)
                                }.let { q ->
                                    if (!it.noodle) q else q.and(Beatmap.noodle eq true)
                                }.let { q ->
                                    if (!it.ranked) q else q.and(Beatmap.ranked eq true)
                                }.let { q ->
                                    if (!it.fullSpread) q else q.and(Beatmap.fullSpread eq true)
                                }.let { q ->
                                    if (it.minNps == null) q else q.and(Beatmap.maxNps greaterEq it.minNps)
                                }.let { q ->
                                    if (it.maxNps == null) q else q.and(Beatmap.minNps lessEq it.maxNps)
                                }.let { q ->
                                    if (it.from == null) q else q.and(Beatmap.uploaded greaterEq it.from.toJavaInstant())
                                }.let { q ->
                                    if (it.to == null) q else q.and(Beatmap.uploaded lessEq it.to.toJavaInstant())
                                }
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