package io.beatmaps

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.GeoIp2Exception
import io.beatmaps.common.DownloadInfo
import io.beatmaps.common.DownloadType
import io.beatmaps.common.consumeAck
import io.beatmaps.common.db.incrementBy
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.rabbitOptional
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.net.InetAddress

val geodbFilePath = System.getenv("GEOIP_PATH") ?: "geolite2.mmdb"
val geoIp = DatabaseReader.Builder(File(geodbFilePath)).withCache(CHMCache()).build()
val cdnPrefixes = mapOf(
    "AF" to "",
    "AN" to "",
    "AS" to "as.",
    "EU" to "eu.",
    "NA" to "na.",
    "OC" to "as.",
    "SA" to "na.",
    "CN" to "na."
)
private val cdnPrefixAttr = AttributeKey<String>("cdnPrefix")

fun getContinentSafe(addr: InetAddress) = try {
    val countryInfo = geoIp.country(addr)
    if (countryInfo.country.isoCode == "CN") {
        "CN"
    } else {
        countryInfo.continent.code ?: ""
    }
} catch (e: GeoIp2Exception) {
    ""
}

fun PipelineContext<*, ApplicationCall>.cdnPrefix(): String {
    if (!call.attributes.contains(cdnPrefixAttr)) {
        call.attributes.put(
            cdnPrefixAttr,
            cdnPrefixes.getOrDefault(getContinentSafe(InetAddress.getByName(call.request.origin.remoteHost)), "")
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
