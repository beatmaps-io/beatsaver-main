package io.beatmaps.api

import kotlinx.serialization.Serializable

@Serializable
data class QuestCode(val code: String)

@Serializable
data class QuestCodeResponse(
    val deviceCode: String,
    val clientName: String? = null,
    val clientIcon: String? = null,
    val scopes: String
)

@Serializable
data class QuestComplete(val deviceCode: String)
