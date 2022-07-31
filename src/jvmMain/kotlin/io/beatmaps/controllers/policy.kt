package io.beatmaps.controllers

import io.beatmaps.login.Session
import io.beatmaps.pages.DMCAPageTemplate
import io.beatmaps.pages.PrivacyPageTemplate
import io.beatmaps.pages.TOSPageTemplate
import io.beatmaps.pages.templates.MainTemplate
import io.ktor.application.call
import io.ktor.html.respondHtmlTemplate
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.routing.Route
import io.ktor.sessions.get
import io.ktor.sessions.sessions

@Location("/policy") class PolicyController {
    @Location("/dmca") data class DMCA(val api: PolicyController)
    @Location("/tos") data class TOS(val api: PolicyController)
    @Location("/privacy") data class Privacy(val api: PolicyController)
}

fun Route.policyController() {
    get<PolicyController.DMCA> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, true, DMCAPageTemplate()), HttpStatusCode.OK) {
            pageTitle = "BeatSaver - DMCA Policy"
        }
    }

    get<PolicyController.TOS> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, true, TOSPageTemplate()), HttpStatusCode.OK) {
            pageTitle = "BeatSaver - Terms of Service"
        }
    }

    get<PolicyController.Privacy> {
        val sess = call.sessions.get<Session>()
        call.respondHtmlTemplate(MainTemplate(sess, true, PrivacyPageTemplate()), HttpStatusCode.OK) {
            pageTitle = "BeatSaver - Privacy Policy"
        }
    }
}
