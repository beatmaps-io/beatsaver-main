package io.beatmaps.login

class Client(
    val id: String,
    val secret: String,
    val redirect: String? = null,
    val scopes: Set<String>? = null
)

val OauthClients = listOf(
    Client("BeatLeader", System.getenv("BLSecret") ?: "InsecureSecret", "https://api.beatleader.xyz/signin-beatsaver", setOf("identity"))
)