package io.beatmaps.api

import kotlinx.datetime.Instant

data class OauthSession(val id: String, val clientName: String, val clientIcon: String? = null, val scopes: List<String>, val expiry: Instant)
data class SiteSession(val id: String, val countryCode: String? = null, val expiry: Instant, val current: Boolean)
data class SessionsData(val oauth: List<OauthSession>, val site: List<SiteSession>)
