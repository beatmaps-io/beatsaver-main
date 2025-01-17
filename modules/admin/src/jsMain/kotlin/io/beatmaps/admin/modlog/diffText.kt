package io.beatmaps.admin.modlog

import io.beatmaps.util.fcmemo
import react.Props
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

external interface DiffTextProps : Props {
    var description: String
    var old: String
    var new: String
}

val diffText = fcmemo<DiffTextProps>("diffText") { props ->
    if (props.new != props.old) {
        p {
            attrs.className = ClassName("card-text")
            if (props.new.isNotEmpty()) {
                +"Updated ${props.description}"
                span {
                    attrs.className = ClassName("text-danger d-block")
                    i {
                        attrs.className = ClassName("fas fa-minus")
                    }
                    +" ${props.old}"
                }
                span {
                    attrs.className = ClassName("text-success d-block")
                    i {
                        attrs.className = ClassName("fas fa-plus")
                    }
                    +" ${props.new}"
                }
            } else {
                // Shows as empty if curator is changing tags
                +"Deleted ${props.description}"
            }
        }
    }
}
