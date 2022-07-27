package io.beatmaps.util

import kotlinx.html.Tag
import react.dom.InnerHTML
import react.dom.RDOMBuilder

fun String.transformURLIntoLinks() =
    replace("\\b((https?|ftp)://)?[-a-zA-Z0-9@:%._+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(/[-a-zA-Z0-9@:%_+.~#?&/=]*)*(?:/|\\b)".toRegex()) {
        if (it.groupValues[1].isEmpty()) it.value else "<a target=\"_blank\" href=\"${it.value}\">${it.value}</a>"
    }

fun String.parseBoldMarkdown() =
    replace("(^|\\s)(\\*\\*|__)((.|\\n)+?)\\2".toRegex(RegexOption.MULTILINE)) {
        "${it.groupValues[1]}<b>${it.groupValues[3]}</b>"
    }

fun String.parseItalicMarkdown() =
    replace("(^|\\s)([*_])((.|\\n)+?)\\2".toRegex(RegexOption.MULTILINE)) {
        "${it.groupValues[1]}<i>${it.groupValues[3]}</i>"
    }

fun String.parseMapReference() =
    replace("(^|\\s)#([\\da-f]+?)($|[^\\da-z])".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))) {
        """${it.groupValues[1]}<a href="/maps/${it.groupValues[2].lowercase()}">#${it.groupValues[2]}</a>${it.groupValues[3]}"""
    }

fun String.parseUserReference() =
    replace("(^|\\s)@([\\w.-]+?)($|[^\\w.-])".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))) {
        """${it.groupValues[1]}<a href="/profile/username/${it.groupValues[2].lowercase()}">@${it.groupValues[2]}</a>${it.groupValues[3]}"""
    }

// Kotlin IR be adding underscores everywhere
class DangerousHtml(override var __html: String) : InnerHTML

fun <T : Tag> RDOMBuilder<T>.textToContent(text: String) {
    domProps.dangerouslySetInnerHTML = DangerousHtml(
        text
            .replace(Regex("<.+?>"), "")
            .parseBoldMarkdown()
            .parseItalicMarkdown()
            .parseMapReference()
            .parseUserReference()
            .transformURLIntoLinks()
            .replace("\n", "<br />")
    )
}
