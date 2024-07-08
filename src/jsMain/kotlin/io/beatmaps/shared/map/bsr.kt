package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import react.Props
import react.dom.a
import react.dom.i
import react.dom.span
import react.fc

external interface CopyBSProps : Props {
    var map: MapDetail
}

val copyBsr = fc<CopyBSProps> { props ->
    a("#") {
        val text = "Copy BSR"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        attrs.onClickFunction = {
            it.preventDefault()
            setClipboard("!bsr ${props.map.id}")
        }
        span("dd-text") { +text }
        i("fab fa-twitch text-info") {
            attrs.attributes["aria-hidden"] = "true"
        }
    }
}

val copyEmbed = fc<CopyBSProps> { props ->
    a("#") {
        //language=html
        val iframe = """
            <iframe 
                src="${window.location.origin}/maps/${props.map.id}/embed" 
                width="600" height="145" 
                style="border: none; border-radius: 4px;"></iframe>
        """.trimIndent()

        val text = "Copy Embed Code"
        attrs.title = text
        attrs.attributes["aria-label"] = text
        attrs.onClickFunction = {
            it.preventDefault()
            setClipboard(iframe)
        }
        span("dd-text") { +text }
        i("fas fa-code text-info") {
            attrs.attributes["aria-hidden"] = "true"
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
