package io.beatmaps.controllers

import io.beatmaps.api.UserDetail
import io.beatmaps.api.from
import io.beatmaps.common.dbo.User
import io.beatmaps.common.dbo.UserDao
import io.beatmaps.genericPage
import io.beatmaps.login.Session
import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.locations.post
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.meta
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Location("/uploader")
class UploaderController {
    @Location("/{key}")
    data class RedirectOld(val key: String, val api: UploaderController)
}

@Location("/profile")
class UserController {
    @Location("/unlink-discord")
    data class UnlinkDiscord(val api: UserController)

    @Location("/unlink-patreon")
    data class UnlinkPatreon(val api: UserController)

    @Location("/{id?}")
    data class Detail(val id: Int? = null, val api: UserController)

    @Location("/username/{name}")
    data class RedirectName(val name: String, val api: UserController)
}

@Location("/alerts")
class AlertController

fun Route.userController() {
    get<UploaderController.RedirectOld> {
        transaction {
            User.selectAll().where {
                User.hash eq it.key
            }.firstOrNull()?.let { UserDao.wrapRow(it) }
        }?.let {
            call.respondRedirect("/profile/${it.id}")
        } ?: run {
            call.respondRedirect("/")
        }
    }

    get<AlertController> {
        if (call.sessions.get<Session>() == null) {
            call.respondRedirect("/login")
        } else {
            genericPage()
        }
    }

    get<UserController.Detail> {
        if (it.id == null && call.sessions.get<Session>() == null) {
            call.respondRedirect("/login")
        } else {
            genericPage(
                headerTemplate = {
                    if (it.id != null) {
                        transaction {
                            User.selectAll().where {
                                User.id eq it.id and User.active
                            }.limit(1).map { u -> UserDetail.from(u) }.firstOrNull()
                        }?.let { detail ->
                            meta("og:type", "profile:${detail.name}")
                            meta("og:site_name", "BeatSaver")
                            meta("og:title", detail.name)
                            meta("og:url", detail.profileLink(absolute = true))
                            meta("og:image", detail.avatar)
                            meta("og:description", "${detail.name}'s BeatSaver profile")
                        }
                    }
                }
            )
        }
    }

    get<UserController.RedirectName> {
        transaction {
            User.selectAll().where {
                (User.uniqueName eq it.name) and User.active
            }.firstOrNull()?.let { UserDao.wrapRow(it) }
        }?.let {
            call.respondRedirect("/profile/${it.id}")
        } ?: run {
            call.respondRedirect("/")
        }
    }

    post<UserController.UnlinkDiscord> {
        val sess = call.sessions.get<Session>()
        if (sess != null) {
            transaction {
                User.update({ User.id eq sess.userId }) {
                    it[discordId] = null
                }
            }
            call.respondRedirect("/profile#account")
        } else {
            call.respondRedirect("/login")
        }
    }

    post<UserController.UnlinkPatreon> {
        val sess = call.sessions.get<Session>()
        if (sess != null) {
            transaction {
                User.update({ User.id eq sess.userId }) {
                    it[patreonId] = null
                }
            }
            call.respondRedirect("/profile#account")
        } else {
            call.respondRedirect("/login")
        }
    }
}
