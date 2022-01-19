package io.beatmaps.pages

import io.beatmaps.login.Session
import io.ktor.html.Template
import kotlinx.html.BODY
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.script

class GenericPageTemplate(private val s: Session?) : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            id = "root"
        }
        s?.let { snn ->
            div("d-none") {
                id = "user-data"
                +"{\"userId\": ${snn.userId}, \"admin\": ${snn.admin}, \"curator\": ${snn.curator || snn.admin}}"
            }
        }
        script(src = "/static/modules.js") {}
        script(src = "/static/output.js") {}
    }
}
