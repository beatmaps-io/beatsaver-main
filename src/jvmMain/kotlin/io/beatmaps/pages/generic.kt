package io.beatmaps.pages

import io.beatmaps.login.Session
import io.ktor.html.Template
import kotlinx.html.BODY
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.script
import kotlinx.html.unsafe

class GenericPageTemplate(private val s: Session?) : Template<BODY> {
    override fun BODY.apply() {
        main("container") {
            id = "root"
        }
        s?.let { snn ->
            script {
                unsafe {
                    +"window.userId = ${snn.userId}; window.admin = ${snn.admin}"
                }
            }
        }
        script(src = "/static/output.js") {}
    }
}