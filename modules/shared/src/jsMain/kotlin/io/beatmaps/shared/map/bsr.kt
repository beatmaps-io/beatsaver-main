package io.beatmaps.shared.map

import io.beatmaps.api.MapDetail
import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import web.dom.document
import web.window.window

external interface CopyBSProps : Props {
    var map: MapDetail
}

val copyBsr = fcmemo<CopyBSProps>("copyBsr") { props ->
    a {
        href = "#"

        val text = "Copy BSR"
        title = text
        ariaLabel = text
        onClick = {
            it.preventDefault()
            setClipboard("!bsr ${props.map.id}")
        }
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fab fa-twitch text-info")
            ariaHidden = true
        }
    }
}

val copyEmbed = fcmemo<CopyBSProps>("copyEmbed") { props ->
    a {
        href = "#"

        //language=html
        val iframe = """
            <iframe 
                src="${window.location.origin}/maps/${props.map.id}/embed" 
                width="600" height="145" 
                loading="lazy"
                style="border: none; border-radius: 4px;"></iframe>
        """.trimIndent()

        val text = "Copy Embed Code"
        title = text
        ariaLabel = text
        onClick = {
            it.preventDefault()
            setClipboard(iframe)
        }
        span {
            className = ClassName("dd-text")
            +text
        }
        i {
            className = ClassName("fas fa-code text-info")
            ariaHidden = true
        }
    }
}

private fun setClipboard(str: String) {
    val tempElement = document.createElement("span")
    tempElement.textContent = str
    document.body.appendChild(tempElement)

    val selection = window.getSelection()
    val range = document.createRange()
    selection?.removeAllRanges()
    range.selectNode(tempElement)
    selection?.addRange(range)
    document.asDynamic().execCommand("copy")
    selection?.removeAllRanges()
    document.body.removeChild(tempElement)
}
