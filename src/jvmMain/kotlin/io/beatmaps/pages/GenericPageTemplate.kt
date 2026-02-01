package io.beatmaps.pages

import io.beatmaps.ConfigData
import io.beatmaps.UserData
import io.beatmaps.cloudflare.CaptchaProvider
import io.beatmaps.cloudflare.CaptchaVerifier
import io.beatmaps.common.solr.SolrHelper
import io.beatmaps.dockerHash
import io.beatmaps.login.Session
import io.ktor.server.html.Template
import kotlinx.html.BODY
import kotlinx.html.SCRIPT
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.script
import kotlinx.serialization.json.Json

class GenericPageTemplate(private val s: Session?, private val provider: CaptchaProvider, private val nonce: String?) : Template<BODY> {
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
        jsTags(nonce)
    }
}

private val applyNonce: SCRIPT.(String?) -> Unit = { n ->
    n?.let { nonce = it }
}

private fun BODY.scriptWithNonce(src: String, nonce: String?) {
    script(src = "$src?v=$dockerHash") {
        applyNonce(nonce)
    }
}

fun BODY.jsTags(nonce: String?) {
    scriptWithNonce("/static/modules.js", nonce)
    scriptWithNonce("/static/kotlin.js", nonce)
    scriptWithNonce("/static/time.js", nonce)
    scriptWithNonce("/static/main.js", nonce)
}
