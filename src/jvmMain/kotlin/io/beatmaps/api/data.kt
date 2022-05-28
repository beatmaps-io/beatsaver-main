package io.beatmaps.api

import io.beatmaps.common.Config
import io.beatmaps.common.MapTag
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.DifficultyDao
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.TestplayDao
import io.beatmaps.common.dbo.VersionsDao
import kotlinx.datetime.Clock
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import java.lang.Integer.toHexString
import kotlin.time.Duration

val remoteCdn = System.getenv("REMOTE_CDN") != null
fun cdnBase(prefix: String) = when (remoteCdn) {
    true -> Config.cdnBase(prefix)
    false -> "/cdn"
}

fun MapDetail.Companion.from(other: BeatmapDao, cdnPrefix: String) = MapDetail(
    toHexString(other.id.value), other.name, other.description,
    UserDetail.from(other.uploader), MapDetailMetadata.from(other), MapStats.from(other), other.uploaded?.toKotlinInstant(), other.automapper, other.ai, other.ranked, other.qualified,
    other.versions.values.map { MapVersion.from(it, cdnPrefix) }.partition { it.state == EMapState.Published }.let {
        // Once a map is published hide previous versions
        it.first.ifEmpty {
            it.second
        }
    },
    other.curator?.let {
        UserDetail.from(it)
    },
    other.curatedAt?.toKotlinInstant(), other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.lastPublishedAt?.toKotlinInstant(),
    other.deletedAt?.toKotlinInstant(),
    other.tags?.mapNotNull {
        MapTag.fromSlug(it)
    } ?: listOf()
)
fun MapDetail.Companion.from(row: ResultRow, cdnPrefix: String) = from(BeatmapDao.wrapRow(row), cdnPrefix)

fun getActualPrefixForVersion(other: VersionsDao, cdnPrefix: String) =
    // Don't use cdn servers during sync period
    if (Clock.System.now() - other.uploaded.toKotlinInstant() < Duration.Companion.seconds(30)) {
        "" to ""
    } else if (other.r2) {
        "r2" to cdnPrefix
    } else {
        cdnPrefix to cdnPrefix
    }
fun MapVersion.Companion.from(other: VersionsDao, cdnPrefix: String) =
    getActualPrefixForVersion(other, cdnPrefix).let { (zipPrefix, actualPrefix) ->
        MapVersion(
            other.hash, other.key64, other.state, other.uploaded.toKotlinInstant(), other.sageScore,
            other.difficulties.values.map { MapDifficulty.from(it) }.sortedWith(compareBy(MapDifficulty::characteristic, MapDifficulty::difficulty)), other.feedback,
            other.testplayAt?.toKotlinInstant(), if (other.testplays.isEmpty()) null else other.testplays.values.map { MapTestplay.from(it) },
            "${cdnBase(zipPrefix)}/${other.hash}.zip", "${cdnBase(actualPrefix)}/${other.hash}.jpg",
            "${cdnBase(actualPrefix)}/${other.hash}.mp3",
            other.scheduledAt?.toKotlinInstant()
        )
    }
fun MapVersion.Companion.from(row: ResultRow, cdnPrefix: String) = from(VersionsDao.wrapRow(row), cdnPrefix)

fun MapDifficulty.Companion.from(other: DifficultyDao) = MapDifficulty(
    other.njs, other.offset, other.notes, other.bombs, other.obstacles, other.nps.toDouble(),
    other.length.toDouble(), other.characteristic, other.difficulty, other.events, other.chroma, other.me, other.ne, other.cinema, other.seconds.toDouble(), MapParitySummary.from(other),
    other.stars?.toFloat(), other.maxScore
)

fun MapParitySummary.Companion.from(other: DifficultyDao) = MapParitySummary(other.pError, other.pWarn, other.pReset)

fun MapDetailMetadata.Companion.from(other: BeatmapDao) = MapDetailMetadata(other.bpm, other.duration, other.songName, other.songSubName, other.songAuthorName, other.levelAuthorName)

fun MapStats.Companion.from(other: BeatmapDao) = MapStats(other.plays, 0, other.upVotesInt, other.downVotesInt, other.score.toFloat())

fun MapTestplay.Companion.from(other: TestplayDao) = MapTestplay(other.feedback, other.video, UserDetail.from(other.user), other.createdAt.toKotlinInstant(), other.feedbackAt?.toKotlinInstant())

fun Query.limit(page: Long?, pageSize: Int = 20): Query {
    val offset = (page ?: 0) * pageSize
    return limit(pageSize, offset)
}

fun PlaylistBasic.Companion.from(other: PlaylistDao, cdnPrefix: String) = PlaylistBasic(
    other.id.value, "${cdnBase(cdnPrefix)}/playlist/${other.id.value}.jpg", other.name, other.public, other.ownerId.value
)
fun PlaylistBasic.Companion.from(row: ResultRow, cdnPrefix: String) = from(PlaylistDao.wrapRow(row), cdnPrefix)

fun PlaylistFull.Companion.from(other: PlaylistDao, cdnPrefix: String) = PlaylistFull(
    other.id.value, other.name, other.description, "${cdnBase(cdnPrefix)}/playlist/${other.id.value}.jpg", other.public, UserDetail.from(other.owner),
    other.curator?.let {
        UserDetail.from(it)
    },
    other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.songsChangedAt?.toKotlinInstant(), other.curatedAt?.toKotlinInstant(),
    other.deletedAt?.toKotlinInstant()
)
fun PlaylistFull.Companion.from(row: ResultRow, cdnPrefix: String) = from(PlaylistDao.wrapRow(row), cdnPrefix)
