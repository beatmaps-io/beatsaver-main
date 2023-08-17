package io.beatmaps.util

import io.beatmaps.common.dbo.Beatmap
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

fun isUploader(mapId: Int, userId: Int) = Beatmap.select {
    Beatmap.id eq mapId and (Beatmap.uploader eq userId)
}.count() > 0
