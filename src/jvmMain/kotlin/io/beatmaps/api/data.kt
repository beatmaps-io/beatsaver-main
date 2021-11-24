package io.beatmaps.api

import io.beatmaps.common.Config
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.DifficultyDao
import io.beatmaps.common.dbo.PlaylistDao
import io.beatmaps.common.dbo.TestplayDao
import io.beatmaps.common.dbo.VersionsDao
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import java.lang.Integer.toHexString

fun MapDetail.Companion.from(other: BeatmapDao, cdnPrefix: String) = MapDetail(
    toHexString(other.id.value), other.name, other.description,
    UserDetail.from(other.uploader), MapDetailMetadata.from(other), MapStats.from(other), other.uploaded?.toKotlinInstant(), other.automapper, other.ranked, other.qualified,
    other.versions.values.map { MapVersion.from(it, cdnPrefix) }.partition { it.state == EMapState.Published }.let {
        // Once a map is published hide previous versions
        it.first.ifEmpty {
            it.second
        }
    },
    other.curator?.name, other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.lastPublishedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant()
)
fun MapDetail.Companion.from(row: ResultRow, cdnPrefix: String) = from(BeatmapDao.wrapRow(row), cdnPrefix)

fun MapVersion.Companion.from(other: VersionsDao, cdnPrefix: String) = MapVersion(
    other.hash, other.key64, other.state, other.uploaded.toKotlinInstant(), other.sageScore,
    other.difficulties.values.map { MapDifficulty.from(it) }.sortedWith(compareBy(MapDifficulty::characteristic, MapDifficulty::difficulty)), other.feedback,
    other.testplayAt?.toKotlinInstant(), if (other.testplays.isEmpty()) null else other.testplays.values.map { MapTestplay.from(it) },
    "${Config.cdnBase(cdnPrefix)}/${other.hash}.zip", "${Config.cdnBase(cdnPrefix)}/${other.hash}.jpg", "${Config.cdnBase(cdnPrefix)}/${other.hash}.mp3"
)
fun MapVersion.Companion.from(row: ResultRow, cdnPrefix: String) = from(VersionsDao.wrapRow(row), cdnPrefix)

fun MapDifficulty.Companion.from(other: DifficultyDao) = MapDifficulty(
    other.njs, other.offset, other.notes, other.bombs, other.obstacles, other.nps.toDouble(),
    other.length.toDouble(), other.characteristic, other.difficulty, other.events, other.chroma, other.me, other.ne, other.cinema, other.seconds.toDouble(), MapParitySummary.from(other),
    other.stars?.toFloat()
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
    other.id.value, "${Config.cdnBase(cdnPrefix)}/playlist/${other.id.value}.jpg", other.name, other.public, other.ownerId.value
)
fun PlaylistBasic.Companion.from(row: ResultRow, cdnPrefix: String) = from(PlaylistDao.wrapRow(row), cdnPrefix)

fun PlaylistFull.Companion.from(other: PlaylistDao, cdnPrefix: String) = PlaylistFull(
    other.id.value, other.name, other.description, "${Config.cdnBase(cdnPrefix)}/playlist/${other.id.value}.jpg", other.public, UserDetail.from(other.owner),
    other.createdAt.toKotlinInstant(), other.updatedAt.toKotlinInstant(), other.songsChangedAt?.toKotlinInstant(), other.deletedAt?.toKotlinInstant()
)
fun PlaylistFull.Companion.from(row: ResultRow, cdnPrefix: String) = from(PlaylistDao.wrapRow(row), cdnPrefix)
