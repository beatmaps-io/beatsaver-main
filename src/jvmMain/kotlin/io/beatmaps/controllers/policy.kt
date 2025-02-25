package io.beatmaps.controllers

import de.nielsfalk.ktor.swagger.Ignore
import io.beatmaps.getNonce
import io.beatmaps.login.Session
import io.beatmaps.pages.DMCAPageTemplate
import io.beatmaps.pages.PrivacyPageTemplate
import io.beatmaps.pages.TOSPageTemplate
import io.beatmaps.pages.templates.MainTemplate
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.resources.get
import io.ktor.server.routing.Route
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

@Resource("/policy")
class PolicyController {
    @Resource("/dmca")
    data class DMCA(
        @Ignore
        val api: PolicyController
    )

    @Resource("/tos")
    data class TOS(
        @Ignore
        val api: PolicyController
    )

    @Resource("/privacy")
    data class Privacy(
        @Ignore
        val api: PolicyController
    )
}

fun Route.policyController() {
    get<PolicyController.DMCA> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, DMCAPageTemplate(), "BeatSaver - DMCA Policy", nonce = call.getNonce()), HttpStatusCode.OK) {}
    }

    get<PolicyController.TOS> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, TOSPageTemplate(), "BeatSaver - Terms of Service", nonce = call.getNonce()), HttpStatusCode.OK) {}
    }

    get<PolicyController.Privacy> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, PrivacyPageTemplate(), "BeatSaver - Privacy Policy", nonce = call.getNonce()), HttpStatusCode.OK) {}
    }
}
