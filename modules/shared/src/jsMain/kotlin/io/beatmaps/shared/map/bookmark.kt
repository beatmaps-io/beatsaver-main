package io.beatmaps.shared.map

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface BookmarkButtonProps : Props {
    var bookmarked: Boolean
    var onClick: (Boolean) -> Unit
}

var bookmarkButton = fcmemo<BookmarkButtonProps>("bookmarkButton") { props ->
    a {
        href = "#"
        className = ClassName("me-1")

        val title = if (props.bookmarked) "Remove Bookmark" else "Add Bookmark"
        this.title = title
        ariaLabel = title
        onClick = {
            it.preventDefault()
            props.onClick(props.bookmarked)
        }
        span {
            className = ClassName("dd-text")
            +title
        }
        i {
            className = ClassName((if (props.bookmarked) "fas" else "far") + " fa-bookmark text-warning")
        }
    }
}
