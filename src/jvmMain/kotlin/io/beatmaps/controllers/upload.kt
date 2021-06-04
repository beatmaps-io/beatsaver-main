package io.beatmaps.controllers

import ch.compile.recaptcha.ReCaptchaVerify
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectWriter
import io.beatmaps.api.FailedUploadResponse
import io.beatmaps.common.beatsaber.MapInfo
import io.beatmaps.common.BSPrettyPrinter
import io.beatmaps.common.api.EMapState
import io.beatmaps.common.beatsaver.localCoverFolder
import io.beatmaps.common.beatsaver.localFolder
import io.beatmaps.common.checkParity
import io.beatmaps.common.copyToSuspend
import io.beatmaps.common.dbo.*
import io.beatmaps.common.jackson
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.beatmaps.common.zip.ExtractedInfo
import io.beatmaps.common.zip.ZipHelper
import io.beatmaps.common.zip.ZipHelper.Companion.openZip
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import pl.jutupe.ktor_rabbitmq.publish
import java.io.File
import java.io.OutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Files
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import kotlin.collections.filter
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapNotNull
import kotlin.collections.mutableMapOf
import kotlin.collections.partition
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.sortedBy
import kotlin.collections.toSet
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream
import kotlin.math.roundToInt

val jsonWriter: ObjectWriter = jackson.writer(BSPrettyPrinter())

val uploadDir = File(System.getenv("UPLOAD_DIR") ?: "S:\\A")
val allowUploads = System.getenv("ALLOW_UPLOADS") != "false"
val reCaptchaVerify = ReCaptchaVerify(System.getenv("RECAPTCHA_SECRET") ?: "")

@Location("/upload") class DMCA

