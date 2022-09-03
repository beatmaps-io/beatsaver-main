package io.beatmaps.api

import de.nielsfalk.ktor.swagger.Ignore
import de.nielsfalk.ktor.swagger.get
import de.nielsfalk.ktor.swagger.notFound
import de.nielsfalk.ktor.swagger.ok
import de.nielsfalk.ktor.swagger.post
import de.nielsfalk.ktor.swagger.responds
import de.nielsfalk.ktor.swagger.version.shared.Group
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.NowExpression
import io.beatmaps.common.db.updateReturning
import io.beatmaps.common.db.upsert
import io.beatmaps.common.db.wrapAsExpressionNotNull
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.Votes
import io.beatmaps.common.dbo.complexToBeatmap
import io.beatmaps.common.dbo.joinVersions
import io.beatmaps.common.pub
import io.beatmaps.common.rabbitOptional
import io.beatmaps.common.tag
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.options
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.SqlExpressionBuilder.coalesce
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import pl.jutupe.ktor_rabbitmq.publish
import java.lang.Integer.toHexString
import kotlin.math.log10
import kotlin.math.pow

@Location("/api") class VoteApi {
    @Group("Vote") @Location("/vote") data class Vote(@Ignore val api: VoteApi)
    @Group("Vote") @Location("/vote") data class Since(val since: Instant, @Ignore val api: VoteApi)
}

@Serializable
data class VoteRequest(val auth: AuthRequest, val hash: String, val direction: Boolean)
@Serializable
data class VoteResponse(val success: Boolean, val error: String? = null)
data class QueuedVote(val userId: Long, val steam: Boolean, val mapId: Int, val direction: Boolean)

@Serializable
data class VoteSummary(val hash: String?, val mapId: Int, val key64: String?, val upvotes: Int, val downvotes: Int, val score: Double)

fun Route.voteRoute() {
    options<VoteApi.Vote> {
        call.response.header("Access-Control-Allow-Origin", "*")
        call.respond(HttpStatusCode.OK)
    }

    application.rabbitOptional {
        consumeAck("vote", QueuedVote::class) { _, body ->
            transaction {
                Votes.upsert(conflictIndex = Index(listOf(Votes.mapId, Votes.userId, Votes.steam), true, "vote_unique")) {
                    it[mapId] = body.mapId
                    it[userId] = body.userId
                    it[vote] = body.direction
                    it[updatedAt] = NowExpression(updatedAt.columnType)
                    it[steam] = body.steam
                }

                val voteTotals = Votes.slice(Count(Votes.vote), Votes.vote).select {
                    Votes.mapId eq body.mapId
                }.groupBy(Votes.vote).toList().associateBy({ it[Votes.vote] }, { it[Count(Votes.vote)] })

                val upVotes = (voteTotals[true] ?: 0)
                val downVotes = (voteTotals[false] ?: 0)
                val totalVotes = (upVotes + downVotes).toDouble()
                val rawScore = upVotes / totalVotes
                val scoreWeighted = rawScore - (rawScore - 0.5) * 2.0.pow(-log10(totalVotes + 1))

                var uploader: Int? = null
                Beatmap.updateReturning(
                    {
                        Beatmap.id eq body.mapId
                    },
                    {
                        it[score] = scoreWeighted.toBigDecimal()
                        it[upVotesInt] = upVotes.toInt()
                        it[downVotesInt] = downVotes.toInt()
                        it[lastVoteAt] = NowExpression(lastVoteAt.columnType)
                    },
                    Beatmap.uploader
                )?.firstOrNull()?.let {
                    uploader = it[Beatmap.uploader].value
                }

                uploader
            }?.let { uploader ->
                publish("beatmaps", "user.stats.$uploader", null, uploader)
                publish("beatmaps", "voteupdate.${body.mapId}", null, body.mapId)
            }
        }

        consumeAck("uvstats", Int::class) { _, body ->
            transaction {
                val subQuery = Beatmap
                    .joinVersions()
                    .slice(coalesce(Beatmap.upVotesInt.sum(), intLiteral(0)).alias("votes"))
                    .select {
                        Beatmap.uploader eq body and Beatmap.deletedAt.isNull()
                    }

                User.update({
                    User.id eq body
                }) {
                    it[upvotes] = wrapAsExpressionNotNull(subQuery)
                }
            }
        }
    }

    get<VoteApi.Since>("Get votes".responds(ok<List<VoteSummary>>(), notFound())) { req ->
        call.response.header("Access-Control-Allow-Origin", "*")

        val voteSummary = transaction {
            val updatedMaps =
                Beatmap.joinVersions(false).select {
                    Beatmap.lastVoteAt greaterEq req.since.toJavaInstant()
                }.complexToBeatmap()

            updatedMaps.map {
                val mapDetail = MapDetail.from(it, "")

                VoteSummary(
                    mapDetail.publishedVersion()?.hash,
                    it.id.value,
                    toHexString(it.id.value),
                    it.upVotesInt,
                    it.downVotesInt,
                    it.score.toDouble()
                )
            }
        }

        call.respond(voteSummary)
    }

    post<VoteApi.Vote, VoteRequest>("Vote on a map".responds(ok<VoteResponse>())) { _, req ->
        call.response.header("Access-Control-Allow-Origin", "*")
        call.tag("platform", if (req.auth.steamId != null) "steam" else if (req.auth.oculusId != null) "oculus" else "unknown")

        newSuspendedTransaction {
            try {
                val mapIdArr = Versions.slice(Versions.mapId).select {
                    Versions.hash eq req.hash
                }.limit(1).toList()

                if (mapIdArr.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@newSuspendedTransaction
                }

                val (userId, steam) = req.auth.steamId?.let { steamId ->
                    if (!validateSteamToken(steamId, req.auth.proof)) {
                        error("Could not validate steam token")
                    }

                    // Valid steam user
                    steamId.toLong() to true
                } ?: req.auth.oculusId?.let { oculusId ->
                    if (!validateOculusToken(oculusId, req.auth.proof)) {
                        error("Could not validate oculus token")
                    }

                    // Valid oculus user
                    oculusId.toLong() to false
                } ?: error("No user identifier provided")

                val mapId = mapIdArr.first()[Versions.mapId]
                call.pub("beatmaps", "vote.$mapId", null, QueuedVote(userId, steam, mapId.value, req.direction))

                call.respond(VoteResponse(true))
            } catch (e: IllegalStateException) {
                call.respond(VoteResponse(false, e.message))
            }
        }
    }
}
