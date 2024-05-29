package io.beatmaps.login

import com.toxicbakery.bcrypt.Bcrypt
import io.beatmaps.api.alertCount
import io.beatmaps.common.Config
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.login.patreon.patreonProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.form
import io.ktor.server.auth.oauth
import io.ktor.server.request.uri
import io.ktor.server.response.respondRedirect
import io.ktor.util.StringValues
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SimpleUserPrincipal(val user: UserDao, val alertCount: Int, val redirect: String) : Principal

fun Application.installOauth() {
    install(Authentication) {
        oauth("discord") {
            client = HttpClient(Apache)
            urlProvider = { "${Config.siteBase()}${request.uri.substringBefore("?")}" }
            providerLookup = {
                val queryParams = request.queryParameters as StringValues
                discordProvider(queryParams["state"])
            }
        }
        oauth("patreon") {
            client = HttpClient(Apache)
            urlProvider = { "${Config.siteBase()}${request.uri.substringBefore("?")}" }
            providerLookup = { patreonProvider }
        }
        form("auth-form") {
            userParamName = "username"
            passwordParamName = "password"
            challenge {
                if (call.request.uri.startsWith("/oauth2")) {
                    call.respondRedirect("${call.request.uri}&failed")
                } else {
                    call.respondRedirect("/login?failed")
                }
            }
            validate { credentials ->
                transaction {
                    User.selectAll().where {
                        if (credentials.name.contains('@')) {
                            (User.email eq credentials.name) and User.discordId.isNull()
                        } else {
                            User.uniqueName eq credentials.name
                        } and User.active
                    }.firstOrNull()?.let {
                        if (it[User.password]?.let { curPw -> Bcrypt.verify(credentials.password, curPw.toByteArray()) } == true) {
                            SimpleUserPrincipal(UserDao.wrapRow(it), alertCount(it[User.id].value), request.uri)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }
}
