package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import kotlinx.browser.document
import kotlinx.browser.window
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface CopyBSProps : Props {
    var map: MapDetail
}

val copyBsr = fcmemo<CopyBSProps>("copyBsr") { props ->
    a {
        attrs.href = "#"

        val text = "Copy BSR"
        attrs.title = text
        attrs.ariaLabel = text
        attrs.onClick = {
            it.preventDefault()
            setClipboard("!bsr ${props.map.id}")
        }
        span {
            attrs.className = ClassName("dd-text")
            +text
        }
        i {
            attrs.className = ClassName("fab fa-twitch text-info")
            attrs.ariaHidden = true
        }
    }
}

val copyEmbed = fcmemo<CopyBSProps>("copyEmbed") { props ->
    a {
        attrs.href = "#"

        //language=html
        val iframe = """
            <iframe 
                src="${window.location.origin}/maps/${props.map.id}/embed" 
                width="600" height="145" 
                loading="lazy"
                style="border: none; border-radius: 4px;"></iframe>
        """.trimIndent()

        val text = "Copy Embed Code"
        attrs.title = text
        attrs.ariaLabel = text
        attrs.onClick = {
            it.preventDefault()
            setClipboard(iframe)
        }
        span {
            attrs.className = ClassName("dd-text")
            +text
        }
        i {
            attrs.className = ClassName("fas fa-code text-info")
            attrs.ariaHidden = true
        }
    }
}

private fun setClipboard(str: String) {
    val tempElement = document.createElement("span")
    tempElement.textContent = str
    document.body?.appendChild(tempElement)
    val selection = window.asDynamic().getSelection()
    val range = window.document.createRange()
    selection.removeAllRanges()
    range.selectNode(tempElement)
    selection.addRange(range)
    window.document.execCommand("copy")
    selection.removeAllRanges()
    window.document.body?.removeChild(tempElement)
}