fun Route.uploadController() {
    get<DMCA> {
        if (call.sessions.get<Session>() == null) {
            call.respondRedirect("/")
        } else {
            genericPage()
        }
    }

    post<DMCA> {
        val session = call.sessions.get<Session>() ?: throw UploadException("Not logged in")

        val file = File(
            uploadDir,
            "upload-${System.currentTimeMillis()}-${session.userId.hashCode()}.zip"
        )

        val multipart = call.receiveMultipart()
        val dataMap = mutableMapOf<String, String>()
        val md = MessageDigest.getInstance("SHA1")
        var extractedInfoTmp: ExtractedInfo? = null

        multipart.forEachPart { part ->
            if (part is PartData.FormItem) {
                dataMap[part.name.toString()] = part.value
            } else if (part is PartData.FileItem) {
                extractedInfoTmp = part.streamProvider().use { its ->
                    try {
                        file.outputStream().buffered().use {
                            its.copyToSuspend(it, sizeLimit = 15 * 1024 * 1024)
                        }.run {
                            DigestOutputStream(OutputStream.nullOutputStream(), md).use { dos ->
                                openZip(file) {
                                    validateFiles(dos)
                                }
                            }
                        }
                    } catch (e: ZipException) {
                        if (file.exists()) {
                            val rar = file.inputStream().use {
                                String(it.readNBytes(4)) == "Rar!"
                            }

                            if (rar) {
                                file.delete()
                                throw UploadException("Don't upload rar files. Use the package button in your map editor.")
                            }
                        }
                        file.delete()
                        throw e
                    } catch (e: JsonMappingException) {
                        file.delete()
                        throw UploadException("Could not parse json")
                    } catch (e: Exception) {
                        file.delete()
                        throw e
                    }
                }
            }
        }

        // Check the recaptcha result
        withContext(Dispatchers.IO) {
            reCaptchaVerify.verify(dataMap["recaptcha"], call.request.origin.remoteHost)
        }.isSuccess || throw UploadException("Could not verify user")

        val newMapId = transaction {
            // Process upload
            val fx = "%0" + md.digestLength * 2 + "x"
            val digest = String.format(fx, BigInteger(1, md.digest()))
            val newFile = File(localFolder(digest), "${digest}.zip")
            val newImageFile = File(localCoverFolder(digest), "${digest}.jpg")

            val existsAlready = Versions.select {
                Versions.hash eq digest
            }.count() > 0

            val extractedInfo = extractedInfoTmp ?: throw UploadException("Internal error 1")

            if (existsAlready) {
                file.delete()
                throw UploadException("Map already uploaded")
            }

            if (!session.testplay || !allowUploads) {
                file.delete()
                throw UploadException("Your map is fine but we're not accepting uploads yet")
            }

            val newMap = try {
                (dataMap["mapId"]?.toInt()?.let { mapId ->
                    val beatmap = BeatmapDao.wrapRows(Beatmap.slice(Beatmap.id, Beatmap.uploader).select {
                        Beatmap.id eq mapId
                    }.limit(1)).firstOrNull()

                    if (beatmap == null) {
                        throw UploadException("Map doesn't exist to add version")
                    } else if (beatmap.uploaderId.value != session.userId) {
                        throw UploadException("Can't upload to someone else's map")
                    }

                    beatmap.id
                } ?: Beatmap.insertAndGetId {
                    it[name] = dataMap["title"] ?: ""
                    it[description] = dataMap["description"] ?: ""
                    it[uploader] = EntityID(session.userId, User)
                    it[bpm] = extractedInfo.mapInfo._beatsPerMinute
                    it[duration] = extractedInfo.duration.roundToInt()
                    it[songName] = extractedInfo.mapInfo._songName
                    it[songSubName] = extractedInfo.mapInfo._songSubName
                    it[levelAuthorName] = extractedInfo.mapInfo._levelAuthorName
                    it[songAuthorName] = extractedInfo.mapInfo._songAuthorName
                    it[automapper] = extractedInfo.score < 0
                    it[plays] = 0
                }).also {
                    // How is a file here if it hasn't be uploaded before?
                    if (newFile.exists()) {
                        newFile.delete()
                    }

                    Files.move(file.toPath(), newFile.toPath())
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }

            try {
                extractedInfo.thumbnail?.let {
                    newImageFile.writeBytes(it.toByteArray())
                } ?: throw UploadException("Internal error 2")

                val newVersion = Versions.insertAndGetId {
                    it[mapId] = newMap
                    it[key64] = null
                    it[hash] = digest
                    it[state] = EMapState.Uploaded
                    it[sageScore] = extractedInfo.score
                }

                extractedInfo.diffs.forEach { cLoop ->
                    cLoop.value.forEach { dLoop ->
                        val diffInfo = dLoop.key
                        val bsdiff = dLoop.value
                        val parityResults = checkParity(bsdiff)

                        Difficulty.insertAndGetId {
                            it[mapId] = newMap
                            it[versionId] = newVersion

                            it[njs] = diffInfo._noteJumpMovementSpeed
                            it[offset] = diffInfo._noteJumpStartBeatOffset
                            it[characteristic] = cLoop.key.enumValue()
                            it[difficulty] = dLoop.key.enumValue()

                            it[pReset] = parityResults.info
                            it[pError] = parityResults.errors
                            it[pWarn] = parityResults.warnings

                            val sorted = bsdiff._notes.sortedBy { note -> note._time }
                            val partitioned = bsdiff._notes.partition { note -> note._type != 3 }
                            val len = if (sorted.isNotEmpty()) {
                                sorted.last()._time - sorted.first()._time
                            } else 0f

                            it[notes] = partitioned.first.size
                            it[bombs] = partitioned.second.size
                            it[obstacles] = bsdiff._obstacles.size
                            it[events] = bsdiff._events.size
                            it[length] = len.toBigDecimal()
                            it[seconds] = (if (extractedInfo.mapInfo._beatsPerMinute == 0f) 0 else (60 / extractedInfo.mapInfo._beatsPerMinute) * len).toDouble().toBigDecimal()

                            it[nps] = BigDecimal.valueOf(if (len == 0f) 0.0 else ((partitioned.first.size / len) * (extractedInfo.mapInfo._beatsPerMinute / 60)).toDouble()).min(maxAllowedNps)
                            it[chroma] = diffInfo._customData?._requirements?.contains("Chroma") ?: false || diffInfo._customData?._suggestions?.contains("Chroma") ?: false
                            it[ne] = diffInfo._customData?._requirements?.contains("Noodle Extensions") ?: false
                            it[me] = diffInfo._customData?._requirements?.contains("Mapping Extensions") ?: false
                        }
                    }
                }

                newMap.value
            } catch (e: Exception) {
                newFile.delete()
                throw e
            }
        }

        call.publish("beatmaps", "maps.$newMapId.updated", null, newMapId)
        call.respond(newMapId)
    }
}

fun ZipHelper.validateFiles(dos: DigestOutputStream) =
    info.also { it ->
        // Add info.dat to hash
        jsonWriter.writeValue(dos, it)
    }.let { it ->
        // Add files referenced in info.dat to whitelist
        ExtractedInfo(findAllowedFiles(it), dos, it, scoreMap())
    }.also { p ->
        // Ensure it ends in a slash
        val prefix = infoPrefix()
        val withoutPrefix = files.map { its -> its.removePrefix(prefix.toLowerCase()) }.toSet()

        // Validate info.dat
        p.mapInfo.validate(withoutPrefix, p, audioFile, ::fromInfo)

        // Rename audio file if it ends in .ogg
        oggToEgg(p)

        // Write updated info.dat back to zip
        infoPath.deleteIfExists()
        newPath("/Info.dat").outputStream().use {
            jsonWriter.writeValue(it, p.mapInfo)
        }

        // Delete any extra files in the zip (like autosaves)
        val paritioned = filesOriginalCase.filter { !it.endsWith("/Info.dat", true) }.partition {
            val originalWithoutPrefix = it.toLowerCase().removePrefix(prefix.toLowerCase())
            !p.allowedFiles.contains(originalWithoutPrefix)
        }

        paritioned.first.forEach {
            newPath(it).deleteIfExists()
        }

        // Move files to root
        if (prefix.length > 1) {
            // Files in subfolder!
            paritioned.second.forEach {
                newPath(it).moveTo(newPath("/" + it.removePrefix(prefix)))
            }
            directories.filter { it.startsWith(prefix) }.sortedBy { it.length }.forEach {
                newPath(it).deleteIfExists()
            }
        }
    }

fun findAllowedFiles(info: MapInfo) = (listOf("info.dat", info._coverImageFilename, info._songFilename) +
        (info._customData?._contributors?.mapNotNull { it._iconPath } ?: listOf()) +
        info._difficultyBeatmapSets.flatMap { set -> set._difficultyBeatmaps.map { it._beatmapFilename } }).map { it.toLowerCase() }

fun ZipHelper.oggToEgg(info: ExtractedInfo) =
    getPath("/${info.mapInfo._songFilename.toLowerCase()}")?.let { path ->
        if (info.mapInfo._songFilename.endsWith(".ogg")) {
            info.mapInfo = info.mapInfo.copy(_songFilename = info.mapInfo._songFilename.replace(Regex("\\.ogg$"), ".egg"))
            Files.move(path, newPath("/${info.mapInfo._songFilename}"))
        }
    }

class UploadException(private val msg: String) : RuntimeException() {
    fun toResponse() = FailedUploadResponse(listOf(msg))
}