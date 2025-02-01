package io.beatmaps.pages.templates

import io.beatmaps.login.Session
import io.ktor.server.html.Placeholder
import io.ktor.server.html.Template
import io.ktor.server.html.TemplatePlaceholder
import io.ktor.server.html.insert
import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.LinkRel
import kotlinx.html.LinkType
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.title

fun HEAD.bmStyle(url: String, nonce: String?, deferred: Boolean = false): Unit = link {
    rel = LinkRel.stylesheet
    type = LinkType.textCss
    nonce?.let { attributes["nonce"] = it }
    href = url
    if (deferred) {
        attributes["data-lazy"] = "true"
        media = "print"
    }
}

class MainTemplate(private val s: Session?, private val body: Template<BODY>, private val pageTitle: String = "", private val includeHeader: Boolean = true, private val nonce: String? = null) : Template<HTML> {
    private val header = TemplatePlaceholder<HeaderTemplate>()
    private val bodyPlaceholder = TemplatePlaceholder<Template<BODY>>()
    val headElements = Placeholder<HEAD>()

    override fun HTML.apply() {
        lang = "en"
        head {
            insert(headElements)
            title { +pageTitle }
            bmStyle("/static/main.css", nonce)
            bmStyle("https://use.fontawesome.com/releases/v5.15.4/css/all.css", nonce, true)
            bmStyle("https://fonts.googleapis.com/css2?family=Lato:ital,wght@0,400;0,700;1,400&display=swap", nonce, true)
            meta("theme-color", "#375a7f")
            meta("viewport", "width=device-width, min-width=575")
            meta("Description", "Beat saber custom maps")
            link("/static/favicon/apple-touch-icon.png", "apple-touch-icon") {
                sizes = "180x180"
            }
            link("/static/favicon/favicon-32x32.png", "icon", "image/png") {
                sizes = "32x32"
            }
            link("/static/favicon/favicon-16x16.png", "icon", "image/png") {
                sizes = "16x16"
            }
            link("/static/favicon/site.webmanifest", "manifest")
            link("/static/favicon/favicon.ico", "shortcut icon")
        }
        body {
            if (includeHeader) insert(HeaderTemplate(s), header)
            insert(body, bodyPlaceholder)
        }
    }
}
