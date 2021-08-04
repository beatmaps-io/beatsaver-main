package io.beatmaps.login

import io.beatmaps.common.Config
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.OAuthServerSettings
import io.ktor.auth.oauth
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.http.HttpMethod

val discordProvider = OAuthServerSettings.OAuth2ServerSettings(
    name = "discord",
    authorizeUrl = "https://discord.com/api/oauth2/authorize",
    accessTokenUrl = "https://discord.com/api/oauth2/token",
    clientId = System.getenv("DISCORD_CLIENTID") ?: "",
    clientSecret = System.getenv("DISCORD_CLIENTSECRET") ?: "",
    requestMethod = HttpMethod.Post,
    defaultScopes = listOf("identify")
)

fun Application.installDiscordOauth() {
    val baseName = System.getenv("BASE_URL") ?: Config.basename
    install(Authentication) {
        oauth("discord") {
            client = HttpClient(Apache)
            providerLookup = { discordProvider }
            urlProvider = { "$baseName/login" }
        }
    }
}