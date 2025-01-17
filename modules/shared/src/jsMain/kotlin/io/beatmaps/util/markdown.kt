package io.beatmaps.util

import io.beatmaps.api.LeaderboardType
import js.objects.jso
import react.RElementBuilder
import react.dom.html.HTMLAttributes

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
        """${it.groupValues[1]}<a data-bs="local" href="/maps/${it.groupValues[2].lowercase()}">#${it.groupValues[2]}</a>${it.groupValues[3]}"""
    }

fun String.parseIssueReference() =
    replace("(^|\\s)\\{([\\da-f]+?)\\}($|[^\\da-z])".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))) {
        """${it.groupValues[1]}<a data-bs="local" href="/issues/${it.groupValues[2].lowercase()}">{${it.groupValues[2]}}</a>${it.groupValues[3]}"""
    }

fun String.parseUserReference() =
    replace("(^|\\s)@([\\w.-]+?)($|[^\\w.-])".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))) {
        """${it.groupValues[1]}<a href="/profile/username/${it.groupValues[2].lowercase()}">@${it.groupValues[2]}</a>${it.groupValues[3]}"""
    }

fun String.parseSocialLinks() =
    listOf(
        "(^|\\s)twt@(\\w+?)($|\\W)" to """$1<a href="https://twitter.com/$2">twt@$2</a>$3""",
        "(^|\\s)yt@([\\w.-]+?)($|[^\\w.-])" to """$1<a href="https://www.youtube.com/channel/$2">yt@$2</a>$3""",
        "(^|\\s)yth@([\\w.-]+?)($|[^\\w.-])" to """$1<a href="https://www.youtube.com/@$2">yth@$2</a>$3""",
        "(^|\\s)ttv@(\\w+?)($|\\W)" to """$1<a href="https://www.twitch.tv/$2">ttv@$2</a>$3""",
        "(^|\\s)steam@(\\d+?)($|\\W)" to """$1<a href="https://steamcommunity.com/profiles/$2">steam@$2</a>$3""",
        "(^|\\s)ss@(\\d+?)($|\\W)" to """$1<a href="${LeaderboardType.ScoreSaber.userPrefix}$2">ss@$2</a>$3""",
        "(^|\\s)bl@(\\d+?)($|\\W)" to """$1<a href="${LeaderboardType.BeatLeader.userPrefix}$2">bl@$2</a>$3""",
        "(^|\\s)gh@([\\w.-]+?)($|[^\\w.-])" to """$1<a href="https://www.github.com/$2">gh@$2</a>$3"""
    ).fold(this) { v, it ->
        v.replace(it.first.toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)), it.second)
    }

fun RElementBuilder<HTMLAttributes<*>>.textToContent(text: String) {
    attrs.dangerouslySetInnerHTML = jso {
        __html = text
            .replace(Regex("<.+?>"), "")
            .parseBoldMarkdown()
            .parseItalicMarkdown()
            .parseMapReference()
            .parseUserReference()
            .parseIssueReference()
            .transformURLIntoLinks()
            .parseSocialLinks()
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("\n", "<br />")
    }
}
