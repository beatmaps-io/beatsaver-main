package io.beatmaps.shared.map

import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.events.Event
import react.Props
import react.dom.a
import react.dom.i
import react.fc

external interface BookmarkButtonProps : Props {
    var bookmarked: Boolean
    var onClick: (Event, Boolean) -> Unit
}

var bookmarkButton = fc<BookmarkButtonProps> { props ->
    a("#", classes = "text-warning me-1") {
        val title = if (props.bookmarked) "Remove Bookmark" else "Add Bookmark"
        attrs.title = title
        attrs.attributes["aria-label"] = title
        attrs.onClickFunction = {
            it.preventDefault()
            props.onClick(it, props.bookmarked)
        }
        i((if (props.bookmarked) "fas" else "far") + " fa-bookmark text-warning") { }
    }
}
