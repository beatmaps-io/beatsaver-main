package io.beatmaps.pages

import io.beatmaps.cloudflare.CaptchaProvider
import io.beatmaps.cloudflare.CaptchaVerifier
import io.beatmaps.common.solr.SolrHelper
import io.beatmaps.login.Session
import io.ktor.server.html.Template
import kotlinx.html.BODY
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.script

class GenericPageTemplate(private val s: Session?, private val provider: CaptchaProvider) : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            id = "root"
        }
        s?.let { snn ->
            div("d-none") {
                id = "user-data"
                +"""{"userId": ${snn.userId}, "admin": ${snn.admin}, "curator": ${snn.curator || snn.admin}, "suspended": ${snn.suspended}}"""
            }
        }
        div("d-none") {
            id = "config-data"
            +"""{"showCaptcha": ${CaptchaVerifier.enabled(provider)}, "captchaProvider": "${provider.name}", "v2Search": ${SolrHelper.enabled}}"""
        }
        jsTags()
    }
}

fun BODY.jsTags() {
    script(src = "/static/modules.js") {}
    script(src = "/static/main.js") {}
}
