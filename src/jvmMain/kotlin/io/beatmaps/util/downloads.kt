package io.beatmaps.util

import io.beatmaps.common.CountryInfo
import io.beatmaps.common.amqp.DownloadInfo
import io.beatmaps.common.amqp.DownloadType
import io.beatmaps.common.amqp.consumeAck
import io.beatmaps.common.amqp.rabbitOptional
import io.beatmaps.common.db.incrementBy
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.getCountry
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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
    }
}
