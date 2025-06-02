package io.beatmaps.util

import external.Moment
import io.beatmaps.History
import io.beatmaps.common.SearchOrder
import io.beatmaps.common.SearchParamsPlaylist
import io.beatmaps.common.api.RankedFilter
import io.beatmaps.common.json
import io.beatmaps.index.SearchParams
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption

fun String.toInstant() = Instant.parse(Moment(this).toISOString())

external fun encodeURIComponent(uri: String): String
external fun decodeURIComponent(encodedURI: String): String

private val base64 = Base64.withPadding(PaddingOption.PRESENT_OPTIONAL)

fun parseJwt(jwt: String): JsonObject {
    val base64Url = jwt.split('.')[1]
    val jsonPayload = base64.decode(base64Url).decodeToString()
    return json.parseToJsonElement(jsonPayload).jsonObject
}

val hashRegex = Regex("^[A-F0-9]{40}$", RegexOption.IGNORE_CASE)

fun SearchParams?.toPlaylistConfig() = SearchParamsPlaylist(
    this?.search ?: "",
    this?.automapper,
    this?.minNps,
    this?.maxNps,
    this?.chroma,
    this?.sortOrder ?: SearchOrder.Latest,
    this?.ascending,
    this?.from?.toInstant(),
    this?.to?.toInstant(),
    this?.noodle,
    this?.ranked ?: RankedFilter.All,
    this?.curated,
    this?.verified,
    this?.fullSpread,
    this?.me,
    this?.cinema,
    this?.vivify,
    this?.tags ?: mapOf(),
    environments = this?.environments ?: emptySet()
)

fun includeIfNotNull(v: Any?, name: String) = if (v != null) "$name=$v" else null

fun <T> T.buildURL(parts: List<String>, root: String = "", row: Int?, state: T?, history: History) =
    ((if (parts.isEmpty()) "/$root" else "?" + parts.joinToString("&")) + (row?.let { "#$it" } ?: "")).let { newUrl ->
        if (this == state) {
            history.replace(newUrl)
        } else {
            history.push(newUrl)
        }
    }
