package io.beatmaps.util

import io.beatmaps.api.LeaderboardType
import js.objects.jso
import react.dom.html.HTMLAttributes
import web.window.window

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

external interface TrustedTypes {
    fun createPolicy(name: String, options: PolicyOptions? = definedExternally): TrustedPolicy
}

external interface TrustedPolicy {
    fun createHTML(input: String): String
}

external interface PolicyOptions {
    var createHTML: (String) -> String
    var createScript: (String) -> String
    var createScriptURL: (String) -> String
}

private val trustedType = try {
    (window.asDynamic().trustedTypes as? TrustedTypes)?.createPolicy(
        "BMMD",
        jso {
            createHTML = { it }
        }
    )
} catch (e: Exception) { null }
private fun String.makeSafe() = trustedType?.createHTML(this) ?: this
fun HTMLAttributes<*>.textToContent(text: String) {
    dangerouslySetInnerHTML = jso {
        __html = text
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .parseBoldMarkdown()
            .parseItalicMarkdown()
            .parseMapReference()
            .parseUserReference()
            .parseIssueReference()
            .transformURLIntoLinks()
            .parseSocialLinks()
            .replace(Regex("\n{3,}"), "\n\n")
            .replace("\n", "<br />")
            .makeSafe()
    }
}
