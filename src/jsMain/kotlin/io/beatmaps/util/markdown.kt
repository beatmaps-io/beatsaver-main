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

fun String.parseSocialLinks() =
    listOf(
        "(?<=^|\\s)twt@(\\w+?)(?=$|\\W)" to """<a href="https://twitter.com/$1">twt@$1</a>""",
        "(?<=^|\\s)yt@([\\w.-]+?)(?=$|[^\\w.-])" to """<a href="https://www.youtube.com/channel/$1">yt@$1</a>""",
        "(?<=^|\\s)ttv@(\\w+?)(?=$|\\W)" to """<a href="https://www.twitch.tv/$1">ttv@$1</a>""",
        "(?<=^|\\s)steam@(\\d+?)(?=$|\\W)" to """<a href="https://steamcommunity.com/profiles/$1">steam@$1</a>""",
        "(?<=^|\\s)ss@(\\d+?)(?=$|\\W)" to """<a href="https://scoresaber.com/u/$1">ss@$1</a>""",
        "(?<=^|\\s)bl@(\\d+?)(?=$|\\W)" to """<a href="https://www.beatleader.xyz/u/$1">bl@$1</a>""",
    ).fold(this) { v, it ->
        v.replace(it.first.toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)), it.second)
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
            .parseSocialLinks()
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("\n", "<br />")
    )
}
