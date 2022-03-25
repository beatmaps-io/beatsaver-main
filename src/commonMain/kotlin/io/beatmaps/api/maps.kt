@file:UseSerializers(InstantAsStringSerializer::class, ECharacteristicSerializer::class)
package io.beatmaps.api

import io.beatmaps.common.MapTag
import io.beatmaps.common.api.ECharacteristic
import io.beatmaps.common.api.ECharacteristicSerializer
import io.beatmaps.common.api.EDifficulty
import io.beatmaps.common.api.EMapState
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.roundToInt

object InstantAsStringSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@Serializable
data class MapDetail(
    val id: String,
    val name: String,
    val description: String,
    val uploader: UserDetail,
    val metadata: MapDetailMetadata,
    val stats: MapStats,
    val uploaded: Instant? = null,
    val automapper: Boolean,
    val ranked: Boolean,
    val qualified: Boolean,
    val versions: List<MapVersion> = listOf(),
    val curator: UserDetail? = null,
    val curatedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastPublishedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val tags: List<MapTag> = listOf()
) {
    fun intId() = id.toInt(16)
    fun latestVersion() = versions.maxByOrNull { it.createdAt }
    fun publishedVersion() = versions.firstOrNull { it.state == EMapState.Published }
    fun mainVersion() = publishedVersion() ?: latestVersion()
    companion object
}

@Serializable
data class MapVersion(
    val hash: String,
    val key: String? = null,
    val state: EMapState,
    val createdAt: Instant,
    val sageScore: Short? = 0,
    val diffs: List<MapDifficulty> = listOf(),
    val feedback: String? = null,
    val testplayAt: Instant? = null,
    val testplays: List<MapTestplay>? = null,
    val downloadURL: String,
    val coverURL: String,
    val previewURL: String,
    val scheduledAt: Instant? = null
) { companion object }

@Serializable
data class MapDifficulty(
    val njs: Float,
    val offset: Float,
    val notes: Int,
    val bombs: Int,
    val obstacles: Int,
    val nps: Double,
    val length: Double,
    val characteristic: ECharacteristic,
    val difficulty: EDifficulty,
    val events: Int,
    val chroma: Boolean,
    val me: Boolean,
    val ne: Boolean,
    val cinema: Boolean,
    val seconds: Double,
    val paritySummary: MapParitySummary,
    val stars: Float? = null,
    val maxScore: Int
) { companion object }

@Serializable
data class MapParitySummary(val errors: Int, val warns: Int, val resets: Int) { companion object }

@Serializable
data class MapDetailMetadata(
    val bpm: Float,
    val duration: Int,
    val songName: String,
    val songSubName: String,
    val songAuthorName: String,
    val levelAuthorName: String
) { companion object }

@Serializable
data class MapStats(val plays: Int, val downloads: Int, val upvotes: Int, val downvotes: Int, val score: Float) {
    val scoreOneDP by lazy { (score * 1000).roundToInt() / 10f }
    companion object
}

@Serializable
data class MapTestplay(val feedback: String? = null, val video: String? = null, val user: UserDetail, val createdAt: Instant, val feedbackAt: Instant? = null) { companion object }

@Serializable
data class SearchResponse(val docs: List<MapDetail>? = null, val redirect: String? = null)
