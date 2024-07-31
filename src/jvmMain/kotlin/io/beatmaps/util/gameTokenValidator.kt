package io.beatmaps.util

import io.beatmaps.api.AuthRequest
import io.beatmaps.api.OculusAuthResponse
import io.beatmaps.api.SteamAPIResponse
import io.beatmaps.api.beatsaberAppid
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException

class GameTokenValidator(private val client: HttpClient) {
    private val authHost = System.getenv("AUTH_HOST") ?: "http://localhost:3030"

    suspend fun steam(steamId: String, proof: String): Boolean {
        val clientId = System.getenv("STEAM_APIKEY") ?: ""
        val data = client.get("https://api.steampowered.com/ISteamUserAuth/AuthenticateUserTicket/v1?key=$clientId&appid=$beatsaberAppid&ticket=$proof")
            .body<SteamAPIResponse>()
        return !(data.response.params == null || data.response.params.result != "OK" || data.response.params.steamid.toString() != steamId)
    }

    suspend fun oculus(oculusId: String, proof: String) = try {
        val data = client.post("$authHost/auth/oculus") {
            contentType(ContentType.Application.Json)
            setBody(AuthRequest(oculusId = oculusId, proof = proof))
        }.body<OculusAuthResponse>()
        data.success
    } catch (e: BadRequestException) {
        false
    } catch (e: ClientRequestException) {
        false
    }
}
