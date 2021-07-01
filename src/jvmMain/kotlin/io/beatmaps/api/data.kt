package io.beatmaps.api

import io.beatmaps.common.api.*
import io.beatmaps.common.dbo.BeatmapDao
import io.beatmaps.common.dbo.DifficultyDao
import io.beatmaps.common.dbo.TestplayDao
import io.beatmaps.common.dbo.VersionsDao
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow

fun MapDetail.Companion.from(other: BeatmapDao) = MapDetail(other.id.value, other.name, other.description,
    UserDetail.from(other.uploader), MapDetailMetadata.from(other), MapStats.from(other), other.uploaded?.toKotlinInstant(), other.automapper,
    other.versions.values.map { MapVersion.from(it) }.partition { it.state == EMapState.Published }.let {
        // Once a map is published hide previous versions
        if (it.first.isNotEmpty()) {
            it.first
        } else {
            it.second
        }
    }, other.curator?.name)
fun MapDetail.Companion.from(row: ResultRow) = from(BeatmapDao.wrapRow(row))

fun MapVersion.Companion.from(other: VersionsDao) = MapVersion(other.hash, other.key64, other.state, other.uploaded.toKotlinInstant(), other.sageScore,
    other.difficulties.values.map { MapDifficulty.from(it) }.sortedWith(compareBy(MapDifficulty::characteristic, MapDifficulty::difficulty)), other.feedback,
    other.testplayAt?.toKotlinInstant(), if (other.testplays.isEmpty()) null else other.testplays.values.map { MapTestplay.from(it) })
fun MapVersion.Companion.from(row: ResultRow) = from(VersionsDao.wrapRow(row))

fun MapDifficulty.Companion.from(other: DifficultyDao) = MapDifficulty(other.njs, other.offset, other.notes, other.bombs, other.obstacles, other.nps.toDouble(),
    other.length.toDouble(), other.characteristic, other.difficulty, other.events, other.chroma, other.me, other.ne, other.cinema, other.seconds.toDouble(), MapParitySummary.from(other),
    other.stars?.toFloat())

fun MapParitySummary.Companion.from(other: DifficultyDao) = MapParitySummary(other.pError, other.pWarn, other.pReset)

fun MapDetailMetadata.Companion.from(other: BeatmapDao) = MapDetailMetadata(other.bpm, other.duration, other.songName, other.songSubName, other.songAuthorName, other.levelAuthorName)

fun MapStats.Companion.from(other: BeatmapDao) = MapStats(other.plays, other.downloads, other.upVotesInt, other.downVotesInt, other.score.toFloat())

fun MapTestplay.Companion.from(other: TestplayDao) = MapTestplay(other.feedback, other.video, UserDetail.from(other.user), other.createdAt.toKotlinInstant(), other.feedbackAt?.toKotlinInstant())

fun Query.limit(page: Long?): Query {
    val pageSize = 20
    val offset = (page ?: 0) * pageSize
    return limit(pageSize, offset)
}