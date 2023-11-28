package io.beatmaps.cloudflare

import io.beatmaps.common.CDNUpdate
import io.beatmaps.common.api.AiDeclarationType
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.consumeAck
import io.beatmaps.common.dbo.Beatmap
import io.beatmaps.common.dbo.Versions
import io.beatmaps.common.dbo.VersionsDao
import io.beatmaps.common.downloadFilename
import io.beatmaps.common.localFolder
import io.beatmaps.common.rabbitOptional
import io.ktor.server.application.Application
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.util.logging.Logger

val cloudflareAccountId = System.getenv("CF_ACC_ID") ?: ""
val cloudflareAuthToken = System.getenv("CF_AUTH_TOKEN") ?: ""
val cloudflareKVId = System.getenv("CF_KV_ID") ?: ""

val cloudflareR2Enabled = System.getenv("CF_R2_ENABLED") != null

val cloudflareR2Key = System.getenv("CF_R2_KEY") ?: ""
val cloudflareR2Secret = System.getenv("CF_R2_SECRET") ?: ""
val cloudflareR2Bucket = System.getenv("CF_R2_BUCKET") ?: "beatsaver"

private val logger = Logger.getLogger("bmio.Cloudflare")

fun <K, V> createLRUMap(maxEntries: Int): MutableMap<K, V> {
    return object : LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
            return size > maxEntries
        }
    }
}

fun Application.filenameUpdater() {
    if (cloudflareAccountId.isEmpty()) {
        logger.warning("Cloudflare updater not set up")
        return
    }

    val worker = Worker(cloudflareAccountId, cloudflareAuthToken)
    val beatsaverKVStore = worker.getKVStore(cloudflareKVId)
    val r2Client = R2(cloudflareAccountId, cloudflareR2Key, cloudflareR2Secret).getBucket(cloudflareR2Bucket)
    val downloadFilenameCache = createLRUMap<String, String>(100)

    rabbitOptional {
        consumeAck("cdn.r2", CDNUpdate::class) { _, update ->
            // Ignore test maps
            if (update.hash?.contains(" ") == true) return@consumeAck

            deleteFromR2(update, r2Client)

            if (update.deleted) return@consumeAck
            val hash = update.hash ?: return@consumeAck

            updateDownloadFilename(update, beatsaverKVStore, hash, downloadFilenameCache)
            if (cloudflareR2Enabled) uploadToR2(update, r2Client)
        }
    }
}

private suspend fun updateDownloadFilename(update: CDNUpdate, beatsaverKVStore: IKVStore, hash: String, downloadFilenameCache: MutableMap<String, String>) {
    val updateSongName = update.songName
    val updateLevelAuthorName = update.levelAuthorName

    if (updateSongName != null && updateLevelAuthorName != null) {
        val dlFilename = downloadFilename(Integer.toHexString(update.mapId), updateSongName, updateLevelAuthorName)

        if (downloadFilenameCache.getOrDefault(hash, "") != dlFilename) {
            beatsaverKVStore.setValue(
                "$hash.zip",
                dlFilename
            )
            downloadFilenameCache[hash] = dlFilename
        }
    }
}

private fun uploadToR2(update: CDNUpdate, r2Client: IR2Bucket) {
    val toUpload = transaction {
        VersionsDao.wrapRows(
            Versions.join(Beatmap, JoinType.INNER, Versions.mapId, Beatmap.id).select {
                // Not uploaded, not beatsage, published
                (Versions.r2 neq true) and (Versions.mapId eq update.mapId) and (Beatmap.declaredAi eq AiDeclarationType.None) and (Versions.state eq EMapState.Published)
            }
        ).map { it.hash }
    }

    toUpload.map { File(localFolder(it), "$it.zip") }.filter { it.exists() }.map {
        r2Client.uploadFile(it)
    }

    transaction {
        Versions.update({
            (Versions.hash inList toUpload) and (Versions.mapId eq update.mapId)
        }) {
            it[r2] = true
        }
    }
}

private fun deleteFromR2(update: CDNUpdate, r2Client: IR2Bucket) {
    val toDelete = transaction {
        VersionsDao.wrapRows(
            Versions.join(Beatmap, JoinType.INNER, Versions.mapId, Beatmap.id).select {
                // Uploaded, deleted or beatsage or not published
                (Versions.r2 eq true) and (Versions.mapId eq update.mapId) and ((Beatmap.deletedAt.isNotNull()) or (Beatmap.declaredAi eq AiDeclarationType.None) or (Versions.state neq EMapState.Published))
            }
        ).map { it.hash }
    }

    toDelete.forEach {
        r2Client.deleteFile("$it.zip")
    }

    transaction {
        Versions.update({
            (Versions.hash inList toDelete) and (Versions.mapId eq update.mapId)
        }) {
            it[r2] = false
        }
    }
}
