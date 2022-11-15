package io.beatmaps

import io.beatmaps.api.ReviewUpdateInfo
import io.beatmaps.common.CountryInfo
import io.beatmaps.common.DownloadInfo
import io.beatmaps.common.DownloadType
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.incrementBy
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Review
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.getCountry
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.avg
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

val cdnPrefixes = mapOf(
    "AF" to "",
    "AN" to "",
    "AS" to "eu.",
    "EU" to "eu.",
    "NA" to "na.",
    "OC" to "na.",
    "SA" to "na.",
    "CN" to "na."
).mapValues {
    val disabled = System.getenv("DISABLECDN_${it.value.trim('.')}") != null
    if (disabled) "" else it.value
}
private val cdnPrefixAttr = AttributeKey<String>("cdnPrefix")

fun getContinentSafe(countryInfo: CountryInfo) =
    if (countryInfo.countryCode == "CN") {
        "CN"
    } else {
        countryInfo.continentCode
    }

fun PipelineContext<*, ApplicationCall>.cdnPrefix(): String {
    if (!call.attributes.contains(cdnPrefixAttr)) {
        call.attributes.put(
            cdnPrefixAttr,
            cdnPrefixes.getOrDefault(getContinentSafe(call.getCountry()), "")
        )
    }

    return call.attributes[cdnPrefixAttr]
}

fun Application.downloadsThread() {
    rabbitOptional {
        consumeAck("bm.downloadCount", DownloadInfo::class) { _, dl ->
            try {
                transaction {
                    if (dl.type == DownloadType.HASH) {
                        Beatmap.join(Versions, JoinType.INNER, onColumn = Beatmap.id, Versions.mapId).update({ Versions.hash eq dl.hash }) {
                            it[Beatmap.downloads] = incrementBy(Beatmap.downloads, 1)
                        }
                    } else {
                        Beatmap.update({ Beatmap.id eq dl.hash.toInt(16) }) {
                            it[downloads] = incrementBy(downloads, 1)
                        }
                    }
                }
            } catch (_: NumberFormatException) {
                // Ignore
            }
        }

        consumeAck("bm.sentiment", ReviewUpdateInfo::class) { _, r ->
            transaction {
                val avg = Review.sentiment.avg(3)
                val count = Review.sentiment.count()

                val stats = Review.slice(avg, count).select {
                    Review.mapId eq r.mapId
                }.singleOrNull().let {
                    (it?.getOrNull(count)?.toInt() ?: 0) to (it?.getOrNull(avg) ?: BigDecimal.ZERO)
                }

                Beatmap.update({ Beatmap.id eq r.mapId }) {
                    it[sentiment] = stats.second
                    it[reviews] = stats.first
                }
            }
        }
    }
}
