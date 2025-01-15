package io.beatmaps.pages

import io.beatmaps.ConfigData
import io.beatmaps.UserData
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GenericPageTemplate(private val s: Session?, private val provider: CaptchaProvider) : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            id = "root"
        }
        s?.let { snn ->
            div("d-none") {
                id = "user-data"
                +Json.encodeToString(
                    UserData(snn.userId, snn.isAdmin(), snn.isCurator(), snn.suspended, snn.blurnsfw)
                )
            }
        }
        div("d-none") {
            id = "config-data"
            +Json.encodeToString(
                ConfigData(CaptchaVerifier.enabled(provider), SolrHelper.enabled, provider.name)
            )
        }
        jsTags()
    }
}

fun BODY.jsTags() {
    script(src = "/static/modules.js") {}
    script(src = "/static/kotlin.js") {}
    script(src = "/static/time.js") {}
    script(src = "/static/main.js") {}
}
