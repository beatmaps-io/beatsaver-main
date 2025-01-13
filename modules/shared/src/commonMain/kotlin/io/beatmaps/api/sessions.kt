package io.beatmaps.api

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

interface SessionInfo {
    val id: String
    val expiry: Instant
}

@Serializable
data class OauthSession(override val id: String, val clientName: String, val clientIcon: String? = null, val scopes: List<OauthScope>, override val expiry: Instant) : SessionInfo

@Serializable
data class SiteSession(override val id: String, val countryCode: String? = null, override val expiry: Instant, val current: Boolean) : SessionInfo

@Serializable
data class SessionsData(val oauth: List<OauthSession>, val site: List<SiteSession>)
