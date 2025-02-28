package io.beatmaps.controllers.upload

import io.beatmaps.common.beatsaber.info.BaseMapInfo
import io.beatmaps.common.beatsaber.info.toJson
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.ZipHelper
import io.beatmaps.common.zip.ZipHelperWithAudio
import io.ktor.client.HttpClient
import java.io.OutputStream
import java.security.DigestOutputStream

fun ZipHelperWithAudio.initValidation(maxVivify: Long) = info.let {
    // Add files referenced in info.dat to whitelist
    ExtractedInfo(findAllowedFiles(it), it, scoreMap(), maxVivify = maxVivify)
}

suspend fun ZipHelperWithAudio.validateFiles(q: ExtractedInfo, client: HttpClient) =
    DigestOutputStream(OutputStream.nullOutputStream(), q.md).use { dos ->
        // Rename audio file if it ends in .ogg
        val (info, newFiles, newFilesOriginalCase) = oggToEgg(q)

        // Ensure it ends in a slash
        val prefix = infoPrefix()
        val withoutPrefix = newFiles.map { its -> its.removePrefix(prefix.lowercase()) }.toSet()

        // Validate info.dat
        info.mapInfo.validate(withoutPrefix, info, audioFile, previewAudioFile, client, ::fromInfo)

        val output = info.mapInfo.toJson().toByteArray()
        dos.write(output)
        info.toHash.writeTo(dos)

        // Generate 10 second preview
        generatePreview(info.preview, info.duration)

        // Write updated info.dat back to zip
        infoPath.deleteIfExists()
        getPathDirect("/Info.dat").outputStream().use {
            it.write(output)
        }

        // Delete any extra files in the zip (like autosaves)
        val paritioned = newFilesOriginalCase.filter { !it.endsWith("/Info.dat", true) }.partition {
            val originalWithoutPrefix = it.lowercase().removePrefix(prefix.lowercase())
            !info.allowedFiles.contains(originalWithoutPrefix)
        }

        paritioned.first.forEach {
            getPathDirect(it).deleteIfExists()
        }

        // Move files to root
        if (prefix.length > 1) {
            // Files in subfolder!
            paritioned.second.forEach {
                moveFile(getPathDirect(it), "/" + it.removePrefix(prefix))
            }
            directories.filter { it.startsWith(prefix) }.sortedBy { it.length }.forEach {
                getPathDirect(it).deleteIfExists()
            }
        }

        info
    }

fun findAllowedFiles(info: BaseMapInfo) =
    (listOfNotNull("info.dat", "cinema-video.json") + info.getExtraFiles())
        .map { it.lowercase() }

fun ZipHelper.oggToEgg(info: ExtractedInfo): Triple<ExtractedInfo, Set<String>, Set<String>> {
    val moved = setOf(info.mapInfo.getSongFilename(), info.mapInfo.getPreviewInfo().filename)
        .filterNotNull()
        .filter { it.endsWith(".ogg") }
        .fold(mapOf<String, String>()) { acc, filename ->
            fromInfo(filename.lowercase())?.let { path ->
                val newFilename = filename.replace(Regex("\\.ogg$"), ".egg")
                moveFile(path, "/$newFilename")
                acc.plus(filename to newFilename)
            } ?: acc
        }

    return Triple(
        info.copy(mapInfo = info.mapInfo.updateFiles(moved)),
        files
            .minus(moved.keys.map { (infoPrefix() + it).lowercase() }.toSet())
            .plus(moved.values.map { (infoPrefix() + it).lowercase() }.toSet()),
        // Don't add it back so that we don't later try and remove the file as it's no longer whitelisted
        filesOriginalCase.minus(moved.keys.map { infoPrefix() + it }.toSet())
    )
}
