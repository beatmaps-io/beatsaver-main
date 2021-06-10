package io.beatmaps.pages.templates

import io.beatmaps.login.Session
import io.ktor.html.Placeholder
import io.ktor.html.Template
import io.ktor.html.TemplatePlaceholder
import io.ktor.html.insert
import kotlinx.html.BODY
import kotlinx.html.HEAD
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.script
import kotlinx.html.styleLink
import kotlinx.html.title

class MainTemplate(private val s: Session?, private val body: Template<BODY>) : Template<HTML> {
    private val header = TemplatePlaceholder<HeaderTemplate>()
    private val bodyPlaceholder = TemplatePlaceholder<Template<BODY>>()
    val headElements = Placeholder<HEAD>()
    var pageTitle = ""

    override fun HTML.apply() {
        lang = "en"
        head {
            title { +pageTitle }
            script(src = "https://kit.fontawesome.com/0d3e310ec2.js") {
                attributes["crossorigin"] = "anonymous"
                defer = true
            }
            styleLink("/static/main.css")
            styleLink("/static/bootstrap.css")
            meta("theme-color", "#375a7f")
            meta("viewport", "width=575, initial-scale=0.86")
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
            insert(headElements)
        }
        body {
            insert(HeaderTemplate(s), header)
            insert(body, bodyPlaceholder)
        }
    }
}